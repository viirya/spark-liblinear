package tw.edu.ntu.csie.liblinear

import scala.math.{exp, log}
import scala.util.control.Breaks._

import org.apache.spark.SparkContext._
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.jblas.DoubleMatrix
import tw.edu.ntu.csie.liblinear.rdd.RDDFunctions._

/**
 * TronFunction defines necessary methods used for different optimization problems in TRON.
 */
abstract class TronFunction
{

    def gencwfRDD(dataPoints: RDD[DataPoint], w_broad : Broadcast[DoubleMatrix], param : Parameter): RDD[Array[Double]]

	def functionValue(dataPoints: RDD[DataPoint], w_broad : Broadcast[DoubleMatrix], param : Parameter, cwfRDD: RDD[Array[Double]]) : Double

	def gradient(dataPoints : RDD[DataPoint], w_broad : Broadcast[DoubleMatrix], param : Parameter, cwfRDD: RDD[Array[Double]]) : DoubleMatrix
	
	def hessianVector(dataPoints : RDD[DataPoint], w_broad : Broadcast[DoubleMatrix], param : Parameter, s : DoubleMatrix, cwfRDD: RDD[Array[Double]]) : DoubleMatrix

}

/**
 * TronLR implements TronFunction for L2-regularized Logistic Regression.
 */
class TronLR extends TronFunction
{

    override def gencwfRDD(dataPoints: RDD[DataPoint], w_broad : Broadcast[DoubleMatrix], param : Parameter): RDD[Array[Double]] = {
 		val C = param.C
		dataPoints.mapPartitions((blocks) => {
			val wB = w_broad.value
			var f_obj : Double = 0.0
            var z_array = Array[Double]()
			while(blocks.hasNext)
			{
				var p = blocks.next()
				var z = 0.0
				var i = 0
				while(i < p.index.length)
				{
					z += p.value(i) * wB.get(p.index(i))
					i += 1
				}
				var yz = p.y * z
				var enyz = exp(-yz)
                z_array = z_array :+ enyz
			}
			Seq(z_array).iterator
        })
    }

	override def functionValue(dataPoints: RDD[DataPoint], w_broad : Broadcast[DoubleMatrix], param : Parameter, cwfRDD: RDD[Array[Double]]) : Double =
	{
		val C = param.C
		val f = dataPoints.zipPartitions(cwfRDD) ((blocks, cwf) => {
			val wB = w_broad.value
            val z_array = cwf.next()
			var f_obj : Double = 0.0
            var index = 0
			while(blocks.hasNext)
			{
				var p = blocks.next()
                var enyz = z_array(index)
				var yz = -log(enyz)
				if(yz >= 0)
				{
					f_obj += math.log(1+enyz)
				}
				else
				{
					f_obj += -yz + math.log(1+exp(yz))
				}
                index += 1
			}
			Seq(f_obj).iterator
		}).reduce(_ + _) * C + (0.5 * w_broad.value.dot(w_broad.value))
		f
	}

	override def gradient(dataPoints : RDD[DataPoint], w_broad : Broadcast[DoubleMatrix], param : Parameter, cwfRDD: RDD[Array[Double]]) : DoubleMatrix =
	{
		val C = param.C
		val g = dataPoints.zipPartitions(cwfRDD) ((blocks, cwf) => {
			val wB = w_broad.value
			val n = wB.length
			var grad = Array.fill(n)(0.0)
            val z_array = cwf.next()
            var index = 0
			while(blocks.hasNext)
			{
				var p = blocks.next()
				var i = 0
				var z = (1.0 / (1.0 + z_array(index)) - 1.0) * p.y
				while(i < p.index.length)
				{
					grad(p.index(i)) += z * p.value(i)
					i += 1
				}
                index += 1
			}
			Seq(new DoubleMatrix(grad)).iterator
		}).slaveReduce(_.addi(_), param.numSlaves).muli(C).addi(w_broad.value)
		g
	}

	override def hessianVector(dataPoints : RDD[DataPoint], w_broad : Broadcast[DoubleMatrix], param : Parameter, s : DoubleMatrix, cwfRDD: RDD[Array[Double]]) : DoubleMatrix =
	{
		val C = param.C
		val sc = dataPoints.sparkContext
		val s_broad = sc.broadcast(s)
		val Hs = dataPoints.zipPartitions(cwfRDD) ((blocks, cwf) => {
			val wB = w_broad.value
			val sB = s_broad.value
			val n = wB.length
			var blockHs = Array.fill(n)(0.0)
            var z_array = cwf.next()
            var index = 0 
			while(blocks.hasNext)
			{
				var p = blocks.next()
				var wa = 0.0 
				var i = 0
				while(i < p.index.length)
				{
					wa += p.value(i) * sB.get(p.index(i))
					i += 1
				}   
				val sigma = 1.0 / (1.0 + z_array(index))
				val D = sigma * (1.0 - sigma)
				wa = D * wa
				i = 0
				while(i < p.index.length)
				{
					blockHs(p.index(i)) += wa * p.value(i)
					i += 1
				}
                index += 1
			}
			Seq(new DoubleMatrix(blockHs)).iterator
		}).slaveReduce(_.addi(_), param.numSlaves).muli(C).addi(s)
		s_broad.unpersist()
		Hs
	}
}

