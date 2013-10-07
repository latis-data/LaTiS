package latis.reader.tsml

import latis.reader.DatasetAccessor
import latis.dm._
import scala.xml._
import latis.metadata._
import scala.collection.mutable.{Map,HashMap}
import java.net.URL
import java.io.File
import latis.ops.Operation
import latis.reader.tsml.ml.Tsml

/**
 * A Reader for a Dataset defined as a "dataset" element within TSML.
 * If the TSML URL ends with "#label", use that to reference a nested 
 * dataset element with that name attribute. 
 */
class TsmlReader(tsml: Tsml) extends DatasetAccessor {
  
  /**
   * The adapter as defined in the TSML for reading the Dataset.
   */
  lazy val adapter: TsmlAdapter = TsmlAdapter(tsml)

  
  /**
   * The Dataset that this Reader is responsible for.
   * Delegate to the Adapter to construct the Dataset
   * from the TSML's single top level "dataset" element.
   */
  lazy val dataset: Dataset = adapter.dataset
  //TODO: need to be lazy? adapter's is, but could be overridden
  
  /**
   * Return the LaTiS Dataset that TSML represents.
   */
  def getDataset(): Dataset = dataset
  
  //def getDataset(operations: Map[String,Seq[String]]): Dataset = {
  def getDataset(operations: Seq[Operation]): Dataset = {
    adapter.getDataset(operations)
  }
  
  /**
   * Clean up any resources that the reader used.
   */
  def close() = adapter.close()
  
  
}

object TsmlReader {
  
  def apply(url: URL) = new TsmlReader(Tsml(url))

  def apply(path: String) = new TsmlReader(Tsml(path))
  
}







