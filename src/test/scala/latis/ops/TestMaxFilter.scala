package latis.ops

import latis.dm.Function
//import latis.dm.implicits._
import latis.dm.Real
import latis.metadata.Metadata
import latis.data.SampledData
import org.junit._
import Assert._
import latis.ops.filter._
import latis.dm.Dataset
import latis.dm.TestDataset
import latis.dm.Sample
import latis.writer.Writer
import latis.dm.Integer
import latis.dm.Tuple
import latis.dm.Text
import latis.dm.Index
import latis.time.Time
import latis.writer._

class TestMaxFilter {
  
  //@Test
  def test_canonical = {
    val ds1 = TestDataset.canonical
    val ds2 = MaxFilter("myText")(ds1)
    latis.writer.AsciiWriter.write(ds2) 
  }
  
  @Test
  def canonical_max_int = {
    val ds1 = TestDataset.canonical
    val ds2 = MaxFilter("myInt")(ds1)
    
    ds2 match {
      case Dataset(Function(it)) => {
        it.next match {case Sample(Text(t), Tuple(Seq(Integer(i), Real(r), Text(txt)))) => 
          assertEquals(3, i)}
      }
    }
  }
  
  @Test
  def canonical_max_real = {
    val ds1 = TestDataset.canonical
    val ds2 = MaxFilter("myReal")(ds1)
    
    ds2 match {
      case Dataset(Function(it)) => {
        it.next match {case Sample(Text(t), Tuple(Seq(Integer(i), Real(r), Text(txt)))) => 
          assertEquals(3.3, r, 0.001)}
      }
    }
  }
  
  @Test
  def canonical_max_text = {
    val ds1 = TestDataset.canonical
    val ds2 = MaxFilter("myText")(ds1)
    
    ds2 match {
      case Dataset(Function(it)) => {
        it.next match {case Sample(Text(t), Tuple(Seq(Integer(i), Real(r), Text(txt)))) => 
          assertEquals("C", txt)}
      }
    }
  }
  
  @Test
  def canonical_max_invalid_name = {
    val ds1 = TestDataset.canonical
    val ds2 = MaxFilter("ShawnIsACoolDude")(ds1)
    
    ds2 match {
      case Dataset(Function(it)) => {
        assert(!it.hasNext)
      }
    }
  }
  
  @Test
  def canonical_max_length = {
    val ds1 = TestDataset.canonical
    val ds2 = MaxFilter("myInt")(ds1)
    
    ds2 match {
      case Dataset(func) => {
        assertEquals("1", func.getMetadata("length").get)
      }
    }
  }

  @Test
  def one_max_in_middle = {
    //val samples = List(1,2,3).map(i => Sample(Real(Metadata("t"), i), Real(Metadata("a"), i*2)))
    //val tuple = Tuple(Integer(Metadata("int1"), 5), Integer(Metadata("int2"), 5), Integer(Metadata("int3"), 1))
    
    //Create samples for test dataset
    val sample1 = Sample(Integer(Metadata("t"), 1), Tuple(Integer(Metadata("int1"), 2), Integer(Metadata("int2"), 1)))
    val sample2 = Sample(Integer(Metadata("t"), 2), Tuple(Integer(Metadata("int1"), 5), Integer(Metadata("int2"), 1)))
    val sample3 = Sample(Integer(Metadata("t"), 3), Tuple(Integer(Metadata("int1"), 3), Integer(Metadata("int2"), 1)))
    
    val samples = List(sample1, sample2, sample3)
    
    //Create dataset and run test
    val ds1 = Dataset(Function(samples, Metadata("function")), Metadata("dataset"))
    val ds2 = MaxFilter("int1")(ds1)
    AsciiWriter.write(ds2)
    ds2 match {
      case Dataset(Function(it)) => {
        it.next match {
          case Sample(Integer(t), Tuple(Seq(Integer(i1), Integer(i2)))) => assertEquals( (2, (5, 1)), (t, (i1, i2)) )
        }  
        assert(!it.hasNext)
      }
    }
  }
  
  @Test
  def three_maxes_in_middle = {
    //Create samples for test dataset
    val sample1 = Sample(Integer(Metadata("t"), 1), Tuple(Integer(Metadata("int1"), 1), Integer(Metadata("int2"), 2)))
    val sample2 = Sample(Integer(Metadata("t"), 2), Tuple(Integer(Metadata("int1"), 1), Integer(Metadata("int2"), 5)))
    val sample3 = Sample(Integer(Metadata("t"), 3), Tuple(Integer(Metadata("int1"), 1), Integer(Metadata("int2"), 5)))
    val sample4 = Sample(Integer(Metadata("t"), 4), Tuple(Integer(Metadata("int1"), 1), Integer(Metadata("int2"), 5)))
    val sample5 = Sample(Integer(Metadata("t"), 5), Tuple(Integer(Metadata("int1"), 1), Integer(Metadata("int2"), 3)))
    
    val samples = List(sample1, sample2, sample3, sample4, sample5)
    
    //Create dataset and run tests
    val ds1 = Dataset(Function(samples, Metadata("function")), Metadata("dataset"))
    val ds2 = MaxFilter("int2")(ds1)
    AsciiWriter.write(ds2)
    ds2 match {
      case Dataset(Function(it)) => {
        it.next match {
          case Sample(Integer(t), Tuple(Seq(Integer(i1), Integer(i2)))) => assertEquals( (2, (1, 5)), (t, (i1, i2)) )
        }  
        it.next match {
          case Sample(Integer(t), Tuple(Seq(Integer(i1), Integer(i2)))) => assertEquals( (3, (1, 5)), (t, (i1, i2)) )
        }  
        it.next match {
          case Sample(Integer(t), Tuple(Seq(Integer(i1), Integer(i2)))) => assertEquals( (4, (1, 5)), (t, (i1, i2)) )
        }  
        assert(!it.hasNext)
      }
    }
  }
  
  
}