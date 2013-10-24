package latis.reader.tsml

import latis.dm._
import scala.collection._
import latis.time.Time
import latis.reader.tsml.ml.ScalarMl
import latis.reader.tsml.ml.Tsml
import latis.data.EmptyData

/**
 * An Adapter for Datasets small enough to fit into memory.
 * This Adapter will read all the data in the Dataset and 
 * store it in a column-oriented store (i.e. each Scalar's
 * data are stored in a separate Data object).
 */
abstract class GranuleAdapter(tsml: Tsml) extends TsmlAdapter(tsml) {
  //TODO: dataMap should contain Data, impl Data with Seq[String]?
  lazy val dataMap: immutable.Map[String, immutable.Seq[String]] = readData

  /**
   * Subclasses need to implement this method to read all of the data
   * into the dataMap.
   * This will be invoked lazily, when the dataMap is first accessed.
   */
  def readData: immutable.Map[String, immutable.Seq[String]]
  
  /**
   * Override to construct Scalars using the data read by this Adapter.
   * Note that only Scalars can have Data with this column-oriented Adapter.
   */
  override protected def makeScalar(sml: ScalarMl): Option[Scalar] = {
    val md = makeMetadata(sml)
    
    val data = dataMap.getOrElse(md("name"), immutable.Seq[String]())
    //note, will be empty if not a named variable (e.g. index)
    //TODO: consider broader applicability
        
    sml.label match {
      case "real" => Some(Real(md, data.map(_.toDouble)))
      case "integer" => Some(Integer(md, data.map(_.toLong)))
      case "text" => Some(Text(md, data))
      case "time" => {
        //if numeric units
        md.get("units") match {
          case Some(u) => {
            //support real or int
            if (u.contains(" since ")) {
              val values = md("type") match {
                case "integer" => data.map(_.toLong)
                case _ => data.map(_.toDouble) //default to double times
              }
              Some(Time(md, values))
            }
            else Some(Time.fromStrings(md, data))
          }
          case None => Some(Time(md, data.map(_.toDouble))) //default to double times
        }
      }
      
      case "index" => {
        //get the number of samples for one of the variables
        val length = dataMap.last._2.length
        Some(Index.withLength(length))
      }
      
      case _ => None
    }
  }

}