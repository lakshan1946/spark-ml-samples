package com.lohika.morning.ml.spark.driver.service.lyrics7;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.ml.classification.LogisticRegression;
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator;
import org.apache.spark.ml.feature.HashingTF;
import org.apache.spark.ml.feature.IDF;
import org.apache.spark.ml.feature.IndexToString;
import org.apache.spark.ml.feature.RegexTokenizer;
import org.apache.spark.ml.feature.StopWordsRemover;
import org.apache.spark.ml.feature.StringIndexer;
import org.apache.spark.ml.feature.StringIndexerModel;
import org.apache.spark.ml.linalg.DenseVector;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Lyrics7ClassifierService {

    private static final List<String> TARGET_GENRES = Arrays.asList(
            "pop", "country", "blues", "jazz", "reggae", "rock", "hip hop", "folk");

    @Autowired
    private SparkSession sparkSession;

    @Value("${lyrics7.dataset.csv.file.path}")
    private String datasetCsvPath;

    @Value("${lyrics7.model.directory.path}")
    private String modelDirectoryPath;

    @Value("${lyrics7.split.seed}")
    private Long splitSeed;

    public Map<String, Object> train() {
        Dataset<Row> prepared = loadAndPrepareDataset().cache();
        long totalRows = prepared.count();

        if (totalRows == 0L) {
                        throw new RuntimeException("No valid rows were found in the merged dataset for the 8 target genres.");
        }

        Dataset<Row>[] split = prepared.randomSplit(new double[] {0.8d, 0.2d}, splitSeed);
        Dataset<Row> train = split[0].cache();
        Dataset<Row> test = split[1].cache();

        Pipeline pipeline = buildPipeline(train);
        PipelineModel model = pipeline.fit(train);

        Dataset<Row> predictions = model.transform(test).cache();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalRows", totalRows);
        report.put("trainRows", train.count());
        report.put("testRows", test.count());
        report.put("classDistribution", getClassDistribution(prepared));
        report.put("metrics", evaluate(predictions));
        report.put("modelDirectory", modelDirectoryPath);

                try {
                        model.write().overwrite().save(modelDirectoryPath);
                } catch (IOException e) {
                        throw new RuntimeException("Unable to save 7-class lyrics model to " + modelDirectoryPath, e);
                }

        return report;
    }

    public Lyrics7Prediction predict(String unknownLyrics) {
        PipelineModel model = PipelineModel.load(modelDirectoryPath);

        String text = unknownLyrics == null ? "" : unknownLyrics.trim();
        if (text.isEmpty()) {
            throw new RuntimeException("Lyrics text is empty.");
        }

        Dataset<Row> input = sparkSession.createDataset(Collections.singletonList(text), Encoders.STRING())
                .toDF("lyrics")
                .withColumn("genre", functions.lit("pop"));

        Row predictionRow = model.transform(input)
                .select("predictedGenre", "probability")
                .first();

        String predictedGenre = predictionRow.getAs("predictedGenre");
        DenseVector probability = predictionRow.getAs("probability");

        StringIndexerModel indexerModel = (StringIndexerModel) model.stages()[0];
        String[] labels = indexerModel.labels();

        List<GenreScore> probabilities = new ArrayList<>();
        double max = 0d;

        for (int i = 0; i < labels.length && i < probability.size(); i++) {
            double value = probability.apply(i);
            probabilities.add(new GenreScore(labels[i], value));
            if (value > max) {
                max = value;
            }
        }

        return new Lyrics7Prediction(predictedGenre, max, probabilities);
    }

    private Dataset<Row> loadAndPrepareDataset() {
        Object[] genresArray = TARGET_GENRES.toArray(new Object[TARGET_GENRES.size()]);

        Dataset<Row> dataset = sparkSession.read()
                .option("header", "true")
                .option("encoding", "UTF-8")
                .option("inferSchema", "true")
                .csv(datasetCsvPath)
                .select(
                        functions.col("genre"),
                        functions.col("lyrics"));

        return dataset
                .withColumn("genre", functions.lower(functions.trim(functions.col("genre"))))
                .withColumn("genre", functions.regexp_replace(functions.col("genre"), "[-_/]", " "))
                .withColumn("genre", functions.regexp_replace(functions.col("genre"), "\\\\s+", " "))
                .withColumn("lyrics", functions.trim(functions.col("lyrics")))
                .filter(functions.col("genre").isin(genresArray))
                .filter(functions.col("lyrics").isNotNull())
                .filter(functions.length(functions.col("lyrics")).gt(0));
    }

        private Pipeline buildPipeline(Dataset<Row> train) {
                StringIndexerModel labelIndexer = new StringIndexer()
                .setInputCol("genre")
                .setOutputCol("label")
                                .setHandleInvalid("skip")
                                .fit(train);

        RegexTokenizer tokenizer = new RegexTokenizer()
                .setInputCol("lyrics")
                .setOutputCol("tokens")
                .setPattern("\\W+")
                .setMinTokenLength(2);

        StopWordsRemover stopWordsRemover = new StopWordsRemover()
                .setInputCol("tokens")
                .setOutputCol("filteredTokens");

        HashingTF hashingTF = new HashingTF()
                .setInputCol("filteredTokens")
                .setOutputCol("rawFeatures")
                .setNumFeatures(1 << 18);

        IDF idf = new IDF()
                .setInputCol("rawFeatures")
                .setOutputCol("features");

        LogisticRegression logisticRegression = new LogisticRegression()
                .setLabelCol("label")
                .setFeaturesCol("features")
                .setFamily("multinomial")
                .setMaxIter(100)
                .setRegParam(0.01d);

        IndexToString labelConverter = new IndexToString()
                .setInputCol("prediction")
                .setOutputCol("predictedGenre")
                .setLabels(labelIndexer.labels());

        return new Pipeline().setStages(new PipelineStage[] {
                labelIndexer,
                tokenizer,
                stopWordsRemover,
                hashingTF,
                idf,
                logisticRegression,
                labelConverter
        });
    }

    private Map<String, Object> evaluate(Dataset<Row> predictions) {
        MulticlassClassificationEvaluator evaluator = new MulticlassClassificationEvaluator()
                .setLabelCol("label")
                .setPredictionCol("prediction");

        Map<String, Object> metrics = new LinkedHashMap<>();

        metrics.put("accuracy", evaluator.setMetricName("accuracy").evaluate(predictions));
        metrics.put("f1", evaluator.setMetricName("f1").evaluate(predictions));
        metrics.put("weightedPrecision", evaluator.setMetricName("weightedPrecision").evaluate(predictions));
        metrics.put("weightedRecall", evaluator.setMetricName("weightedRecall").evaluate(predictions));

        return metrics;
    }

    private Map<String, Long> getClassDistribution(Dataset<Row> dataset) {
        List<Row> rows = dataset.groupBy("genre").count().sort(functions.col("genre")).collectAsList();

        Map<String, Long> distribution = new LinkedHashMap<>();
        for (Row row : rows) {
            distribution.put(row.getAs("genre"), row.getAs("count"));
        }

        return distribution;
    }
}
