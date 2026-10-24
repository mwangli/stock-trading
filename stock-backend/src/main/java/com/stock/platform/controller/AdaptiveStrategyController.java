// AI_GENERATE_START---
package com.stock.platform.controller;

import com.stock.platform.dto.ResponseDTO;
import com.stock.decision.dto.AdaptiveStrategyOverviewResponse;
import com.stock.platform.service.AdaptiveStrategyOverviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自适应策略观察与治理接口。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Slf4j
@RestController
@RequestMapping("/api/adaptiveStrategy")
@RequiredArgsConstructor
public class AdaptiveStrategyController {

    private final AdaptiveStrategyOverviewService overviewService;

    /**
     * 查询自适应策略聚合运行事实。
     *
     * @return 自适应策略页面聚合响应
     */
    @GetMapping("/overview")
    public ResponseDTO<AdaptiveStrategyOverviewResponse> getOverview() {
        log.info("查询自适应策略聚合运行事实");
        return ResponseDTO.success(overviewService.getOverview());
    }
}
// AI_GENERATE_END---
