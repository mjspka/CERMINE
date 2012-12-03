package pl.edu.icm.cermine.tools.classification.svm;

import java.io.BufferedReader;
import java.util.EnumSet;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import libsvm.*;
import org.apache.commons.collections.iterators.ArrayIterator;
import pl.edu.icm.cermine.structure.model.*;
import pl.edu.icm.cermine.tools.classification.features.FeatureVector;
import pl.edu.icm.cermine.tools.classification.features.FeatureVectorBuilder;
import pl.edu.icm.cermine.tools.classification.general.FeatureLimits;
import pl.edu.icm.cermine.tools.classification.general.FeatureVectorScaler;
import pl.edu.icm.cermine.tools.classification.general.LinearScaling;
import pl.edu.icm.cermine.tools.classification.hmm.training.TrainingElement;

/**
 * @author Pawel Szostek (p.szostek@
 *
 * @param <S> classified object's class
 * @param <T> context class
 * @param <E> target enumeration for labels
 */
public abstract class SVMClassifier<S, T, E extends Enum<E>> {
	final protected static svm_parameter defaultParameter = new svm_parameter();		
	static {
		// default values
		defaultParameter.svm_type = svm_parameter.C_SVC;
		defaultParameter.C = 8;
		defaultParameter.kernel_type = svm_parameter.POLY;
		defaultParameter.degree = 3;
		defaultParameter.gamma = 1.0/8.0; // 1/k
		defaultParameter.coef0 = 0.5;
		defaultParameter.nu = 0.5;
		defaultParameter.cache_size = 100;
		defaultParameter.eps = 1e-3;
		defaultParameter.p = 0.1;
		defaultParameter.shrinking = 1;
		defaultParameter.probability = 0;
		defaultParameter.nr_weight = 0;
		defaultParameter.weight_label = new int[0];
		defaultParameter.weight = new double[0];
	}
	protected FeatureVectorBuilder<S, T> featureVectorBuilder;
	protected FeatureVectorScaler scaler;
	protected String[] featuresNames;
		
	protected svm_parameter param;
	protected svm_problem problem;
	protected svm_model model;
	
	protected Class<E> enumClassObj;
	
	public SVMClassifier(FeatureVectorBuilder<S, T> featureVectorBuilder, Class<E> enumClassObj) 
	{
		this.featureVectorBuilder = featureVectorBuilder;
		this.enumClassObj = enumClassObj;
		Integer dimensions = featureVectorBuilder.size();
		
		Double scaledLowerBound = 0.0;
		Double scaledUpperBound = 1.0;
		scaler = new FeatureVectorScaler(dimensions, scaledLowerBound, scaledUpperBound);
		scaler.setStrategy(new LinearScaling());
		
		featuresNames = (String[])featureVectorBuilder.getFeatureNames().toArray(new String[0]);
		
		param = getDefaultParam();
	}
	
	protected static svm_parameter clone(svm_parameter param) {
		svm_parameter ret = new svm_parameter();
		// default values
		ret.svm_type = param.svm_type;
		ret.C = param.C;
		ret.kernel_type = param.kernel_type;
		ret.degree = param.degree;
		ret.gamma = param.gamma; // 1/k
		ret.coef0 = param.coef0;
		ret.nu = param.nu;
		ret.cache_size = param.cache_size;
		ret.eps = param.eps;
		ret.p = param.p;
		ret.shrinking = param.shrinking;
		ret.probability = param.probability;
		ret.nr_weight = param.nr_weight;
		ret.weight_label = param.weight_label;
		ret.weight = param.weight;
		return ret;
	}
	
	public static svm_parameter getDefaultParam() {
		return clone(defaultParameter);
	}
	
	public void buildClassifier(List<TrainingElement<E>> trainingElements) 
	{
		assert trainingElements.size() > 0;
		scaler.calculateFeatureLimits(trainingElements);
		problem = buildDatasetForTraining(trainingElements);
		model = libsvm.svm.svm_train(problem, param);
	}
	
	public BxZoneLabel predictLabel(S object, T context) {
		svm_node[] instance = buildDatasetForClassification(object, context);
		double predictedVal = svm.svm_predict(model, instance);
		return BxZoneLabel.values()[(int)predictedVal];
	}

	public E classify(S object, T context) {
		svm_node[] instance = buildDatasetForClassification(object, context);
		Integer predictedVal = ((Double)svm.svm_predict(model, instance)).intValue();
		return enumClassObj.getEnumConstants()[predictedVal];
	}

	protected svm_problem buildDatasetForTraining(List<TrainingElement<E>> trainingElements)
	{
		svm_problem problem = new svm_problem();
		problem.l = trainingElements.size();
		problem.x = new svm_node[problem.l][trainingElements.get(0).getObservation().size()];
		problem.y = new double[trainingElements.size()];
		
		Integer elemIdx = 0;
		for(TrainingElement<E> trainingElem : trainingElements) {
			FeatureVector scaledFV = scaler.scaleFeatureVector(trainingElem.getObservation());
			Integer featureIdx = 0;
			for(Double val: scaledFV.getFeatures()) {
				svm_node cur = new svm_node();
				cur.index = featureIdx;
				cur.value = val;
				problem.x[elemIdx][featureIdx] = cur;
				++featureIdx;
			}
			problem.y[elemIdx] = trainingElem.getLabel().ordinal();
			System.out.println("training " + trainingElem.getLabel().ordinal() + " (" + trainingElem.getLabel() + ")");
			++elemIdx;
		}
		return problem;
	}
	
