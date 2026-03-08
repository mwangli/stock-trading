package com.stock.tradingExecutor.api;

import com.stock.tradingExecutor.domain.vo.OrderResult;
import com.stock.tradingExecutor.execution.BrokerAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单信息控制器
 * 对应前端 /api/orderInfo/* 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/orderInfo")
@RequiredArgsConstructor
public class OrderController {

    private final BrokerAdapter brokerAdapter;

    /**
     * 获取订单列表 (目前仅支持当日订单)
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listOrders(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {

        // 获取当日订单
        List<OrderResult> allOrders = brokerAdapter.getTodayOrders();

        // 简单内存分页
        int total = allOrders.size();
        int fromIndex = (current - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);

        List<OrderResult> pageData = List.of();
        if (fromIndex < total) {
            pageData = allOrders.subList(fromIndex, toIndex);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data", pageData);
        response.put("total", total);
        response.put("success", true);
        response.put("current", current);
        response.put("pageSize", pageSize);

        return ResponseEntity.ok(response);
    }
}
