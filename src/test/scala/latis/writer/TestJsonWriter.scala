package latis.writer

import latis.dm.implicits._
import org.junit._
import Assert._
import com.typesafe.scalalogging.slf4j.Logging
import latis.dm._
import latis.metadata.Metadata
import latis.time.Time
import latis.dm.TestDataset

class TestJsonWriter {
  
  def real = Writer.fromSuffix("json").write(TestDataset.real)
  def integer = Writer.fromSuffix("json").write(TestDataset.integer)
  def text = Writer.fromSuffix("json").write(TestDataset.text)
  
  //@Test
  def test = Writer.fromSuffix("json").write(TestDataset.index_function)
  
  //@Test 
  def empty_dataset = Writer.fromSuffix("json").write(TestDataset.empty)
  
  //@Test
  def empty_function = Writer.fromSuffix("json").write(TestDataset.empty_function)
}