package latis.util

import latis.data.Data
import latis.dm.Integer
import latis.dm.Real
import latis.dm.Text
import latis.dm.Variable
import java.net.URL
import java.io.File
import java.net.URLEncoder
import java.nio.ByteBuffer

/**
 * Utility methods for manipulating Strings.
 */
object StringUtils {
    
  /**
   * Resolve any property references in the given string.
   * e.g. ${dataset.dir}
   * This will ask LatisProperties which has access to properties from
   * the latis.properties file, systems properties, and environment variables.
   */
  def resolveParameterizedString(s: String): String = {
    //val pattern = """${(.+)}""".r //TODO: restrict to \w\\.
    //pattern.replaceAllIn(s, (m: Match) => LatisProperties(m.matched))
    //TODO: support more than one parameter?
    //TODO: default with ${foo:-default} like logback (and bash)
    
    s.indexOf("${") match {
      case -1 => s
      case i1: Int => s.indexOf('}', i1) match {
        case -1 => s
        case i2: Int => {
          val prop = s.substring(i1 + 2, i2)
          LatisProperties.get(prop) match {
            case Some(p) => s.substring(0,i1) + p + s.substring(i2 + 1)
            case None => s //TODO: error, property not found? but would disallow legit values of that form
          }
        }
      }
    }
  }
  
  /**
   * Return the given string padded or truncated to the given length (number of characters).
   * If the String is shorter than the desired length, it will be padded with spaces on the right.
   * If the String is longer than the desired length, it will be truncated on the right.
   */
  def padOrTruncate(s: String, length: Int): String = s.length match {
    case l: Int if (l < length) => s.padTo(length, ' ')
    case l: Int if (l > length) => s.substring(0, length)
    //otherwise, the size is just right
    case _ => s
  }
  /**
   * Return the given string padded or truncated to the length of the given Variable.
   * If the Variable is not Text, return the original string.
   */
  def padOrTruncate(s: String, template: Variable): String = template match {
    case t: Text => padOrTruncate(s, t.length)
    case _ => s
  }
  
  /**
   * Make sure the given string is surrounded in quotes.
   */
  def quoteStringValue(s: String): String = {
    //don't add if it is already quoted
    "'" + s.replaceAll("^['\"]","").replaceAll("['\"]$","") + "'"
  }
  
  /**
   * Can this string be converted to a Double.
   */
  def isNumeric(s: String): Boolean = {
    try {
      s.toDouble
      true
    } catch {
      case e: NumberFormatException => false
    }
  }
  
  /**
   * Convert the given String to a Double.
   * If not convertible, return NaN.
   */
  def toDouble(s: String): Double = {
    try {
      s.toDouble
    } catch {
      case e: Exception => Double.NaN
    }
  }
  
  /**
   * Convert the first nchar * 2 bytes from the given ByteBuffer into a String
   * of nchar characters. The factor of 2 is needed because a char is 2 bytes in Java.
   */
  def byteBufferToString(bb: ByteBuffer, nchar: Int): String = {
    val cs = new Array[Char](nchar)      //allocate an array of chars just big enough for our String
    bb.asCharBuffer.get(cs)              //load the array (cs) with nchar*2 bytes
    bb.position(bb.position() + nchar * 2) //advance position in underlying buffer
    new String(cs)                       //make a String out of our array
  }
  
  /**
   * Construct Data from a String by matching a Variable template
   */
  //TODO: support regex property for each variable
  def parseStringValue(value: String, variableTemplate: Variable): Data = variableTemplate match {
    case _: Integer => if(isNumeric(value)) Data(toDouble(value).toLong)
      else Data(variableTemplate.asInstanceOf[Integer].getFillValue.asInstanceOf[Long])
      
    case _: Real => if(isNumeric(value)) Data(toDouble(value))
      else Data(variableTemplate.asInstanceOf[Real].getFillValue.asInstanceOf[Double])
      
    case t: Text    => Data(StringUtils.padOrTruncate(value, t.length)) //enforce length
  }
  
  /**
   * Construct Data from a String by matching a variable type string.
   */
  def parseStringValue(value: String, vtype: String): Data = vtype match {
    case "integer" => if(isNumeric(value)) Data(toDouble(value).toLong)
      else Data.empty //TODO: Data(variableTemplate.asInstanceOf[Integer].getFillValue.asInstanceOf[Long])
      
    case "real" => if(isNumeric(value)) Data(toDouble(value))
      else Data.empty //TODO: Data(variableTemplate.asInstanceOf[Real].getFillValue.asInstanceOf[Double])
      
    case "text" => Data(StringUtils.padOrTruncate(value, value.length)) //TODO: enforce max length
  }
  
  //TODO: find a better home, See (LATIS-619)
  def getUrl(loc: String): URL = {
    if(loc.matches("""\w+:.+""")) new URL(loc) //absolute
    else if (loc.startsWith(File.separator)) new URL("file:" + loc) 
    else getClass.getResource("/"+loc) match { //relative path: try looking in the classpath
      case url: URL => url
      case null => {  
        val dir = scala.util.Properties.userDir
        new URL("file:" + dir + File.separator + loc) //relative to current working directory
      }
    }
  }
  
}