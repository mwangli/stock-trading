package com.stock.modelService.controller;

import com.stock.modelService.dto.ApiResponse;
import com.stock.modelService.service.LstmTrainerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/lstm")
public class LstmTrainingController {
    private final LstmTrainerService lstmTrainerService;

    @PostMapping("/train")
    public ApiResponse<LstmTrainerService.TrainingResult> trainLstmModel(
            @RequestParam String stockCodes,
            @RequestParam(required = false, defaultValue = "365") int days,
            @RequestParam(required = false) Integer epochs,
            @RequestParam(required = false) Integer batchSize,
            @RequestParam(required = false) Double learningRate
    ) {
        LstmTrainerService.TrainingResult result = lstmTrainerService.trainModel(stockCodes, days, epochs, batchSize, learningRate);
        if (result.isSuccess()) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error(result.getMessage());
        }
    }
}