/**
 * TronL2SVM implements TronFunction for L2-regularized L2-loss SVM.
 */
class TronL2SVM extends TronFunction
{
 
    override def gencwfRDD(dataPoints: RDD[DataPoint], w_broad : Broadcast[DoubleMatrix], param : Parameter): RDD[Array[Double]] = {
 		val C = param.C
		dataPoints.mapPartitions((blocks) => {
			val wB = w_broad.value
			var f_obj : Double = 0.0
            var z_array = Array[Double]()
			while(blocks.hasNext)
			{
				var p = blocks.next()
				var z = 0.0
				var i = 0
				while(i < p.index.length)
				{
					z += p.value(i) * wB.get(p.index(i))
					i += 1
				}
                val pyz = p.y * z
                z_array = z_array :+ pyz
			}
			Seq(z_array).iterator
        })
    }
 
	override def functionValue(dataPoints: RDD[DataPoint], w_broad : Broadcast[DoubleMatrix], param : Parameter, cwfRDD: RDD[Array[Double]]) : Double =
	{
		val C = param.C
		val f = dataPoints.zipPartitions(cwfRDD) ((blocks, cwf) => {
			val wB = w_broad.value
            val z_array = cwf.next()
			var f_obj : Double = 0.0
            var index = 0
			while(blocks.hasNext)
			{
				var p = blocks.next()
				val d = 1 - z_array(index)
				if (d > 0)
				{
					f_obj += d * d;
				}
                index += 1
			}
			Seq(f_obj).iterator
		}).reduce(_ + _) * C + (0.5 * w_broad.value.dot(w_broad.value))
		f
	}

	override def gradient(dataPoints : RDD[DataPoint], w_broad : Broadcast[DoubleMatrix], param : Parameter, cwfRDD: RDD[Array[Double]]) : DoubleMatrix =
	{
		val C = param.C
		val g = dataPoints.zipPartitions(cwfRDD) ((blocks, cwf) => {
			val wB = w_broad.value
			val n = wB.length
			var grad = Array.fill(n)(0.0)
            val z_array = cwf.next()
            var index = 0
			while(blocks.hasNext)
			{
				var p = blocks.next()
				var i = 0
				var z = z_array(index)
				if(z < 1)
				{
					z = p.y * (z-1)
					i = 0
					while(i < p.index.length)
					{
						grad(p.index(i)) += z * p.value(i)
						i += 1
					}
				}
                index += 1
			}
			Seq(new DoubleMatrix(grad)).iterator
		}).slaveReduce(_.addi(_), param.numSlaves).muli(2*C).addi(w_broad.value)
		g
	}

	override def hessianVector(dataPoints : RDD[DataPoint], w_broad : Broadcast[DoubleMatrix], param : Parameter, s : DoubleMatrix, cwfRDD: RDD[Array[Double]]) : DoubleMatrix =
	{
		val C = param.C
		val sc = dataPoints.sparkContext
		val s_broad = sc.broadcast(s)
		val Hs = dataPoints.zipPartitions(cwfRDD) ((blocks, cwf) => {
			val wB = w_broad.value
			val sB = s_broad.value
			val n = wB.length
			var blockHs = Array.fill(n)(0.0)
            val z_array = cwf.next()
            var index = 0
			while(blocks.hasNext)
			{
				var p = blocks.next()
				var i = 0
				if(z_array(index) < 1)
				{
					var wa = 0.0
					i = 0
					while(i < p.index.length)
					{
						wa += p.value(i) * sB.get(p.index(i))
						i += 1
					} 
					i = 0
					while(i < p.index.length)
					{
						blockHs(p.index(i)) += wa * p.value(i)
						i += 1
					}
				}
                index += 1
			}
			Seq(new DoubleMatrix(blockHs)).iterator
		}).slaveReduce(_.addi(_), param.numSlaves).muli(2*C).addi(s)
		s_broad.unpersist()
		Hs
	}
}
/**
 * Tron is used to solve an optimization problem by a trust region Newton method.
 *
 * @param function a class which defines necessary methods used for the optimization problem
 */

class Tron(val function : TronFunction)
{

