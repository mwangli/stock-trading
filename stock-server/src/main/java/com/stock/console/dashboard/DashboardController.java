// AI_GENERATE_START --
package com.stock.console.dashboard;

import com.stock.console.facts.TradingFacts;
import com.stock.console.market.MarketRepository;
import com.stock.console.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final TradingFacts facts;
    private final MarketRepository market;

    public DashboardController(TradingFacts facts, MarketRepository market) {
        this.facts = facts;
        this.market = market;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tradingSource", facts.status());
        result.put("marketSource", market.status());
        result.put("account", facts.account().stream().findFirst().orElse(Map.of()));
        result.put("positions", facts.positions(50));
        result.put("performance", facts.performance(90));
        result.put("recentOrders", facts.orders(10));
        result.put("recentTasks", facts.taskRuns(10));
        return ApiResponse.ok(result);
    }
}
// AI_GENERATE_END --
