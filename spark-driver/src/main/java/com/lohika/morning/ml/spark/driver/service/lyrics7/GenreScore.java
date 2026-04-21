package com.lohika.morning.ml.spark.driver.service.lyrics7;

public class GenreScore {

    private String genre;
    private Double value;

    public GenreScore(String genre, Double value) {
        this.genre = genre;
        this.value = value;
    }

    public String getGenre() {
        return genre;
    }

    public Double getValue() {
        return value;
    }
}
