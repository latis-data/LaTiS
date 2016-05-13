package latis.writer

import latis.dm.Binary
import latis.dm.Dataset
import latis.dm.Function
import latis.dm.Index
import latis.dm.Integer
import latis.dm.Real
import latis.dm.Sample
import latis.dm.Scalar
import latis.dm.Text
import latis.dm.Tuple
import latis.dm.Variable
import latis.util.FirstThenOther

import java.io.PrintWriter

/**
 * Generic writer for tabulated ASCII output.
 */
class TextWriter extends Writer {
  //TODO: consider putting make* methods (that return Strings and require no state) in companion object so others can use them
  //  e.g. JsonMetadataAndDataWriter needs content from two
  
  private[this] lazy val _writer = new PrintWriter(getOutputStream)
  def printWriter: PrintWriter = _writer
  
  /**
   * Platform independent new-line.
   */
  val newLine = System.getProperty("line.separator") //"\n"
    
  /**
   * Get delimiter from writer properties. Default to comma plus space.
   */
  lazy val delimiter = getProperty("delimiter", ", ")
    
  /**
   * Write a header before writing data values.
   */
  def writeHeader(dataset: Dataset) = printWriter.print(makeHeader(dataset)) //NOTE: just "print" w/o nl
  
  /**
   * Write a footer after writing data values.
   */
  def writeFooter(dataset: Dataset) = printWriter.print(makeFooter(dataset)) //NOTE: just "print" w/o nl
  
  /**
   * Extension point to define a header.
   */
  def makeHeader(dataset: Dataset): String = ""
  
  /**
   * Extension point to define a footer.
   */
  def makeFooter(dataset: Dataset): String = ""
    
  /**
   * Main entry point for writing a Dataset.
   */
  def write(dataset: Dataset) {
    writeHeader(dataset)
    writeVariable(dataset.unwrap) //see JsonWriter for treating Dataset as a Tuple
    
    writeFooter(dataset)
    printWriter.flush()
  }
  
  /**
   * Write the given Variable recursively.
   * Designed for top level Variables so we can support arbitrarily large datasets.
   * Otherwise, use "make*" to create a String.
   */
  def writeVariable(variable: Variable): Unit = variable match {
    case f: Function => writeFunction(f)
    case _ => printWriter.println(varToString(variable)) //new line for each top level variable
  }
  
  /**
   * Write the given Function one sample at a time.
   * Designed for top level Functions so we can support arbitrarily large datasets.
   * Otherwise, use "makeFunction" to create a String.
   */
  def writeFunction(function: Function) {
    val startThenDelim = FirstThenOther("", newLine)
    for (sample <- function.iterator) {
      val s = varToString(sample)
      printWriter.print(startThenDelim.value + s)
    }
    printWriter.println
  }
  
  /**
   * Recursively build String representation of the given Variable's data.
   */
  def varToString(variable: Variable): String = variable match {
    case   scalar: Scalar   => makeScalar(scalar)
    case   sample: Sample   => makeSample(sample)
    case    tuple: Tuple    => makeTuple(tuple)
    case function: Function => makeFunction(function)
  }
  
  /**
   * Convert Scalar value to a String.
   */
  def makeScalar(scalar: Scalar): String = {
    val form = (scalar.getMetadata("precision"), scalar.getMetadata("sigfigs")) match {
      case (None, None) => None
      case (Some(precision), None) => "%."+precision.toInt+"f"
      case (None, Some(sigfigs)) => "%."+sigfigs.toInt+"g"
      case (_, _) => None
    }
    (form, scalar) match {
      // Indices cannot have precision or sigfigs
      case (_, Index(i))   => i.toString
      // Reals and Integers can
      case (None, Real(d)) => d.toString
      case (f:String, Real(d))    => f.format(d)
      case (None, Integer(l)) => l.toString
      case (f:String, Integer(l)) => f.format(l)
      // Text/Binary can't (shouldn't?)
      case (_, Text(s))    => s.trim
      case (_, Binary(b))  => "blob" //TODO: uuencode?
      //TODO: use Scalar.toStringValue?
      //TODO: deal with Time format
    }
  }
  
  /**
   * Save domain value to repeat for nested functions.
   */
  def makeSample(sample: Sample): String = sample match {
    case Sample(d, r: Function) => {
      prepend :+= varToString(d)
      varToString(r)
    }
    case _ => {
      if(prepend.isEmpty) makeTuple(sample)
      else prepend.mkString("", delimiter, delimiter) + makeTuple(sample)
    }
  }
  
  /**
   * Default String representation of a Tuple.
   * Delimited list of member Variables.
   */
  def makeTuple(tuple: Tuple): String = tuple match {
    case Sample(d: Index, r) => varToString(r) //drop Index domain
    case Tuple(vars) => vars.map(varToString(_)).mkString(delimiter)
  }
  
  /**
   * Default String representation of a function.
   * Delimited list of Samples.
   */
  def makeFunction(function: Function): String = {
    val s = function.iterator.map(varToString(_)).mkString("", newLine, "")
    prepend = prepend.dropRight(1)
    s
  }
  
  /**
   * Override mime type. Try to get from 'mimeType' writer property.
   * Default to "text/plain".
   */
  override def mimeType: String = getProperty("mimeType", "text/plain")
  
  protected var prepend = List[String]()
}
