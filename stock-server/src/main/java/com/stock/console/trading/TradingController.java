// AI_GENERATE_START -
package com.stock.console.trading;

import com.stock.console.facts.TradingFacts;
import com.stock.console.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trading")
public class TradingController {

    private final TradingFacts facts;

    public TradingController(TradingFacts facts) {
        this.facts = facts;
    }

    @GetMapping("/orders")
    public ApiResponse<List<Map<String, Object>>> orders(@RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(facts.orders(limit));
    }

    @GetMapping("/fills")
    public ApiResponse<List<Map<String, Object>>> fills(@RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(facts.fills(limit));
    }

    @GetMapping("/positions")
    public ApiResponse<List<Map<String, Object>>> positions(@RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(facts.positions(limit));
    }
}
// AI_GENERATE_END -
