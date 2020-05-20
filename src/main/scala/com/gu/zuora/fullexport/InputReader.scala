package com.gu.zuora.fullexport

import better.files._

import scala.util.Try
import com.gu.spy._
import com.gu.zuora.fullexport.Model._
import com.gu.zuora.fullexport.Model.OptionPickler._
import com.gu.zuora.fullexport.Impl._
import com.typesafe.scalalogging.LazyLogging

import scala.util.chaining._

object InputReader extends LazyLogging {
  def input(args: Array[String]): Input = {
    val autoDiscover = Try(args(0)).toOption.map(_.toLowerCase).contains("auto")
    if (autoDiscover) {
      logger.info(
        """|
          |"WARNING: You have not provided input.json which means system will auto-detect and export all fields of each object
          |in alphabetical order.
          |Do you wish to proceed? (Y)
          |""".stripMargin)

      val confirmation = scala.io.StdIn.readLine()
      if (confirmation != "Y") System.exit(-1)
    }

    if (autoDiscover)
      Input(beginningOfTime = "2014-01-01", objects = List("Account", "Amendment").map(obj => ZuoraObject(obj, discoverFields(obj))))
    else
      file"input.json".contentAsString.pipe(read[Input](_))
  }
}
