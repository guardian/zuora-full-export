package example

import java.lang.System.getenv
import java.time.{LocalDate, YearMonth}
import java.time.temporal.ChronoUnit
import scalaj.http.{BaseHttp, HttpOptions}
import scala.concurrent.duration._
import scala.util.chaining._
import better.files._
import Model._
import Model.OptionPickler._
import com.typesafe.scalalogging.LazyLogging
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object Impl extends LazyLogging {
  def getFieldNames(rawXml: String): List[String] = {
    scala.xml.XML.loadString(rawXml)
      .pipe { result =>
        (result \\ "field").map { field =>
          val fieldName = (field \ "name").text
          val isExportable = (field \\ "context").map(_.text).contains("export")
          (fieldName, isExportable)
        }
      }
      .collect { case (field, exportable) if exportable => field }
      .toList
      .sorted
  }

  def getRelatedObjects(rawXml: String): List[String] = {
    scala.xml.XML
      .loadString(rawXml)
      .pipe { result => (result \\ "related-objects" \\ "name") map (_.text) }
      .toList
  }

  lazy val stage = getenv("Stage")

  lazy val zuoraApiHost: String =
    stage match {
      case "CODE" => "https://rest.apisandbox.zuora.com"
      case "PROD" => "https://rest.zuora.com"
    }

  object HttpWithLongTimeout extends BaseHttp(
    options = Seq(
      HttpOptions.connTimeout(5000),
      HttpOptions.readTimeout(5 * 60 * 1000),
      HttpOptions.followRedirects(false)
    )
  )

  object PeriodicAccessToken {
    import java.util.{Timer, TimerTask}

    private def impl(): String = {
      HttpWithLongTimeout(s"$zuoraApiHost/oauth/token")
        .postForm(Seq(
          "client_id" -> getenv("ClientId"),
          "client_secret" -> getenv("ClientSecret"),
          "grant_type" -> "client_credentials"
        ))
        .asString
        .body
        .pipe(read[AccessToken](_))
        .access_token
    }
    private var _accessToken: String = null
    private val timer = new Timer()

    timer.schedule(
      new TimerTask { def run(): Unit = _accessToken = impl() },
      0, 2 * 60 * 1000 // refresh token every 2 min
    )
    _accessToken = impl() // set token on initialization

    def accessToken(): String = _accessToken
  }

  import PeriodicAccessToken._

  def describe(objectName: String) = {
    HttpWithLongTimeout(s"$zuoraApiHost/v1/describe/${objectName}")
      .header("Authorization", s"Bearer ${accessToken()}")
      .asString
      .body
  }

  def buildZoqlQuery(
    objectName: String,
    fields: List[String],
    from: LocalDate,
    to: LocalDate
  ): String = {
    s"""SELECT ${fields.mkString(",")} FROM ${objectName} WHERE (CreatedDate >= '${from}T00:00:00') AND (CreatedDate <= '${to}T00:00:00')"""
  }

  def fromBookmarkUntilNowByMonth(startDate: LocalDate): Range.Inclusive =
    0 to ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(LocalDate.now)).toInt

  def startAquaJob(zoql: String, objectName: String, start: LocalDate): String = {
    val body =
      s"""
         |{
         |	"format" : "csv",
         |	"version" : "1.1",
         |	"name" : "zuora-full-export",
         |	"encrypted" : "none",
         |	"useQueryLabels" : "true",
         |	"dateTimeUtc" : "true",
         |	"queries" : [
         |		{
         |			"name" : "${objectName}-$start",
         |			"query" : "$zoql",
         |			"type" : "zoqlexport"
         |		}
         |	]
         |}
    """.stripMargin

    HttpWithLongTimeout(s"$zuoraApiHost/v1/batch-query/")
      .header("Authorization", s"Bearer ${accessToken()}")
      .header("Content-Type", "application/json")
      .postData(body)
      .method("POST")
      .asString
//      .tap(response => { println(response.code); println(response.body); if (response.code != 200) logger.error(response.body) })
      .body
      .pipe(read[QueryResponse](_))
      .id
  }

  /**
   * Poll job status recursively until all queries have completed.
   *
   * https://knowledgecenter.zuora.com/DC_Developers/AB_Aggregate_Query_API/C_Get_Job_ID
   */
  @tailrec def getJobResult(jobId: String): JobResults = {
    val response = HttpWithLongTimeout(s"$zuoraApiHost/v1/batch-query/jobs/$jobId")
      .header("Authorization", s"Bearer ${accessToken()}")
      .header("Content-Type", "application/json")
      .asString

    val jobResults = read[JobResults](response.body)

    jobResults.batches.find(_.status == "aborted").map { abortedBatch =>
      throw new RuntimeException(s"Failed to complete query: $abortedBatch")
    }

    if (jobResults.batches.forall(_.status == "completed"))
      jobResults
    else {
      logger.info(s"Checking if getJobResult($jobId) is done...")
      //      Thread.sleep(1.minute.toMillis)
      Thread.sleep(5.seconds.toMillis) // FIXME: Increase delay
      getJobResult(jobId) // Keep trying until lambda timeout
    }
  }

  def downloadCsvFile(batch: Batch): String = {
    logger.info(s"Downloading $batch ....")
    val fileId = batch.fileId.getOrElse(throw new RuntimeException("Failed to get csv file due to missing fileId"))
    HttpWithLongTimeout(s"$zuoraApiHost/v1/file/$fileId")
      .header("Authorization", s"Bearer ${accessToken()}")
      .asString
      .body
  }

  def discoverFields(objectName: String): List[String] = {
    logger.info(s"Auto-discovering field names of $objectName...")
    val rawXml = describe(objectName)
    val fieldNames = getFieldNames(rawXml)
    val relatedObjectIds = getRelatedObjects(rawXml).map(objectName => s"${objectName}.Id")
    fieldNames ++ relatedObjectIds
  }

  /** Date inside .bookmark file takes precedence over beginning of time */
  def readBookmark(objectName: String, beginningOfTime: String): LocalDate = {
    Try(file"$scratchDir/$objectName.bookmark".lines.head)
      .map(LocalDate.parse)
      .getOrElse(LocalDate.parse(beginningOfTime))
  }

  // https://stackoverflow.com/a/7931459/5205022
  @tailrec def retry[T](n: Int)(fn: => T): T = {
    util.Try { fn } match {
      case Success(x) => x
      case Failure(e) if n > 1 =>
        logger.warn(s"Retrying operation due to failure: $e")
        retry(n - 1)(fn)

      case util.Failure(e) => throw e
    }
  }

  def boom(time: Long): Unit = {
    val riggedThread = Thread.currentThread()
    logger.warn(s"${riggedThread.getName} has been rigged with explosive he he he...")
    import java.util._
    val timer = new Timer()
    val task = new TimerTask { def run() = riggedThread.interrupt() }
    timer.schedule(task, time)
  }

  val scratchDir = "output/scratch"
  val outputDir = "output"

  def writeHeaderOnce(objectName: String, lines: List[String]): Unit = {
    if (file"$scratchDir/$objectName-header.csv".exists) {
      // do nothing
    } else {
      lines match {
        case header :: _ =>
          file"$scratchDir/$objectName-header.csv".appendLines(s"IsDeleted,$header")
          file"$outputDir/$objectName.csv".appendLines(s"IsDeleted,$header")

        case Nil =>
          throw new RuntimeException(s"Downloaded $objectName CSV file should have at least a header")
      }
    }
  }
}
