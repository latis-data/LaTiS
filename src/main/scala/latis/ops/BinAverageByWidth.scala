package latis.ops

import latis.dm._
import latis.util.iterator.MappingIterator
import latis.util.LatisServiceException
import scala.collection.Map
import scala.collection.mutable.ListBuffer
import scala.math._
import latis.util.iterator.PeekIterator
import latis.time.Time
import latis.metadata.Metadata
import latis.data.SampledData
import latis.ops.filter.ExcludeMissing

/**
 * Assume 1D domain.
 * If range is tuple, take first element, for now.
 * Nested Functions not yet supported.
 */
class BinAverageByWidth(binWidth: Double, startVal: Double = Double.NaN) extends Operation {
  //TODO: accept ISO 8601 time duration
  //TODO: start with min of time coverage instead of 1st sample
  //TODO: deal with 'length' metadata
  //TODO: deal with missing values
  //TODO: take domain var arg so we can bin nested functions, akin to integration
  
  if (binWidth <= 0) throw new LatisServiceException("Bin average must have a positive bin width.")
  
  /**
   * Get bin width via getter so it can be overridden.
   */
  def getBinWidth: Double = binWidth
  
  override def applyToFunction(f: Function): Option[Variable] = {
    val fit = PeekIterator(f.iterator)
    //If the function has no samples, make a new Function around this iterator (since we can't reuse the orig)
    if (fit.isEmpty) Some(Function(f, fit))
    else {
      //Get initial domain value so we know where to start,
      //default to first sample in the data if no startVal was provided in constructor.
      //Currently, an error is thrown if requested startVal is greater than first sample
      val firstSampleTime = getDomainValue(fit.peek)
      val startValue = if (!startVal.isNaN && startVal <= firstSampleTime) startVal      
                       else if (!startVal.isNaN && startVal > firstSampleTime) {
                         throw new LatisServiceException("Requested start value should not be after the start of data")
                       }
                       else firstSampleTime                     
 
      val domainType = f.getDomain //Used in pattern match to decide between Time or Real
      val domainMetadata = f.getDomain.getMetadata
      val rangeMetadata = reduce(f.getRange).getMetadata //first scalar

      val sampleIterator = getIteratorOfBinnedSamples(fit, startValue, domainType, domainMetadata, rangeMetadata)
      val sampleTemplate = sampleIterator.peek
      
      Some(Function(sampleTemplate.domain, sampleTemplate.range, sampleIterator))
    }
  }
  
  /*
   * Make an iterator of new samples in bins
   */
  private def getIteratorOfBinnedSamples(
      fit: PeekIterator[Sample],
      nextVal: Double,
      dType: Variable,
      dMetadata: Metadata,
      rMetadata: Metadata): PeekIterator[Sample] = {
    
    var nextValue = nextVal
    new PeekIterator[Sample] {
      def getNext = {
        nextValue += getBinWidth
        if (fit.isEmpty) null
        else {
          //accumulate the samples for this bin
          val binnedSamples = ListBuffer[Sample]()
          while (fit.hasNext && getDomainValue(fit.peek) < nextValue) binnedSamples += fit.next
            
          //create domain with bin center as its value, reuse original metadata
          //TODO: munge metadata
          val domainValue = nextValue - (0.5 * getBinWidth) //bin center    
            
          val domain = dType match { //Note this pattern match only cares about type, not the sample itself
            case _: Time => Time(dMetadata, domainValue)
            case _ => Real(dMetadata, domainValue)
          } 
            
          val range = computeRangeStatistics(binnedSamples, rMetadata)
          Sample(domain, range)
        }
      }
    }
  }
  
  /*
   * Compute statistics on range values
   */
  private def computeRangeStatistics(bins: ListBuffer[Sample], rMeta: Metadata): Tuple = {
    computeStatistics(bins) match {
      case Some(range) => range
      case None => {
        //fill empty bin with NaNs
        //TODO: or call getNext if we want to skip empty bins
        val mean = Real(rMeta, Double.NaN)
        val min  = Real(Metadata("min"), Double.NaN)
        val max  = Real(Metadata("max"), Double.NaN)
        val count = Real(Metadata("count"), 0)
        Tuple(mean, min, max, count) //TODO: add metadata, consider model for bins
      }
    }
  }
  
  /**
   * Extract the value of the domain variable in the given sample as a Double.
   * Error if not a 1D numeric Scalar. Time will be treated as unix ms.
   */
  private def getDomainValue(sample: Sample): Double = sample.domain match {
    case Number(d) => d
    case t: Time => t.getJavaTime.toDouble
    case _ => throw new LatisServiceException("BinAverage supports only one dimensional numeric domains.")
  }
  
  private def computeStatistics(samples: Seq[Sample]): Option[Tuple] = {
    //Assume all Samples have the same type
    //TODO: pattern match on range type so we can eventually extend this
    //TODO: make Function from these samples then apply mean, min, max?
    //TODO: consider CF binning conventions
    //TODO: use count to weigh bins

    /*
     * TODO: remove missing values
     * NaNs don't behave with min...
     * use exclude_missing operation?
     * use fill value instead of NaN to fill empty bins
     * 
     */
    //val samples2 = samples.map()
    
    if (samples.isEmpty) None
    else {
      //process only the first scalar, for now
      val rangeTemplate = reduce(samples.head.range)
      
      //get data as a Map: name -> array of values
      val data = samplesToDoubleMap(samples)
      val values = data.get(rangeTemplate.getName) match {
        case Some(vs) => vs
        case None => return None
      }
      
      val meanValue = values.sum / values.length
      val mean = Real(rangeTemplate.getMetadata, meanValue)
      
      //if the original data was already binned (i.e. has min and max value) then use them.
      val minValue = data.get("min") match {
        case Some(ms) => ms.min
        case None => values.min
      }
      val min = Real(Metadata("min"), minValue)
      
      val maxValue = data.get("max") match {
        case Some(ms) => ms.max
        case None => values.max
      }
      val max = Real(Metadata("max"), maxValue)
      
      val countValue = data.get("count") match {
        case Some(cs) => cs.sum
        case None => values.length
      }
      val count = Real(Metadata("count"), countValue)
      
      Some(Tuple(mean, min, max, count))
    }
  }
  
  //If the range is a tuple, just use the first element
  private def reduce(v: Variable): Scalar = v match {
    case s: Scalar => s
    case Tuple(vars) => reduce(vars.head)
    case _: Function => throw new LatisServiceException("Can't perform a bin average over a nested Function.")
  }
  
  private def samplesToDoubleMap(samples: Seq[Sample]): Map[String, Array[Double]] = {
    //make a dataset from these samples then op on it
    val ds = Dataset(Function(samples))
    //exclude samples with missing data
    val ds2 = ExcludeMissing()(ds)
    
    
    ds2.toDoubleMap
  }
}


object BinAverageByWidth extends OperationFactory {
  
  override def apply(args: Seq[String]): BinAverageByWidth = {
    if (args.length > 2) throw new LatisServiceException("The BinAverage operation only accepts up to two arguments")
    try {
      BinAverageByWidth(args.head.toDouble)
    } catch {
      case e: NumberFormatException => throw new LatisServiceException("The BinAverage operation requires numeric arguments")
    }
  }
    
  def apply(binWidth: Double, startVal: Double = Double.NaN): BinAverageByWidth = new BinAverageByWidth(binWidth, startVal)
}
