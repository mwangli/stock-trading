// AI_GENERATE_START----
package com.stock.trading.controller;

import com.stock.base.api.ResponseDTO;
import com.stock.trading.dto.OrderListResponseDto;
import com.stock.trading.execution.BrokerAdapter;
import com.stock.trading.vo.OrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当日订单查询控制器。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@RestController
@RequestMapping("/api/orderInfo")
@RequiredArgsConstructor
public class OrderController {

    private final BrokerAdapter brokerAdapter;

    /**
     * 分页查询券商当日订单。
     *
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页记录数
     * @return 当日订单分页数据
     */
    @GetMapping("/list")
    public ResponseDTO<OrderListResponseDto> listOrders(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.info("[Order] 查询当日订单 | current={}, pageSize={}", current, pageSize);
        int safeCurrent = Math.max(1, current);
        int safePageSize = Math.max(1, pageSize);
        List<OrderResult> allOrders = brokerAdapter.getTodayOrders();
        int total = allOrders.size();
        int fromIndex = (safeCurrent - 1) * safePageSize;
        int toIndex = Math.min(fromIndex + safePageSize, total);
        List<OrderResult> items = fromIndex < total ? allOrders.subList(fromIndex, toIndex) : List.of();
        return ResponseDTO.success(OrderListResponseDto.builder()
                .items(items)
                .total(total)
                .current(safeCurrent)
                .pageSize(safePageSize)
                .build());
    }
}
// AI_GENERATE_END----
