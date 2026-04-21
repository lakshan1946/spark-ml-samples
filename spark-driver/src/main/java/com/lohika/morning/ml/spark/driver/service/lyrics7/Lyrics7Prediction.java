package com.lohika.morning.ml.spark.driver.service.lyrics7;

import java.util.List;

public class Lyrics7Prediction {

    private String predictedGenre;
    private Double confidence;
    private List<GenreScore> probabilities;

    public Lyrics7Prediction(String predictedGenre, Double confidence, List<GenreScore> probabilities) {
        this.predictedGenre = predictedGenre;
        this.confidence = confidence;
        this.probabilities = probabilities;
    }

    public String getPredictedGenre() {
        return predictedGenre;
    }

    public Double getConfidence() {
        return confidence;
    }

    public List<GenreScore> getProbabilities() {
        return probabilities;
    }
}
