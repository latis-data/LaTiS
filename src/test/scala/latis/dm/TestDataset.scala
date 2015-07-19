package latis.dm

import latis.dm.implicits._
import org.junit._
import Assert._
import com.typesafe.scalalogging.LazyLogging
import latis.dm._
import latis.metadata.Metadata
import latis.time.Time
import latis.writer.AsciiWriter
import java.nio.ByteBuffer
import latis.data.value.DoubleValue
import latis.reader.tsml.TsmlReader

class TestDataset {

  @Test
  def extract_variable {
    TestDataset.real match {
      case Dataset(v) => //pass
      case _ => fail
    }
  }
  
  @Test
  def extract_variable_from_empty_dataset {
    Dataset.empty match {
      case Dataset(v) => fail
      case _ => //pass
    }
  }
  
  @Test
  def empty_dataset_equals_empty_dataset {
    assertEquals(Dataset.empty, Dataset.empty)
  }
  
  @Test
  def isEmpty_doesnt_consume_item {
    
    // This test is based off a bug that was first noticed
    // in TestMathExpressionDerivation.test_tsml()
    // 
    // For this dataset, calling isEmpty appears to consume
    // the first row of the iterator. To test: create 2
    // identical datasets from vecmag.tsml. Call isEmpty
    // on one of them, and then assert that they both
    // have the same data. If the test passes, the bug is
    // fixed.
    
    val ds1 = TsmlReader("vecmag.tsml").getDataset
    val ds2 = TsmlReader("vecmag.tsml").getDataset
    
    val fn1 = ds1.unwrap.asInstanceOf[Function]
    val fn2 = ds2.unwrap.asInstanceOf[Function]
    
    assertEquals(false, ds1.isEmpty)
    
    val it1 = fn1.iterator
    val it2 = fn2.iterator
    val zippedIts = it1.zip(it2)
    val columns = List("t", "a", "b", "c", "X")
    
    zippedIts.foreach(rowPair => {
      val row1 = rowPair._1
      val row2 = rowPair._2
      
      columns.foreach(col => {
        assertEquals(
          row1.findVariableByName(col).get.getNumberData.doubleValue,
          row2.findVariableByName(col).get.getNumberData.doubleValue,
          0.0
        )
      })
    })
  }
}

/**
 * Static datasets to use for testing.
 */
object TestDataset {
  
  //TODO: should we allow an empty Dataset, maybe from the monadic perspective
  //def empty = Dataset(List(), Metadata("emptyDS"))
  
  def real = Dataset(Real(Metadata("myReal"), 3.14), Metadata("realDS"))
  def integer = Dataset(Integer(Metadata("myInteger"), 42), Metadata("intDS"))
  def text = Dataset(Text(Metadata("myText"), "Hi"), Metadata("textDS"))
  def real_time = Dataset(Time(Metadata("myRealTime"), 1000.0), Metadata("timeDS"))
  def text_time = Dataset(Time(Metadata(Map("name" -> "myTextTime", "type" -> "text", "length" -> "10", "units" -> "yyyy-MM-dd")), "1970/01/01"), Metadata("text_timeDS"))
  def int_time = Dataset(Time(Metadata(Map("name" -> "myIntegerTime", "type" -> "integer")), 1000.toLong), Metadata("integer_timeDS"))
  //def scalars = Dataset(List(Real(Metadata("myReal"), 3.14), Integer(Metadata("myInteger"), 42), Text(Metadata("myText"), "Hi"), Time(Metadata("myRealTime"), 1000.0)), Metadata("scalarDS"))
  def binary = Dataset(Binary(Metadata("myBinary"), DoubleValue(1.1).getByteBuffer), Metadata("binaryDS"))
  def nan = Dataset(Real(Metadata("myReal"), Double.NaN), Metadata("nanDS"))
  
