package latis.writer

import latis.dm._
import java.io._
import scala.collection.mutable.MapBuilder
import latis.time.Time
import latis.time.TimeScale

/**
 * Return data as nested arrays, without metadata.
 * One inner array for each sample.
 * Handy for clients that just want the data (e.g. HighCharts).
 * Like CSV, table as 2D array.
 */
class CompactJsonWriter extends JsonWriter {

  override def makeHeader(dataset: Dataset) = ""
  override def makeFooter(dataset: Dataset) = ""
  
//  //TODO: can we generalize to writeHeader, ...?
//  def write(dataset: Dataset) = {
//    _writer.print("[")
//    
//    //assume a single top level Function
//    val f = dataset.getVariables.find(_.isInstanceOf[Function]) match {
//      case Some(f: Function) => f
//      case _ => throw new RuntimeException("No Function found in dataset: " + dataset)
//    }
//    
//    writeTopLevelFunction(f)
//    
//    _writer.println("]")
//    _writer.flush()
//  }
//  
//  private def writeTopLevelFunction(f: Function) {
//    var startThenDelim = ""
//    for (Sample(domain, range) <- f.iterator) {
//      val d = varToString(domain)
//      val r = varToString(range)
//      //TODO: reduce resolution to keep volume down?
//      _writer.println(startThenDelim + "[" + d + "," + r + "]")
//      startThenDelim = ","
//    }
//  }
  
  override def makeLabel(variable: Variable): String = ""
    
  //override to remove {} in tuple
  override def makeTuple(tuple: Tuple): String = tuple match {
    case Sample(d: Index, r) => varToString(r) //drop Index domain
 //TODO: need to flatten when range is tuple (or function?)
 //  think about row vs col major
    
    case Tuple(vars) => vars.map(varToString(_)).mkString("[", ",", "]")
  }
  
//  private def varToString(variable: Variable): String = variable match {
//    case t: Time => t.getJavaTime.toString  //use java time for json
//    case Number(d) => d.toString //TODO: format?
//    //TODO: Integer vs Real?
//    case Text(s) => "\"" + s.trim + "\"" //put quotes around text data
//    case Tuple(vars) => vars.map(varToString(_)).mkString(",")
//    case f: Function => ??? //TODO: deal with inner Function, borrow from non-flattened csv
//  }
  
  override def mimeType: String = "application/json" 
  
}