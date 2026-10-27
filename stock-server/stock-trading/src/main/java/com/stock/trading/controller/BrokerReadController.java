// AI_GENERATE_START-----------
package com.stock.trading.controller;

import com.stock.base.web.ResponseDTO;
import com.stock.trading.dto.BrokerAccountDto;
import com.stock.trading.dto.BrokerFillListResponseDto;
import com.stock.trading.dto.BrokerHistoryQueryRequest;
import com.stock.trading.dto.BrokerOrderListResponseDto;
import com.stock.trading.dto.BrokerReadStatusDto;
import com.stock.trading.service.BrokerReadService;
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
@RequestMapping("/api/brokerQuery")
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
        log.info("查询数据库中的最新券商账户资金快照");
        return executeQuery(brokerReadService::getAccount);
    }

    /**
     * 查询当日全部委托。
     *
     * @return 当日委托列表
     */
    @GetMapping("/todayOrders")
    public ResponseDTO<BrokerOrderListResponseDto> getTodayOrders() {
        log.info("查询券商当日全部委托");
        return executeQuery(brokerReadService::getTodayOrders);
    }

    /**
     * 查询当日成交。
     *
     * @return 当日成交列表
     */
    @GetMapping("/todayFills")
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
    @PostMapping("/historyOrders")
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
    @PostMapping("/historyFills")
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
// AI_GENERATE_END-----------
