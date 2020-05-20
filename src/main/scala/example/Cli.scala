package example

import java.util.concurrent.Executors

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import Program._
import Impl._

import scala.util.chaining._
import Model._
import com.gu.spy._
import InputReader._
import com.typesafe.scalalogging.LazyLogging

object Cli extends App with LazyLogging {
  private def startExportProcess(in: Input): Unit = {
    implicit val nonDeamonEc = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)
    Future.traverse(in.objects) { obj =>
      Future {
        retry(1)(exportObject(obj.name, obj.fields, in.beginningOfTime))
      }.andThen {
        case Success(value) => logger.info(value)
        case Failure(e) =>
          logger.error(s"${obj.name} failed! Restarting the export will resume from last bookmark. $e")
//          System.exit(-1)
      }
    }.andThen {
      case Success(_) => logger.info(s"Successfully completed full export of ${in.objects.map(_.name)}")
      case Failure(e) => logger.error(s"Some object did not fully export. Please keep restarting the process until all are done. It is safe to simply restart as it will resume from last bookmark.")
    }
  }

  input(args)
    .tap  { in => logger.info(s"Exporting zuora objects:\n${in.objects.spy}") }
    .pipe { startExportProcess }
}
