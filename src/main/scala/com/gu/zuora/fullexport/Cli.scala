package com.gu.zuora.fullexport

import java.util.concurrent.Executors

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import Program._
import Impl._
import better.files._
import better.files.Dsl._

import scala.util.chaining._
import Model._
import Model.OptionPickler._
import com.gu.spy._
import InputReader._
import com.typesafe.scalalogging.LazyLogging

object Cli extends App with LazyLogging {
  private def startExportProcess(in: Input): Unit = {
    implicit val nonDeamonEc = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)
    Future.traverse(in.objects) { obj =>
      Future(retry(2)(exportObject(obj, in.beginningOfTime)))
        .andThen(logRunningStatus(obj))
    }.onComplete(logFinalResultWithDelay(in))
  }

  private def logRunningStatus(obj: ZuoraObject): PartialFunction[Try[String], Unit]  = {
    case Success(value) => logger.info(value)
    case Failure(e) => logger.error(s"${obj.name} failed! Restarting the export will resume from last bookmark", e)
  }

  /* Make sure the final log statement is the overall result. Needed due to async out-of-order logging happening in threads */
  private def logFinalResultWithDelay(in: Input): PartialFunction[Try[List[String]], Unit] = {
    case Success(_) =>
      Thread.sleep(5000)
      logger.info(s"Successfully completed full export of ${in.objects.map(_.name)}")
    case Failure(e) =>
      Thread.sleep(5000)
      logger.error(s"Some object did not fully export. Please keep restarting the process until all are done. It is safe to simply restart as it will resume from last bookmark.")
  }

  input(args)
    .tap  { in => logger.info(s"Exporting zuora objects:\n${in.objects.spy}") }
    .pipe { startExportProcess }
}
