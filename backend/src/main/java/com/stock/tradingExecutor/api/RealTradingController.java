// AI_GENERATE_START -
package com.stock.tradingExecutor.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.tradingExecutor.domain.dto.RealBuyRequestDto;
import com.stock.tradingExecutor.domain.dto.RealSellRequestDto;
import com.stock.tradingExecutor.domain.dto.RealTradingStatusDto;
import com.stock.tradingExecutor.domain.dto.TradeExecutionBatchResponseDto;
import com.stock.tradingExecutor.domain.dto.TradeExecutionResponseDto;
import com.stock.tradingExecutor.domain.dto.TradingCandidateListResponseDto;
import com.stock.tradingExecutor.service.RealTradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

/**
 * 真实交易控制器。
 * 提供候选查询、人工确认和显式触发无人值守检查的最小接口。
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

    /**
     * 人工确认买入候选股票。
     *
     * @param request 买入请求
     * @return 委托结果
     */
    @PostMapping("/manual-buy")
    public ResponseDTO<TradeExecutionResponseDto> executeManualBuy(@RequestBody RealBuyRequestDto request) {
        log.info("人工确认真实买入: stockCode={}, amount={}",
                request != null ? request.getStockCode() : null,
                request != null ? request.getAmount() : null);
        return execute(() -> realTradingService.executeManualBuy(request));
    }

    /**
     * 人工卖出真实持仓。
     *
     * @param request 卖出请求
     * @return 委托结果
     */
    @PostMapping("/manual-sell")
    public ResponseDTO<TradeExecutionResponseDto> executeManualSell(@RequestBody RealSellRequestDto request) {
        log.info("人工确认真实卖出: stockCode={}, quantity={}",
                request != null ? request.getStockCode() : null,
                request != null ? request.getQuantity() : null);
        return execute(() -> realTradingService.executeManualSell(request));
    }

    /**
     * 显式触发一次无人值守候选买入检查。
     *
     * @return 批量委托结果
     */
    @PostMapping("/auto-buy-now")
    public ResponseDTO<TradeExecutionBatchResponseDto> executeAutomaticBuys() {
        log.info("手动触发无人值守候选买入检查");
        return execute(realTradingService::executeAutomaticBuys);
    }

    /**
     * 显式触发一次无人值守 T+1 退出检查。
     *
     * @return 批量委托结果
     */
    @PostMapping("/auto-exit-now")
    public ResponseDTO<TradeExecutionBatchResponseDto> executeAutomaticExits() {
        log.info("手动触发无人值守 T+1 退出检查");
        return execute(realTradingService::executeAutomaticExits);
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
// AI_GENERATE_END -
