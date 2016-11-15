package latis.reader

import scala.collection.mutable.ArrayBuffer

import org.junit.Assert._
import org.junit.Test

import latis.ops.Operation
import latis.ops.filter.FirstFilter
import latis.ops.filter.LastFilter
import latis.ops.filter.Selection
import latis.reader.tsml.TsmlReader

class TestFileJoinAdapter {
  
  @Test
  def first {
    val ops = ArrayBuffer[Operation]()
    ops += FirstFilter()
    val ds = TsmlReader("agg/agg_list_join.tsml").getDataset(ops)
    val data = ds.toDoubleMap
    assertEquals(0, data("T").head, 0.0)
  }
  
  @Test
  def last {
    val ops = ArrayBuffer[Operation]()
    ops += LastFilter()
    val ds = TsmlReader("agg/agg_list_join.tsml").getDataset(ops)
    val data = ds.toDoubleMap
    assertEquals(19, data("T").head, 0.0)
  }
  
  @Test
  def empty = {
    val ds = TsmlReader("agg/agg_list_join_empty.tsml").getDataset
    assertTrue(ds.isEmpty)
  }
  
  @Test
  def logs {
    val ops = ArrayBuffer[Operation]()
    val ds = TsmlReader("log/log_join.tsml").getDataset(ops)
    assertEquals(6, ds.getLength)
  }
  
  @Test
  def first_two_log_files {
    val ops = ArrayBuffer[Operation]()
    ops += Selection("time<2015-07-10")
    val ds = TsmlReader("log/log_join.tsml").getDataset(ops)
    val data = ds.toStringMap
    assertEquals("2015-07-01T10:11:12.136", data("time").last)
  }
  
  @Test
  def last_two_log_files {
    val ops = ArrayBuffer[Operation]()
    ops += Selection("time>2015-07-10")
    val ds = TsmlReader("log/log_join.tsml").getDataset(ops)
    val data = ds.toStringMap
    assertEquals("2015-07-12T10:11:12.136", data("time").head)
  }
  
  @Test
  def select_on_dataset_content = {
    val ops = ArrayBuffer[Operation]()
    ops += Selection(s"message =~ ..2")
    val ds = DatasetAccessor.fromName("log/log_join").getDataset(ops)
    val data = ds.toStrings
    assertEquals(3, data(0).length)
    assertEquals("1.2", data.last.head)
  }
}