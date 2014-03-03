package latis.reader.tsml

//TODO: move to reader package?

import scala.io.Source
import latis.util.RegEx

trait AsciiParser extends Parser[String] {
  
  //---- Methods for implementation to provide --------------------------------
  
  /**
   * Get the String (one or more characters) that is used at the start of a 
   * line to indicate that it should not be read as data. 
   * Defaults to null, meaning that no line should be ignored (except empty lines).
   * Return null if there are no comments to skip.
   */
  def getCommentCharacter: String
  
  /**
   * Get the String (one or more characters) that is used to separate data values.
   */
  def getDelimiter: String
  
  /**
   * Return the number of lines (as returned by Source.getLines) that make up
   * each data record.
   */
  def getLinesPerRecord: Int
  
  /**
   * Source from which we will read data.
   */
  def getDataSource: Source
  
  /**
   * Return a list of variable names represented in the data.
   */
  def getVariableNames: Seq[String]
  
  
  //---- Parse operations -----------------------------------------------------
  
  /**
   * Return an Iterator of data records. Group multiple lines of text for each record.
   */
  def getRecordIterator: Iterator[String] = {
    val lpr = getLinesPerRecord
    val dlm = getDelimiter
    getLineIterator.grouped(lpr).map(_.mkString(dlm))
  }
  
  /**
   * Return Iterator of lines, filter out lines deemed unworthy by "shouldSkipLine".
   */
  def getLineIterator: Iterator[String] = {
    getDataSource.getLines().filterNot(shouldSkipLine(_))
  }
  
  /**
   * This method will be used by the lineIterator to skip lines from the data source
   * that we don't want in the data. 
   * Note that the "isEmpty" test bypasses an end of file problem iterating over the 
   * iterator from Source.getLines.
   */
  def shouldSkipLine(line: String): Boolean = {
    //TODO: what if nothing but whitespace? trim?
    val c = getCommentCharacter
    line.isEmpty() || (c != null && line.startsWith(c))
  }
  
  /**
   * Return Map with Variable name to value.
   */
  def parseRecord(record: String): Map[String,String] = {
    (getVariableNames zip record.split(getDelimiter)).toMap
  }
}