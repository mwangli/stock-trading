// AI_GENERATE_START--------
package com.stock.trading.controller;

import com.stock.base.web.ResponseDTO;
import com.stock.trading.dto.PerformanceOverviewDto;
import com.stock.trading.service.PerformanceReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收益报表查询接口。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Slf4j
@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceReportController {

    private final PerformanceReportService performanceReportService;

    /**
     * 查询当前账户收益报表总览。
     *
     * @return 收益统计和周期收益数据
     */
    @GetMapping("/overview")
    public ResponseDTO<PerformanceOverviewDto> getOverview() {
        log.info("查询账户收益报表总览");
        try {
            return ResponseDTO.success(performanceReportService.getOverview());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.warn("收益报表查询未完成: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("收益报表查询异常", exception);
            return ResponseDTO.error("收益报表查询失败，请检查服务日志");
        }
    }
}
// AI_GENERATE_END--------