  def tuple_of_scalars = Dataset(Tuple(Integer(Metadata("myInteger"), 0), Real(Metadata("myReal"), 0), Text(Metadata("myText"), "zero")), Metadata("tupleDS"))
  def tuple_of_tuples = Dataset(Tuple(Tuple(Integer(Metadata("myInteger"), 0), Real(Metadata("myReal"), 0)), Tuple(Integer(Metadata("myInteger"), 1), Real(Metadata("myReal"), 1.1))), Metadata("tuple_of_tuplesDS"))
  def tuple_of_functions = Dataset(TestNestedFunction.tuple_of_functions, Metadata("tuple_of_functions"))
  def scalar_tuple = Dataset(Tuple(Integer(Metadata("myInteger"), 1)), Metadata("scalar_tuple"))
  def mixed_tuple = Dataset(Tuple(Real(Metadata("myReal"), 0.0), Tuple(Integer(Metadata("myInteger"), 0), Real(Metadata("myReal"), 0)), function_of_scalar.unwrap), Metadata("mixed_tuple"))
  def tuple_with_nan = Dataset(Tuple(Integer(Metadata("myInteger"), 0), Real(Metadata("myReal"), Double.NaN), Text(Metadata("myText"), "zero")), Metadata("tuple_with_nan"))
  
  def function_of_scalar = {
    val samples = List(Sample(Real(0), Real(0)), 
                       Sample(Real(1), Real(1)), 
                       Sample(Real(2), Real(2)))
    Dataset(Function(samples), Metadata("function_of_scalar"))
  }
  
  def function_of_scalar_with_nan = {
    val samples = List(Sample(Real(0), Real(0)), 
                       Sample(Real(1), Real(Double.NaN)), 
                       Sample(Real(2), Real(2)))
    Dataset(Function(samples), Metadata("function_of_scalar_with_nan"))
  }
  
  //should be invalid since it can't be sorted...
  def function_of_scalar_with_nan_in_domain = {
    val samples = List(Sample(Real(0), Real(0)), 
                       Sample(Real(Double.NaN), Real(1)), 
                       Sample(Real(2), Real(2)))
    Dataset(Function(samples), Metadata("function_of_scalar_with_nan_in_domain"))
  }
  
  def function_of_named_scalar = {
    val samples = List(Sample(Real(Metadata("t"), 0), Real(Metadata("a"), 0)), 
                       Sample(Real(Metadata("t"), 1), Real(Metadata("a"), 1)), 
                       Sample(Real(Metadata("t"), 2), Real(Metadata("a"), 2)))
    Dataset(Function(samples), Metadata("function_of_named_scalar"))
  }
  
  def function_of_scalar_with_length = {
    val samples = List(Sample(Real(0), Real(0)), 
                       Sample(Real(1), Real(1)), 
                       Sample(Real(2), Real(2)))
    Dataset(Function(samples, Metadata(Map("length"->"3"))), Metadata("function_of_scalar"))
  }
  
  def function_of_scalar_with_rounding = {
    val samples = List(Sample(Real(Metadata(Map("precision"->"2", "name"->"a")),-0.004), Integer(Metadata(Map("sigfigs"->"1", "name"->"b")),123)),
    				   Sample(Real(Metadata(Map("precision"->"2", "name"->"a")),1.001111), Integer(Metadata(Map("sigfigs"->"2", "name"->"b")),123)),
    				   Sample(Real(Metadata(Map("precision"->"2", "name"->"a")),1.995), Integer(Metadata(Map("sigfigs"->"3", "name"->"b")),123)))
    Dataset(Function(samples), Metadata("function_of_scalar_with_rounding"))
  }
  
  def function_of_tuple = {
    val samples = List(Sample(Integer(Metadata("myInteger"), 0), Tuple(Real(Metadata("myReal"), 0), Text(Metadata("myText"), "zero"))), 
                       Sample(Integer(Metadata("myInteger"), 1), Tuple(Real(Metadata("myReal"), 1), Text(Metadata("myText"), "one"))), 
                       Sample(Integer(Metadata("myInteger"), 2), Tuple(Real(Metadata("myReal"), 2), Text(Metadata("myText"), "two"))))
    Dataset(Function(samples), Metadata("function_of_tuple"))
  }
  
  def function_of_tuple_with_nan = {
    val samples = List(Sample(Integer(Metadata("myInteger"), 0), Tuple(Real(Metadata("myReal"), 0), Text(Metadata("myText"), "zero"))), 
                       Sample(Integer(Metadata("myInteger"), 1), Tuple(Real(Metadata("myReal"), Double.NaN), Text(Metadata("myText"), "one"))), 
                       Sample(Integer(Metadata("myInteger"), 2), Tuple(Real(Metadata("myReal"), 2), Text(Metadata("myText"), "two"))))
    Dataset(Function(samples), Metadata("function_of_tuple"))
  }
  
  def function_of_functions = Dataset(TestNestedFunction.function_of_functions_with_data_in_scalars, Metadata("function_of_functions"))
  
