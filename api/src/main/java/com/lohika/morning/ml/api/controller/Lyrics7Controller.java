package com.lohika.morning.ml.api.controller;

import com.lohika.morning.ml.api.service.Lyrics7Service;
import com.lohika.morning.ml.spark.driver.service.lyrics7.Lyrics7Prediction;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lyrics7")
public class Lyrics7Controller {

    @Autowired
    private Lyrics7Service lyrics7Service;

    @RequestMapping(value = "/train", method = RequestMethod.GET)
    ResponseEntity<Map<String, Object>> trainLyricsModel() {
        return new ResponseEntity<>(lyrics7Service.train(), HttpStatus.OK);
    }

    @RequestMapping(value = "/predict", method = RequestMethod.POST)
    ResponseEntity<Lyrics7Prediction> predictGenre(@RequestBody String unknownLyrics) {
        return new ResponseEntity<>(lyrics7Service.predict(unknownLyrics), HttpStatus.OK);
    }
}