	protected svm_node[] buildDatasetForClassification(S object, T context)
	{
		svm_node[] ret = new svm_node[featureVectorBuilder.getFeatureNames().size()];
		FeatureVector scaledFV = scaler.scaleFeatureVector(featureVectorBuilder.getFeatureVector(object, context));
		
		Integer featureIdx = 0;
		for(Double val: scaledFV.getFeatures()) {
			svm_node cur = new svm_node();
			cur.index = featureIdx;
			cur.value = val;
			ret[featureIdx] = cur;
			++featureIdx;
		}
		return ret;
	}

	public double[] getWeights() {
		double[][] coef = model.sv_coef;

		double[][] prob = new double[model.SV.length][featureVectorBuilder.size()];
		for (int i = 0; i < model.SV.length; i++) {
			for (int j = 0; j < model.SV[i].length; j++) {
				prob[i][j] = model.SV[i][j].value;
			}
		}
		double w_list[][][] = new double[model.nr_class][model.nr_class - 1][model.SV[0].length];

		for (int i = 0; i < model.SV[0].length; ++i) {
			for (int j = 0; j < model.nr_class - 1; ++j) {
				int index = 0;
				int end = 0;
				double acc;
				for (int k = 0; k < model.nr_class; ++k) {
					acc = 0.0;
					index += (k == 0) ? 0 : model.nSV[k - 1];
					end = index + model.nSV[k];
					for (int m = index; m < end; ++m) {
						acc += coef[j][m] * prob[m][i];
					}
					w_list[k][j][i] = acc;
				}
			}
		}

		double[] weights = new double[model.SV[0].length];
		for (int i = 0; i < model.nr_class - 1; ++i) {
			for (int j = i + 1, k = i; j < model.nr_class; ++j, ++k) {
				for (int m = 0; m < model.SV[0].length; ++m) {
					weights[m] = (w_list[i][k][m] + w_list[j][i][m]);

				}
			}
		}
		return weights;
	}

	public void loadModel(String modelFilePath, String rangeFilePath) throws IOException
	{
		InputStreamReader modelISR = new InputStreamReader(Thread.currentThread().getClass()
				.getResourceAsStream(modelFilePath));
		BufferedReader modelFile = new BufferedReader(modelISR);
		
		InputStreamReader rangeISR = new InputStreamReader(Thread.currentThread().getClass()
				.getResourceAsStream(rangeFilePath));
		BufferedReader rangeFile = new BufferedReader(rangeISR);
		loadModel(modelFile, rangeFile);
	}
	
	public void loadModel(BufferedReader modelFile, BufferedReader rangeFile) throws IOException
	{
		Double feature_min, feature_max;
		if(rangeFile.read() == 'x') {
			rangeFile.readLine();		// pass the '\n' after 'x'
            String line = rangeFile.readLine();
            if (line == null) {
                line = "";
            }
			StringTokenizer st = new StringTokenizer(line);
			Double scaledLowerBound = Double.parseDouble(st.nextToken());
			Double scaledUpperBound = Double.parseDouble(st.nextToken());
			if(scaledLowerBound != 0 || scaledUpperBound != 1) {
				throw new RuntimeException("Feature lower bound and upper bound must"
						+ "be set in range file to resepctively 0 and 1");
			}
			String restore_line = null;
			List<FeatureLimits> limits = new ArrayList<FeatureLimits>();
			while((restore_line = rangeFile.readLine())!=null)
			{
				StringTokenizer st2 = new StringTokenizer(restore_line);
				st2.nextToken(); //discard feature index
				feature_min = Double.parseDouble(st2.nextToken());
				feature_max = Double.parseDouble(st2.nextToken());
				FeatureLimits newLimit = new FeatureLimits(feature_min, feature_max);
				limits.add(newLimit);
			}
			if(limits.size() != featureVectorBuilder.size()) {
				throw new IllegalArgumentException("Supplied .range file has "
						+ "wrong number of features (got " + limits.size() 
						+ ", expected " + featureVectorBuilder.size() + " )");
			}
			scaler = new FeatureVectorScaler(limits.size(), scaledLowerBound, scaledUpperBound);
			scaler.setStrategy(new LinearScaling());
			scaler.setFeatureLimits(limits);
		} else {
			throw new RuntimeException("y scaling not supported");
		}
		rangeFile.close();
		model = svm.svm_load_model(modelFile);
	}

	public void saveModel(String modelPath) throws IOException
	{
		BufferedWriter fp_save = null;
		try {
			Formatter formatter = new Formatter(new StringBuilder());
			fp_save = new BufferedWriter(new FileWriter(modelPath + ".range"));

			Double lower = 0.0;
			Double upper = 1.0;

			formatter.format("x\n");
			formatter.format("%.16g %.16g\n", lower, upper);
			for(Integer i=0 ; i<featureVectorBuilder.size() ; ++i) {
				formatter.format("%d %.16g %.16g\n", i, scaler.getLimits()[i].getMin(), scaler.getLimits()[i].getMax());
			}
			
			fp_save.write(formatter.toString());
		} finally {
			if(fp_save != null) {
				fp_save.close();
			}
		}
		
		svm.svm_save_model(modelPath, model);
	}
	
	public void printWeigths(FeatureVectorBuilder<BxZone, BxPage> vectorBuilder)
	{
		Set<String> fnames = featureVectorBuilder.getFeatureNames();
		Iterator<String> namesIt = fnames.iterator();
		Iterator<Double> valueIt = (Iterator<Double>)new ArrayIterator(getWeights());

		assert fnames.size() == getWeights().length;
		
        while(namesIt.hasNext() && valueIt.hasNext()) {
        	String name = namesIt.next();
        	Double val = valueIt.next();
        	System.out.println(name + " " + val);
        }
	}

	public void setParameter(svm_parameter param) {
		this.param = param;
	}

}