  def function_of_functions2 = Dataset(TestNestedFunction.function_of_functions_with_sampled_data, Metadata("function_of_functions2"))
  
  def function_of_functions_text = Dataset(TestNestedFunction.function_of_functions_with_text_data, Metadata("function_of_functions_text"))
  
  def mixed_function = {
    val samples = List(Sample(Real(Metadata("myReal"), 0.0), Tuple(Tuple(Integer(Metadata("myInteger"), 0), Real(Metadata("myReal"), 0)), (function_of_scalar+(0)).unwrap)),
                       Sample(Real(Metadata("myReal"), 1.1), Tuple(Tuple(Integer(Metadata("myInteger"), 1), Real(Metadata("myReal"), 1)), (function_of_scalar+(1)).unwrap)),
                       Sample(Real(Metadata("myReal"), 2.2), Tuple(Tuple(Integer(Metadata("myInteger"), 2), Real(Metadata("myReal"), 2)), (function_of_scalar+(2)).unwrap)))
    Dataset(Function(samples), Metadata("mixed_function"))
  }
  
  def canonical = {
    val md = Map("name" -> "myTime", "type" -> "text", "length" -> "10", "units" -> "yyyy/MM/dd")
    val samples = List(Sample(Time(Metadata(md), "1970/01/01"), Tuple(Integer(Metadata("myInt"), 1), Real(Metadata("myReal"), 1.1), Text(Metadata("myText"), "A"))),
                       Sample(Time(Metadata(md), "1970/01/02"), Tuple(Integer(Metadata("myInt"), 2), Real(Metadata("myReal"), 2.2), Text(Metadata("myText"), "B"))),
                       Sample(Time(Metadata(md), "1970/01/03"), Tuple(Integer(Metadata("myInt"), 3), Real(Metadata("myReal"), 3.3), Text(Metadata("myText"), "C"))))
    Dataset(Function(samples), Metadata("canonical"))
  }
  
  def time_series = {
    val md = Map("name" -> "myTime", "type" -> "text", "length" -> "10", "units" -> "yyyy/MM/dd", "alias"->"time")
    val samples = List(Sample(Time(Metadata(md), "1970/01/01"), Real(Metadata("myReal"), 1.1)),
                       Sample(Time(Metadata(md), "1970/01/02"), Real(Metadata("myReal"), 2.2)),
                       Sample(Time(Metadata(md), "1970/01/03"), Real(Metadata("myReal"), 3.3)))
    Dataset(Function(samples), Metadata("time_series"))
  }
  
  def numeric_time_series = {
    val md = Map("name" -> "myTime", "type" -> "real", "units" -> "days since 2000-01-01", "alias"->"time")
    val samples = List(Sample(Time(Metadata(md), 0.0), Real(Metadata("myReal"), 1.1)),
                       Sample(Time(Metadata(md), 1.0), Real(Metadata("myReal"), 2.2)),
                       Sample(Time(Metadata(md), 2.0), Real(Metadata("myReal"), 3.3)))
    Dataset(Function(samples), Metadata("numeric_time_series"))
  }
  
  def empty_function = Dataset(Function(Real(Metadata("domain")), Real(Metadata("range")), Iterator.empty), Metadata("empty_function"))
  
  def index_function = Dataset(Function(List(Integer(1), Integer(2))), Metadata("indexFunctionDS"))
  
  def combo = Dataset(Tuple(function_of_tuple.unwrap, tuple_of_tuples.unwrap, text.unwrap), Metadata("combo"))
  
  def tuple_domain = {
    val samples = List(Sample(Tuple(Real(Metadata("lon"),0.0), Real(Metadata("lat"), 90.0)), Real(Metadata("x"), 0)),
                       Sample(Tuple(Real(Metadata("lon"),90.0), Real(Metadata("lat"), 0.0)), Real(Metadata("x"), 1)),
                       Sample(Tuple(Real(Metadata("lon"),180.0), Real(Metadata("lat"), -90.0)), Real(Metadata("x"), 2)))
    Dataset(Function(samples), Metadata("tuple_domain"))
  }
  
//  def datasets = Seq(empty, real, integer, text, real_time, text_time, int_time, scalars, binary, tuple_of_scalars,
//                     tuple_of_tuples, tuple_of_functions, scalar_tuple, mixed_tuple, function_of_scalar,
//                     function_of_tuple, function_of_functions, mixed_function, empty_function, index_function, combo)
}
