package latis.reader.adapter

import latis.data._
import latis.metadata._
import latis.util.StringUtils
import java.security.cert.X509Certificate
import scala.io.Source
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager
import latis.dm._

class AsciiAdapter3(metadata: Metadata3, config: AdapterConfig) 
  extends IterativeAdapter3[String](metadata, config) {
  
  //---- Manage data source ---------------------------------------------------
  
  private lazy val source = Source.fromURL(getUrl)
  
  override def close = if (source != null) source.close
  
  //---- Adapter Properties ---------------------------------------------------
  
  /**
   * Get the String (one or more characters) that is used at the start of a 
   * line to indicate that it should not be read as data. 
   * Defaults to null, meaning that no line should be ignored (except empty lines).
   * Return null if there are no comments to skip.
   * Use a lazy val since this will be used for every line.
   */
  lazy val getCommentCharacter: String = getProperty("commentCharacter") match {
    case Some(s) => s
    case None    => null
  }
  
  /**
   * Get the String (one or more characters) that is used to separate data values.
   * Default to comma (",").
   */
  def getDelimiter: String = getProperty("delimiter", ",")
  //TODO: reconcile with ability to define delimiter in tsml as regex, 
  //  but need to be able to insert into data
  
  /**
   * Return the number of lines (as returned by Source.getLines) that make up
   * each data record.
   */
  def getLinesPerRecord: Int = getProperty("linesPerRecord") match {
    case Some(s) => s.toInt
    case None => 1
  }
    
  /**
   * Return the number of lines (as returned by Source.getLines) that should
   * be skipped before reading data.
   */
  def getLinesToSkip: Int = getProperty("skip") match {
    case Some(s) => s.toInt
    case None => 0
  }

  /**
   * Get the String used as the data marker from tsml file.
   * Use a lazy val since this will be used for every line.
   */
  lazy val getDataMarker: String = getProperty("marker") match {
    case Some(s) => s
    case None => null
  }
  
  /**
   * Keep track of whether we have encountered a data marker.
   */
  private var foundDataMarker = false


  //---- Parse operations -----------------------------------------------------
  
  /**
   * Return an Iterator of data records. Group multiple lines of text for each record.
   */
  def getRecordIterator: Iterator[String] = {
    val lpr = getLinesPerRecord
    val dlm = getDelimiter
    val records = getLineIterator.grouped(lpr).map(_.mkString(dlm))
    
 //TODO: apply length of Function if given
    getProperty("limit") match {
      case Some(s) => records.take(s.toInt) //TODO: deal with bad value
      case None    => records
    }
  }
  
  /**
   * Return Iterator of lines, filter out lines deemed unworthy by "shouldSkipLine".
   */
  def getLineIterator: Iterator[String] = {
    //TODO: does using 'drop' cause premature reading of data?
    val skip = getLinesToSkip
    source.getLines.drop(skip).filterNot(shouldSkipLine(_))
  }
  
  /**
   * This method will be used by the lineIterator to skip lines from the data source
   * that we don't want in the data. 
   * Note that the "isEmpty" test bypasses an end of file problem iterating over the 
   * iterator from Source.getLines.
   */
  def shouldSkipLine(line: String): Boolean = {
    val d = getDataMarker
    val c = getCommentCharacter

    if (d == null || foundDataMarker) {
      // default behavior: ignore empty lines and lines that start with comment characters
      line.isEmpty() || (c != null && line.startsWith(c))
    } else {
      // We have a data marker and we haven't found it yet,
      // therefore we should ignore everything until we
      // find it. We should also exclude the data marker itself
      // when we find it. 
      if (line.matches(d)) foundDataMarker = true;
      true
    }
  }
  
  /**
   * Return Map with Variable name to value(s) as Data.
   */
  def parseRecord(record: String): Option[Map[String,Any]] = {
    /*
     * TODO: consider nested functions
     * if not flattened, lines per record will be length of inner Function (assume cartesian?)
     * deal with here or use algebra?
     */
    
    //assume one value per scalar per record
    val vnames = getScalarNames
    val values = extractValues(record)
    
    val datas: Seq[Any] = (values zip vnames) map { p =>
      metadata.findVariableProperty(p._2, "type") match {
        //TODO: handle conversion errors
        case Some("integer") => p._1.toLong
        case Some("real") => p._1.toDouble
        case Some("text") => p._1
      }
    }
      
    Some((vnames zip datas).toMap)
  }
  
  /**
   * Extract the Variable values from the given record.
   */
  def extractValues(record: String): Seq[String] = splitAtDelim(record)
  
  def splitAtDelim(str: String): Array[String] = str.trim.split(getDelimiter, -1)
  //Note, use "-1" so trailing ","s will yield empty strings.

}







