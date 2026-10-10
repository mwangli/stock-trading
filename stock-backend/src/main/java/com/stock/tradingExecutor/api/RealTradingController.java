// AI_GENERATE_START ---
package com.stock.tradingExecutor.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.tradingExecutor.domain.dto.RealTradingStatusDto;
import com.stock.tradingExecutor.domain.dto.TradingCandidateListResponseDto;
import com.stock.tradingExecutor.service.RealTradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

/**
 * 真实交易控制器。
 * 提供自动交易状态、候选查询和运维检查入口。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@RestController
@RequestMapping("/api/real-trading")
@RequiredArgsConstructor
public class RealTradingController {

    private final RealTradingService realTradingService;

    /**
     * 查询真实交易运行状态。
     *
     * @return 真实交易状态
     */
    @GetMapping("/status")
    public ResponseDTO<RealTradingStatusDto> getStatus() {
        log.info("查询真实交易运行状态");
        return execute(realTradingService::getStatus);
    }

    /**
     * 查询当日真实模型交易候选。
     *
     * @return 候选列表
     */
    @GetMapping("/candidates")
    public ResponseDTO<TradingCandidateListResponseDto> getCandidates() {
        log.info("查询当日真实模型交易候选");
        return execute(realTradingService::getCandidates);
    }

    private <T> ResponseDTO<T> execute(Supplier<T> supplier) {
        try {
            return ResponseDTO.success(supplier.get());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.warn("真实交易请求未执行: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("真实交易请求执行异常", exception);
            return ResponseDTO.error("真实交易执行失败，请检查服务日志");
        }
    }
}
// AI_GENERATE_END ---
