package tw.edu.ntu.csie.liblinear.rdd

import scala.language.implicitConversions
import scala.reflect.ClassTag
import org.apache.spark.rdd.RDD

/**
 * Spark LIBLINEAR specific RDD functions.
 */
class RDDFunctions[T: ClassTag](self: RDD[T])
{

	def slaveReduce(f: (T, T) => T, numSlaves : Int): T = {
		var rdd = self
		if(numSlaves > 0)
		{
			rdd = rdd.coalesce(numSlaves, false)
		}
		rdd.reduce(f)
  	}
}

object RDDFunctions
{

	/** Implicit conversion from an RDD to RDDFunctions. */
	implicit def fromRDD[T: ClassTag](rdd: RDD[T]) = new RDDFunctions[T](rdd)
}
