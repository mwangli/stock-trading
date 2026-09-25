// AI_GENERATE_START ---
package com.stock.tradingExecutor.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.tradingExecutor.domain.dto.BrokerAccountDto;
import com.stock.tradingExecutor.domain.dto.BrokerFillListResponseDto;
import com.stock.tradingExecutor.domain.dto.BrokerHistoryQueryRequest;
import com.stock.tradingExecutor.domain.dto.BrokerOrderListResponseDto;
import com.stock.tradingExecutor.domain.dto.BrokerPositionListResponseDto;
import com.stock.tradingExecutor.domain.dto.BrokerReadStatusDto;
import com.stock.tradingExecutor.service.BrokerReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

/**
 * 券商只读查询接口。
 * 本控制器不提供下单、撤单或模式切换能力。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@RestController
@RequestMapping("/api/broker-query")
@RequiredArgsConstructor
public class BrokerReadController {

    private final BrokerReadService brokerReadService;

    /**
     * 查询券商连接和交易门禁状态。
     *
     * @return 券商只读网关状态
     */
    @GetMapping("/status")
    public ResponseDTO<BrokerReadStatusDto> getStatus() {
        log.info("查询券商只读网关状态");
        return executeQuery(brokerReadService::getStatus);
    }

    /**
     * 查询账户资金。
     *
     * @return 账户资金快照
     */
    @GetMapping("/account")
    public ResponseDTO<BrokerAccountDto> getAccount() {
        log.info("查询券商账户资金");
        return executeQuery(brokerReadService::getAccount);
    }

    /**
     * 查询当前持仓。
     *
     * @return 持仓列表
     */
    @GetMapping("/positions")
    public ResponseDTO<BrokerPositionListResponseDto> getPositions() {
        log.info("查询券商当前持仓");
        return executeQuery(brokerReadService::getPositions);
    }

    /**
     * 查询当日全部委托。
     *
     * @return 当日委托列表
     */
    @GetMapping("/today-orders")
    public ResponseDTO<BrokerOrderListResponseDto> getTodayOrders() {
        log.info("查询券商当日全部委托");
        return executeQuery(brokerReadService::getTodayOrders);
    }

    /**
     * 查询当日成交。
     *
     * @return 当日成交列表
     */
    @GetMapping("/today-fills")
    public ResponseDTO<BrokerFillListResponseDto> getTodayFills() {
        log.info("查询券商当日成交");
        return executeQuery(brokerReadService::getTodayFills);
    }

    /**
     * 查询最多 31 个自然日的历史委托。
     *
     * @param request 日期范围
     * @return 历史委托列表
     */
    @PostMapping("/history-orders")
    public ResponseDTO<BrokerOrderListResponseDto> getHistoryOrders(
            @RequestBody BrokerHistoryQueryRequest request) {
        log.info("查询券商历史委托: startDate={}, endDate={}",
                request != null ? request.getStartDate() : null,
                request != null ? request.getEndDate() : null);
        return executeQuery(() -> brokerReadService.getHistoryOrders(request));
    }

    /**
     * 查询最多 31 个自然日的历史成交。
     *
     * @param request 日期范围
     * @return 历史成交列表
     */
    @PostMapping("/history-fills")
    public ResponseDTO<BrokerFillListResponseDto> getHistoryFills(
            @RequestBody BrokerHistoryQueryRequest request) {
        log.info("查询券商历史成交: startDate={}, endDate={}",
                request != null ? request.getStartDate() : null,
                request != null ? request.getEndDate() : null);
        return executeQuery(() -> brokerReadService.getHistoryFills(request));
    }

    private <T> ResponseDTO<T> executeQuery(Supplier<T> supplier) {
        try {
            return ResponseDTO.success(supplier.get());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.warn("券商只读查询未完成: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("券商只读查询异常", exception);
            return ResponseDTO.error("券商只读查询失败，请检查服务日志");
        }
    }
}
// AI_GENERATE_END ---
