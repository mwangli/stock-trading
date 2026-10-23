// AI_GENERATE_START-----
package com.stock.trading.controller;

import com.stock.base.api.ResponseDTO;
import com.stock.trading.dto.BrokerOrderPageResponseDto;
import com.stock.trading.dto.BrokerTransactionQueryRequest;
import com.stock.trading.dto.TradeRecordPageResponseDto;
import com.stock.trading.service.BrokerTransactionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 已入库历史订单和 FIFO 汇总交易记录查询接口。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Slf4j
@RestController
@RequestMapping("/api/tradeRecords")
@RequiredArgsConstructor
public class BrokerTransactionController {

    private final BrokerTransactionQueryService brokerTransactionQueryService;

    /**
     * 分页查询当前账户已成交历史订单。
     *
     * @param request 查询条件
     * @return 历史订单分页结果
     */
    @GetMapping("/orders")
    public ResponseDTO<BrokerOrderPageResponseDto> queryOrders(
            @ModelAttribute BrokerTransactionQueryRequest request) {
        log.info("分页查询已成交历史订单: keyword={}, direction={}, startDate={}, endDate={}, current={}, pageSize={}",
                request.getKeyword(), request.getDirection(), request.getStartDate(), request.getEndDate(),
                request.getCurrent(), request.getPageSize());
        try {
            return ResponseDTO.success(brokerTransactionQueryService.queryOrders(request));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.warn("历史订单查询未完成: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }

    /**
     * 分页查询由历史订单按 FIFO 汇总得到的交易记录。
     *
     * @param request 查询条件
     * @return 交易记录分页结果
     */
    @GetMapping("/trades")
    public ResponseDTO<TradeRecordPageResponseDto> queryTrades(
            @ModelAttribute BrokerTransactionQueryRequest request) {
        log.info("分页查询 FIFO 交易记录: keyword={}, startDate={}, endDate={}, current={}, pageSize={}",
                request.getKeyword(), request.getStartDate(), request.getEndDate(),
                request.getCurrent(), request.getPageSize());
        try {
            return ResponseDTO.success(brokerTransactionQueryService.queryTrades(request));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.warn("交易记录查询未完成: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }
}
// AI_GENERATE_END-----
