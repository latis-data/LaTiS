package latis.ops

import org.junit.Test
import scala.math._
import org.junit.Assert._
import latis.dm._
import latis.ops._
import latis.ops.health.CatalogDatasetLiveness
import latis.ops.filter.Selection
import latis.metadata.Metadata
import latis.writer.AsciiWriter
import scala.collection.mutable.ArrayBuffer
import latis.util.FileUtils
import latis.reader.CatalogReader
import org.junit.Ignore
 
class TestCatalogDatasetHealth {
  
  def generateFakeCatalog(operations: Seq[Operation]) = {
    //name=ascii_float_as_int (alive)
    //name=empty (dead)
    val sampleAlive = Sample(Text(Metadata("name"), "ascii_float_as_int"), Real(0))
    val sampleDead = Sample(Text(Metadata("name"), "empty"), Real(0))
    val samples = Seq(sampleAlive, sampleDead)
    
    val f = Function(samples, Metadata("datasets"))
    val dataset = Dataset(f, Metadata("FakeCatalog"))
    operations.foldLeft(dataset)((ds,op) => op(ds))
  }
  
  @Test  
  def check_health_dataset_creation = {
    val ops = ArrayBuffer[Operation]()
    ops += CatalogDatasetLiveness()
    val ds = generateFakeCatalog(ops)
    
    ds match {
      case Dataset(Function(it)) => it.next match { 
        case Sample(dsn: Text, TupleMatch(a: Text, _, _)) => {
          assertEquals("ds_name", dsn.getName)
          assertEquals("alive", a.getName)
          assertEquals("ascii_float_as_int", dsn.getValue)
          assertEquals("true", a.getValue)
        }
        case _ => fail
      }
    }
  } 

  @Test 
  def check_only_dead_datasets = {
    val ops = ArrayBuffer[Operation]()
    ops += CatalogDatasetLiveness()
    ops += Selection("alive=false")
    val ds = generateFakeCatalog(ops)
    
    ds match {
      case Dataset(Function(it)) => it.foreach { 
        s => s match {
          case Sample(_, TupleMatch(Text(alive), _, _)) => assertEquals("false", alive) 
        }
      }
    }
  }
  
  
//  @Test
//  def write_health_dataset = {
//    val ops = ArrayBuffer[Operation]()
//    ops += CatalogDatasetLiveness()
//    //val ds = CatalogReader().getDataset(ops)
//    val ds = generateFakeCatalog(ops)
//  
//    latis.writer.AsciiWriter.write(ds)
//  }    
  
//  @Test
//  def write_dead_datasets = {
//    val ops = ArrayBuffer[Operation]()
//    ops += CatalogDatasetLiveness()
//    ops += Selection("alive=false")
//    //val ds = CatalogReader().getDataset(ops)
//    val ds = generateFakeCatalog(ops)
//  
//    latis.writer.AsciiWriter.write(ds)
//  }
  
}