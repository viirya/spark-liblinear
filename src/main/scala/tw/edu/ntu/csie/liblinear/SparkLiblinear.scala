package tw.edu.ntu.csie.liblinear

import scala.util.control.Breaks._
import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import tw.edu.ntu.csie.liblinear.SolverType._
import scala.math.{max,min}
import org.jblas.DoubleMatrix

/**
 * The interface for training liblinear on Spark.
 */
object SparkLiblinear
{

	private def train_one(prob : Problem, param : Parameter, posLabel : Double) : DoubleMatrix =
	{
		var w : DoubleMatrix = null
		/* Construct binary labels.*/
		val binaryProb = prob.genBinaryProb(posLabel)

		val pos = binaryProb.dataPoints.map(point => point.y).filter(_ > 0).count()
		val neg = binaryProb.l - pos
		val primalSolverTol = param.eps * max(min(pos,neg), 1)/binaryProb.l;

		param.solverType match {
			case L2_LR => {
				var solver = new Tron(new TronLR())
				w = solver.tron(binaryProb, param, primalSolverTol)
			}
			case L2_L2LOSS_SVC => {
				var solver = new Tron(new TronL2SVM())
				w = solver.tron(binaryProb, param, primalSolverTol)
			}
			case _ => {
				System.err.println("ERROR: unknown solver_type")
				return null
			}
		}
		binaryProb.dataPoints.unpersist()
		w
	}
  
	private def train(prob : Problem, param : Parameter) : Model = 
	{
		val labels = prob.dataPoints.mapPartitions(blocks => {
			blocks.map(p => p.y)
		}).distinct()

		val labelSet : Array[Double] = labels.collect()
		var model : Model= new Model(param, labelSet).setBias(prob.bias)

		if(labelSet.size == 2)
		{
			model.w(0) = train_one(prob, param, model.label(0))
		}
		else
		{
			for(i <- 0 until labelSet.size)
			{
				model.w(i) = train_one(prob, param, model.label(i))
			}
		}
		model
	}
	
	/**
	 * Show the detailed usage of train.
	 */
	def printUsage() = 
	{
		System.err.println("Usage: model = train(trainingData, 'options')")
		printOptions()
	}

	private def printOptions() = 
	{
		System.err.println(
			"options:\n"
			+ "-s type : set type of solver (default 0)\n"
			+ "\t0 -- L2-regularized logistic regression (primal)\n"
			+ "\t2 -- L2-regularized L2-loss support vector classification (primal)\n"
			+ "-c cost : set the parameter C (default 1)\n"
			+ "-e epsilon : set tolerance of termination criterion\n"
			+ "\t-s 0 and 2\n"
			+ "\t\t|f'(w)|_2 <= eps*min(pos,neg)/l*|f'(w0)|_2,\n"
			+ "\t\twhere f is the primal function and pos/neg are # of\n"
			+ "\t\tpositive/negative data (default 0.01)\n"
			+ "-B bias : if bias >= 0, instance x becomes [x; bias]; if < 0, no bias term added (default -1)\n"
			+ "-N #salves : if #slaves > 0, enable the coalesce function to reduce the communication cost; if <= 0, do not use the coalesce function (default -1)\n")
  	}

 	def train(data : RDD[DataPoint]) : Model =
	{
  		train(data, "")
  	}
	
	/**
	 * Train a model given an input RDD of DataPoint.
	 *
	 * @param data an RDD of DataPoint
	 * @param options Liblinear-like options
	 * @return a model
	 */
  	def train(data : RDD[DataPoint], options : String) : Model =
	{
		var param = new Parameter()
		val prob = new Problem()
		var model : Model = null
    	
		/* Parse options */
		var argv = options.trim.split("[ \t]+")
		breakable {
	 	var i = 0
		while(i < argv.size)
		{
			if(argv(i).size == 0)
			{
				break
			}
			if(argv(i)(0) != '-' || i+1 >= argv.size)
			{
				System.err.println("ERROR: wrong usage")
				printUsage()
				return model
			}
			i += 1
			argv(i-1)(1) match {
				case 's' => param.solverType = SolverType.parse(argv(i).toInt)
				case 'e' => param.eps = argv(i).toDouble
				case 'c' => param.C = argv(i).toDouble
				case 'B' => prob.bias = argv(i).toDouble
				case 'N' => param.numSlaves = argv(i).toInt
				case _ => {
					System.err.println("ERROR: unknown option")
					printUsage()
					return model
				}
			}
			i += 1
	 	}
		}
		if(param.numSlaves > data.partitions.size)
		{
			param.numSlaves = -1
		}
		prob.setData(data.cache())
		train(prob, param)
  	}
}
