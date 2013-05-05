/*
 * avenir: Predictive analytic based on Hadoop Map Reduce
 * Author: Pranab Ghosh
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.avenir.discriminant;

import java.io.IOException;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.chombo.mr.NumericalAttrStats;
import org.chombo.util.Tuple;
import org.chombo.util.Utility;

/**
 * Fisher univariate discriminant. Feature is numeric. Classification is binary
 * @author pranab
 *
 */
public class FisherDiscriminant  extends Configured implements Tool {

	@Override
	public int run(String[] args) throws Exception {
        Job job = new Job(getConf());
        String jobName = "Univariate Fisher linear discriminant";
        job.setJobName(jobName);
        
        job.setJarByClass(FisherDiscriminant.class);

        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        Utility.setConfiguration(job.getConfiguration(), "avenir");
        job.setMapperClass(NumericalAttrStats.StatsMapper.class);
        job.setReducerClass(FisherDiscriminant.FisherReducer.class);
        job.setCombinerClass(NumericalAttrStats.StatsCombiner.class);
        
        job.setMapOutputKeyClass(Tuple.class);
        job.setMapOutputValueClass(Tuple.class);

        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);

        job.setNumReduceTasks(job.getConfiguration().getInt("num.reducer", 1));

        int status =  job.waitForCompletion(true) ? 0 : 1;
        return status;
	}

	/**
	 * @author pranab
	 *
	 */
	public static class FisherReducer  extends NumericalAttrStats.StatsReducer  {
		private static final String uncondAttrVal = "0";
		private ConditionedFeatureStat[] consStats = new ConditionedFeatureStat[2];
		private int  condStatIndex = 0;

	   	/* (non-Javadoc)
	   	 * @see org.apache.hadoop.mapreduce.Reducer#cleanup(org.apache.hadoop.mapreduce.Reducer.Context)
	   	 */
	   	protected void cleanup(Context context)  throws IOException, InterruptedException {
	   		double pooledVariance = (consStats[0].getVariance() * consStats[0].getCount() + 
	   				consStats[1].getVariance() * consStats[1].getCount()) / (consStats[0].getCount() + consStats[1].getCount());
	   		double logOddsPrior = Math.log((double)consStats[0].getCount() / consStats[1].getCount());
	   		double meanDiff = consStats[0].getMean() - consStats[1].getMean();
	   		double discrimValue = (consStats[0].getMean() + consStats[1].getMean()) / 2;
	   		discrimValue -= logOddsPrior * pooledVariance / meanDiff;
	   		outVal.set("" + logOddsPrior + fieldDelim + pooledVariance + fieldDelim + discrimValue);
			context.write(NullWritable.get(), outVal);
	   	}	
	   	
    	protected void reduce(Tuple key, Iterable<Tuple> values, Context context)
            	throws IOException, InterruptedException {
    		processReduce(values);
    		String condAttrVal = key.getString(1);
    		if (!uncondAttrVal.equals(condAttrVal)) {
    			consStats[condStatIndex++] = new ConditionedFeatureStat(condAttrVal, totalCount, mean, variance);
    		}
    		emitOutput( key,  context);
    	}	
	}
	
	/**
	 * @author pranab
	 *
	 */
	public static class ConditionedFeatureStat {
		private String condAttrVal;
		private int count;
		private double mean;
		private double variance;
		
		public ConditionedFeatureStat(String condAttrVal, int count, double mean, double variance) {
			super();
			this.condAttrVal = condAttrVal;
			this.count = count;
			this.mean = mean;
			this.variance = variance;
		}

		public String getCondAttrVal() {
			return condAttrVal;
		}

		public int getCount() {
			return count;
		}

		public double getMean() {
			return mean;
		}

		public double getVariance() {
			return variance;
		}
		
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new FisherDiscriminant(), args);
        System.exit(exitCode);
	}

}
