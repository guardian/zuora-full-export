package example

object Model {
  object OptionPickler extends upickle.AttributeTagged {
    implicit def optionWriter[T: Writer]: Writer[Option[T]] =
      implicitly[Writer[T]].comap[Option[T]] {
        case None => null.asInstanceOf[T]
        case Some(x) => x
      }

    implicit def optionReader[T: Reader]: Reader[Option[T]] = {
      new Reader.Delegate[Any, Option[T]](implicitly[Reader[T]].map(Some(_))){
        override def visitNull(index: Int) = None
      }
    }
  }

  import OptionPickler._

  case class AccessToken(access_token: String)
  case class QueryResponse(id: String)
  case class Batch(batchId: String, name: String, status: String, recordCount: Int, fileId: Option[String] = None)
  case class JobResults(id: String, status: String, batches: List[Batch])
  case class ZuoraObject(name: String, fields: List[String])
  case class Input(beginningOfTime: String, objects: List[ZuoraObject])

  implicit val accessTokenRW: ReadWriter[AccessToken] = macroRW
  implicit val queryResponseRW: ReadWriter[QueryResponse] = macroRW
  implicit val batchRW: ReadWriter[Batch] = macroRW
  implicit val jobResultsRW: ReadWriter[JobResults] = macroRW
  implicit val ZuoraObjectRW: ReadWriter[ZuoraObject] = macroRW
  implicit val InputRW: ReadWriter[Input] = macroRW
}
