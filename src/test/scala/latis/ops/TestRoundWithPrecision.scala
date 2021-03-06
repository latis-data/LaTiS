package latis.ops

import org.junit._
import Assert._
import latis.dm._
import latis.metadata.Metadata

class TestRoundWithPrecision {
  val test: Metadata = Metadata("test")
  val testMetadata: Metadata = Metadata(("name", "u"))

  val testInteger1: Integer = Integer(Metadata("t"), 1)
  val testInteger2: Integer = Integer(Metadata("t"), 2)
  val testInteger3: Integer = Integer(Metadata("t"), 3)
  val testInteger4: Integer = Integer(Metadata("t"), 4)


  @Test
  def test_user_specified_precision {
    val testSample: Sample = Sample(testInteger1, Real(testMetadata, 2.123))

    val testSamples: List[Sample] = List(testSample)

    val asserts: List[Double] = List(2.12)

    val ds: Dataset = Dataset(Function(testSamples, test), test)
    val ds2: Dataset = RoundWithPrecision(Seq("u", "2"))(ds)
    //val ds2: Dataset = RoundWithPrecision("u", 2)(ds)

    ds2 match {
      case Dataset(Function(it)) => it.next match {
          case Sample(_, Real(r)) => assertEquals(asserts(0), r, 0.0)
      }
    }
  }


  @Test
  def test_invalid_user_precision {

    val testSample: Sample = Sample(testInteger1, Real(testMetadata, 2.123))

    val testSamples: List[Sample] = List(testSample)

    val ds: Dataset = Dataset(Function(testSamples, test), test)
    try {
      val ds2: Dataset = RoundWithPrecision("u", -1)(ds)
      assertEquals("Error Checking Failed", 0, 1)
    }
    catch {
      case e: RuntimeException => assertEquals("Precision must be a postive integer", e.getMessage)
    }
  }
}
