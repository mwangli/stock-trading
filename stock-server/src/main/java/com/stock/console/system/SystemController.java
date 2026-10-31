// AI_GENERATE_START --
package com.stock.console.system;

import com.stock.console.facts.TradingFacts;
import com.stock.console.market.MarketRepository;
import com.stock.console.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final TradingFacts facts;
    private final MarketRepository market;

    public SystemController(TradingFacts facts, MarketRepository market) {
        this.facts = facts;
        this.market = market;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "time", Instant.now().toString(),
                "trading", facts.status(),
                "market", market.status()));
    }
}
// AI_GENERATE_END --
