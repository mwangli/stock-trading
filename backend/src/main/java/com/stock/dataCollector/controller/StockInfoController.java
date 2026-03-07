package com.stock.dataCollector.controller;

import com.stock.dataCollector.entity.StockInfo;
import com.stock.dataCollector.service.StockDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 股票信息控制器
 * 对应前端 /api/stockInfo/* 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/stockInfo")
@RequiredArgsConstructor
public class StockInfoController {

    private final StockDataService stockDataService;

    /**
     * 获取股票列表
     * 前端参数: current, pageSize, name, code
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listStockInfo(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code) {
        
        // 获取所有股票 (因为Service层目前只有findAll，暂时在内存中分页)
        List<StockInfo> allStocks = stockDataService.findAllStocks();
        
        // 过滤
        List<StockInfo> filtered = allStocks.stream()
            .filter(s -> (name == null || (s.getName() != null && s.getName().contains(name))) &&
                         (code == null || (s.getCode() != null && s.getCode().contains(code))))
            .collect(Collectors.toList());
            
        int total = filtered.size();
        int fromIndex = (current - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        
        List<StockInfo> pageData = List.of();
        if (fromIndex < total) {
            pageData = filtered.subList(fromIndex, toIndex);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data", pageData);
        response.put("total", total);
        response.put("success", true);
        response.put("current", current);
        response.put("pageSize", pageSize);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取历史价格
     */
    @GetMapping("/listHistoryPrices")
    public ResponseEntity<Map<String, Object>> listHistoryPrices(@RequestParam String code) {
        // 调用Service获取历史数据
        var prices = stockDataService.getHistoryPrices(code);
        
        Map<String, Object> response = new HashMap<>();
        response.put("data", prices);
        response.put("success", true);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 加入自选 (模拟)
     */
    @GetMapping("/selectStockInfo")
    public ResponseEntity<Map<String, Object>> selectStockInfo(@RequestParam String code) {
        log.info("加入自选: {}", code);
        // TODO: 实现真正的自选逻辑 (可能需要用户系统)
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    /**
     * 取消自选 (模拟)
     */
    @GetMapping("/cancelStockInfo")
    public ResponseEntity<Map<String, Object>> cancelStockInfo(@RequestParam String code) {
        log.info("取消自选: {}", code);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取涨幅榜 (复用list接口，前端可能自己排序，或者这里做排序)
     */
    @GetMapping("/listIncreaseRate")
    public ResponseEntity<Map<String, Object>> listIncreaseRate(@RequestParam(required = false) String code) {
        // 简单返回所有股票，或者按涨幅排序
        List<StockInfo> allStocks = stockDataService.findAllStocks();
        
        // 按涨幅排序 (ChangePercent)
        List<StockInfo> sorted = allStocks.stream()
            .sorted((a, b) -> {
                BigDecimal p1 = a.getChangePercent() != null ? a.getChangePercent() : BigDecimal.valueOf(-100.0);
                BigDecimal p2 = b.getChangePercent() != null ? b.getChangePercent() : BigDecimal.valueOf(-100.0);
                return p2.compareTo(p1); // 降序
            })
            .limit(20) // 只返回前20
            .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("data", sorted);
        response.put("success", true);
        
        return ResponseEntity.ok(response);
    }
}