	private def trcg(dataPoints : RDD[DataPoint], param : Parameter, delta : Double,  w_broad : Broadcast[DoubleMatrix], g : DoubleMatrix, cwfRDD: RDD[Array[Double]]) : (Int, DoubleMatrix, DoubleMatrix) = 
	{
		val n = w_broad.value.length
		var s = DoubleMatrix.zeros(n)
		var r = g.neg()
		var d = r.dup()
		var (rTr, rnewTrnew, beta, cgtol) = (0.0, 0.0, 0.0, 0.0)
		cgtol = 0.1 * g.norm2()

		var cgIter = 0
		rTr = r.dot(r)
		breakable {
		while(true)
		{   
			if(r.norm2() <= cgtol)
			{
				break()
			}
			cgIter += 1

			/* hessianVector */
			var Hd = function.hessianVector(dataPoints, w_broad, param, d, cwfRDD)
			var alpha = rTr / d.dot(Hd)
			s.addi(d.mul(alpha))
			if(s.norm2() > delta)
			{
				println("cg reaches trust region boundary")
				alpha = -alpha
				s.addi(d.mul(alpha))
				val std = s.dot(d)
				val sts = s.dot(s)
				val dtd = d.dot(d)
				val dsq = delta*delta
				val rad = math.sqrt(std*std + dtd*(dsq-sts))
				if (std >= 0)
				{
					alpha = (dsq - sts)/(std + rad)
				} 
				else
				{
					alpha = (rad - std)/dtd
				}
				s.addi(d.mul(alpha))
				alpha = -alpha
				r.addi(Hd.mul(alpha))
				break()
			}
			alpha = -alpha;
			r.addi(Hd.mul(alpha))
			rnewTrnew = r.dot(r)
			beta = rnewTrnew/rTr
			d.muli(beta)
			d.addi(r)
			rTr = rnewTrnew
		}
		}
		(cgIter, s, r)
	}

	/**
	 * Train a model by a trust region Newton method.
	 *
	 * @param prob a problem which contains data and necessary information
	 * @param param user-specified parameters
	 */
	def tron(prob : Problem, param : Parameter, eps : Double) : DoubleMatrix =
	{
		val ITERATIONS = 1000
		val (eta0, eta1, eta2) = (1e-4, 0.25, 0.75)
		val (sigma1, sigma2, sigma3) = (0.25, 0.5, 4.0)
		var (delta, snorm) = (0.0, 0.0)
		var (alpha, f, fnew, prered, actred, gs) = (0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
		var (search, iter) = (1, 1)
		var w = DoubleMatrix.zeros(prob.n)
		var w_new : DoubleMatrix = null
		var dataPoints = prob.dataPoints

		val sc = dataPoints.sparkContext
		var w_broad = sc.broadcast(w)

        var cwf_rdd = function.gencwfRDD(dataPoints, w_broad, param)
        cwf_rdd.cache()
 
	   	/* Function Value*/
		f = function.functionValue(dataPoints, w_broad, param, cwf_rdd)

		/* gradient */
		var g = function.gradient(dataPoints, w_broad, param, cwf_rdd)
		delta = g.norm2()
		var gnorm1 = delta
		var gnorm = gnorm1
		if(gnorm <= eps * gnorm1)
		{
			search = 0
		}

		breakable {
		while(iter <= ITERATIONS && search == 1)
		{
			var (cgIter, s, r) = trcg(dataPoints, param, delta, w_broad, g, cwf_rdd)
			w_new = w.add(s)
			gs = g.dot(s)
			prered = -0.5*(gs - s.dot(r))
			w_broad.unpersist()
			w_broad = sc.broadcast(w_new)

            /* Generate new values */
            cwf_rdd = function.gencwfRDD(dataPoints, w_broad, param)
            cwf_rdd.cache()

			/* Function value */
			fnew = function.functionValue(dataPoints, w_broad, param, cwf_rdd)

			/* Compute the actual reduction. */
			actred = f - fnew

			/* On the first iteration, adjust the initial step bound. */
			snorm = s.norm2()
			if (iter == 1)
			{
				delta = math.min(delta, snorm)
			}

			/* Compute prediction alpha*snorm of the step. */
			if(fnew - f - gs <= 0)
			{
				alpha = sigma3
			}
			else
			{
				alpha = math.max(sigma1, -0.5*(gs/(fnew - f - gs)))
			}

			/* Update the trust region bound according to the ratio of actual to predicted reduction. */
			if (actred < eta0*prered)
			{
				delta = math.min(math.max(alpha, sigma1)*snorm, sigma2*delta);
			}
			else if(actred < eta1*prered)
			{
				delta = math.max(sigma1*delta, math.min(alpha*snorm, sigma2*delta))
			} 
			else if (actred < eta2*prered)
			{
				delta = math.max(sigma1*delta, math.min(alpha*snorm, sigma3*delta))
			}
			else
			{
				delta = math.max(delta, math.min(alpha*snorm, sigma3*delta))
			}
	
			println("iter %2d act %5.3e pre %5.3e delta %5.3e f %5.3e |g| %5.3e CG %3d".format(iter, actred, prered, delta, f, gnorm, cgIter))
			
			if (actred > eta0*prered)
			{
				iter += 1
				w = w_new
				f = fnew
				/* gradient */
				g = function.gradient(dataPoints, w_broad, param, cwf_rdd)

				gnorm = g.norm2()
				if (gnorm <= eps*gnorm1)
				{
					break()
				}
			}
			if (f < -1.0e+32)
			{
				println("WARNING: f < -1.0e+32")
				break()
			}
			if (math.abs(actred) <= 0 && prered <= 0)
			{
				println("WARNING: actred and prered <= 0")
				break()
			}
			if (math.abs(actred) <= 1.0e-12*math.abs(f) && math.abs(prered) <= 1.0e-12*math.abs(f))
			{
				println("WARNING: actred and prered too small")
				break()
			}
		}
		}
		w
	}
}
