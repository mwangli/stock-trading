// AI_GENERATE_START ---
package com.stock.console.dashboard;

import com.stock.console.facts.TradingFacts;
import com.stock.console.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return ApiResponse.ok(facts.dashboardOverview());
    }
}
// AI_GENERATE_END ---
