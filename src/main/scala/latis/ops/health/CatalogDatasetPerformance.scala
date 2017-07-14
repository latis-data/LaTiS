package latis.ops.health

import com.typesafe.scalalogging.LazyLogging
import latis.dm.Dataset
import latis.dm.Function
import latis.dm.Sample
import latis.dm.Text
import latis.dm.Tuple
import latis.metadata.Metadata
import latis.ops.Operation
import latis.ops.OperationFactory
import latis.ops.filter.FirstFilter
import latis.reader.DatasetAccessor

/* 
 * Given a catalog dataset following the DCAT ontology, describe 
 * basic performance statistics from each dataset such as 
 * access time and memory usage. 
 */
class CatalogDatasetPerformance extends Operation with LazyLogging {
 
  /**
   * Apply this Operation to the given Dataset.
   * (Overriden to change the dataset's name)
   */
  override def apply(dataset: Dataset): Dataset = dataset match { 
    case Dataset(variable) => {
      val md = dataset.getMetadata
      applyToVariable(variable) match {
        case Some(v) => Dataset(v, md + ("name", "dataset_performance"))
        case None => Dataset.empty
      }
    }
    case _ => dataset
  }
  
  /*
   * Aquire performance statistics from the datasets listed in 
   * this catalog.
   */
  override def applyToFunction(function: Function): Option[Function] = {
    function match {
      case Function(it) => {
        val samples = it.map { s => s match {
          //TODO: Consider matching sample's range as well
          case Sample(Text(name), _) => {
            val results = getPerformanceResults(name)
            
            Sample(Text(Metadata("ds_name"), name), 
              Tuple(Seq(Text(Metadata("alive"), results._1),
                Text(Metadata("access_time"), results._2),
                Text(Metadata("memory_usage"), results._3))))
          }
          //Case only matches when sample's domain is not Text 
          case _ => Sample(Text(Metadata("ds_name"), "[INVALID RECORD]"), 
                      Tuple(Seq(Text(Metadata("alive"), "N/A"),
                          Text(Metadata("access_time"), "N/A"),
                          Text(Metadata("memory_usage"), "N/A"))))              
          }   
        }
        Some(Function(samples.toSeq)) 
      }
    }
  }
  
  /*
   * Given a dataset name, assess the performance of that dataset and return a
   * tuple of all the results (whether the ds is alive, how many seconds 
   * it took to read the first sample, and how much memory it used).  
   * This is all done concurrently to avoid duplicate operations. 
   * Resulting tuple will take the form: (alive, time, memUsage) 
   */
  def getPerformanceResults(name: String): (String, Double, Double) = {
    System.gc //Run the garbage collector to *greatly* improve accuracy of mem usage.
              //This does not impart a noticable performance hit here.
    
    val startTime = System.nanoTime
    val startFreeMem = Runtime.getRuntime.freeMemory
    
    val isAlive = requestWholeDataset(name)
    
    val endFreeMem = Runtime.getRuntime.freeMemory
    val endTime = System.nanoTime
    val timeDiff = (endTime - startTime)/1e9 //converts nanoseconds to seconds
    val memUsed = (startFreeMem - endFreeMem)/1e6 //converts bytes to megabytes                  
   
    logger.info("PERFORMANCE: dsName=" + name + ", alive=" + isAlive + ", timeUsed(s)=" + timeDiff + ", memoryUsed(MB)=" + memUsed + " ")
                         
    (isAlive.toString, timeDiff, memUsed)
  }
  
  /*
   * Return true once a dataset of a given name has served all of its data.
   * If a dataset fails to load, return false. 
   */
  def requestWholeDataset(name: String): Boolean = {
    try {
      val ds = DatasetAccessor.fromName(name).getDataset.force
      ds match {
        case Dataset(Function(it)) => it.hasNext
        case _ => false
      }
    } 
    catch {
      case e: Throwable => 
        logger.info("Dataset access failed for " + name + ". Unable to read from LaTiS: " + e.getMessage); false
    } 
  }
  
  
}

object CatalogDatasetPerformance extends OperationFactory {
  override def apply(): CatalogDatasetPerformance = new CatalogDatasetPerformance
}