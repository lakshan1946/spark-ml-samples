package com.lohika.morning.ml.api.service;

import com.lohika.morning.ml.spark.driver.service.lyrics7.Lyrics7ClassifierService;
import com.lohika.morning.ml.spark.driver.service.lyrics7.Lyrics7Prediction;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Lyrics7Service {

    @Autowired
    private Lyrics7ClassifierService classifierService;

    public Map<String, Object> train() {
        return classifierService.train();
    }

    public Lyrics7Prediction predict(String lyrics) {
        return classifierService.predict(lyrics);
    }
}
