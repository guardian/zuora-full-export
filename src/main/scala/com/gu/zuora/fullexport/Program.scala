package com.gu.zuora.fullexport

import java.time.LocalDate
import java.time.LocalDate._

import Impl._
import better.files._
import Model.{JobResults, ZuoraObject}

import scala.util.Try
import scala.util.chaining._
import Model.OptionPickler._
import com.typesafe.scalalogging.LazyLogging

/**
 * Main business logic.
 *
 * 1. Calculate resume pointer from object.bookmark file if it exists otherwise from beginning of time
 * 2. Break export into monthly chunks from bookmark until now
 * 3. Start AQUA export job for each chunk in sequence
 * 4. Keep checking if the job is done
 * 5. Download chunk to object-YYYY-MM-DD.csv
 * 6. Append lines from the chunk to aggregate file object.csv
 * 7. Verify record count of aggregate file against metadata of chunks
 * 8. Once all chunks have downloaded write object.success file
 */
object Program extends LazyLogging {
  def exportObject(obj: ZuoraObject, beginningOfTime: String): String = {
    val ZuoraObject(objectName, fields) = obj
    if (file"$scratchDir/$objectName.success".exists) {
      s"$objectName full export has already successfully competed. Skipping!"
    } else {
      val bookmark = readBookmark(objectName, beginningOfTime) tap { bookmark => logger.info(s"Resume $objectName from $bookmark") }
      val chunkRange = fromBookmarkUntilNowByMonth(bookmark)
      val totalChunks = chunkRange.length
      chunkRange foreach { step =>
        val start = bookmark.plusMonths(step)
        val end = start.plusMonths(1)
        val chunk = s"(${step + 1}/$totalChunks)"
        val zoqlQuery = buildZoqlQuery(objectName, fields, start, end)
        val jobId = startAquaJob(zoqlQuery, objectName, start) tap { jobId => logger.info(s"Exporting $objectName $start to $end chunk $chunk by job $jobId") }
        val jobResult = getJobResult(jobId)
        val batch = jobResult.batches.head
        val filePath = downloadCsvFile(batch, objectName, start)
        val iteratorForLength = filePath.lineIterator
        val lines = filePath.lineIterator
        val recordCountWithoutHeader = iteratorForLength.length - 1
        Assert(s"Downloaded record count should match $jobId metadata record count $recordCountWithoutHeader =/= ${batch.recordCount}", recordCountWithoutHeader == batch.recordCount)
        writeHeaderOnceAndAdvanceIterator(objectName, lines) tap (_ => logger.info(s"Completed $objectName-$start.csv header processing"))
        logger.info(s"Writing downloaded $objectName records to .csv file")
        val aggregateFile = file"$outputDir/$objectName.csv"
        val linesWithIsDeletedColumn = lines.map(row => s"false,$row")
        aggregateFile.printLines(linesWithIsDeletedColumn)
        file"$scratchDir/$objectName-$start.metadata".write(write(jobResult))
        file"$scratchDir/$objectName.bookmark".write(end.toString)
        logger.info(s"Done $objectName $start to $end chunk $chunk with record count $recordCountWithoutHeader exported by job $jobId")
      }
      verifyAggregateFileAgainstChunkMetadata(objectName)
      file"$scratchDir/$objectName.success".touch()
      s"All $objectName chunks successfully exported and verified!"
    }
  }
}
