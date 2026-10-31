// AI_GENERATE_START -
package com.stock.console.model;

import com.stock.console.facts.TradingFacts;
import com.stock.console.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final TradingFacts facts;

    public ModelController(TradingFacts facts) {
        this.facts = facts;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("versions", facts.models(20));
        result.put("predictions", facts.predictions(20));
        result.put("evaluations", facts.evaluations(20));
        result.put("trainingRequests", facts.trainingRequests(20));
        return ApiResponse.ok(result);
    }

    @GetMapping("/predictions")
    public ApiResponse<List<Map<String, Object>>> predictions(
            @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(facts.predictions(limit));
    }

    @GetMapping("/evaluations")
    public ApiResponse<List<Map<String, Object>>> evaluations(
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(facts.evaluations(limit));
    }
}
// AI_GENERATE_END -
