// AI_GENERATE_START -
package com.stock.console.dashboard;

import com.stock.console.facts.TradingFacts;
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

    public DashboardController(TradingFacts facts) {
        this.facts = facts;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", facts.contractStatus());
        result.put("account", facts.account().stream().findFirst().orElse(Map.of()));
        result.put("positions", facts.positions(50));
        result.put("performance", facts.performance(90));
        result.put("recentOrders", facts.orders(10));
        result.put("recentTasks", facts.taskRuns(10));
        return ApiResponse.ok(result);
    }
}
// AI_GENERATE_END -
