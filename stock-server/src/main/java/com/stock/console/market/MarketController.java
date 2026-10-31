// AI_GENERATE_START --
package com.stock.console.market;

import com.stock.console.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketRepository market;

    public MarketController(MarketRepository market) {
        this.market = market;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(market.status());
    }

    @GetMapping("/instruments")
    public ApiResponse<List<Map<String, Object>>> instruments(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(market.instruments(keyword, limit));
    }

    @GetMapping("/bars")
    public ApiResponse<List<Map<String, Object>>> bars(
            @RequestParam String code,
            @RequestParam(defaultValue = "day") String interval,
            @RequestParam(defaultValue = "240") int limit) {
        if ("day".equalsIgnoreCase(interval)) {
            return ApiResponse.ok(market.dailyBars(code, limit));
        }
        if ("5m".equalsIgnoreCase(interval)) {
            return ApiResponse.ok(market.minuteBars(code, limit));
        }
        throw new IllegalArgumentException("K 线周期只支持 day 或 5m");
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> statistics() {
        return ApiResponse.ok(market.statistics());
    }
}
// AI_GENERATE_END --
