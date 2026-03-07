package com.stock.tradingExecutor.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 交易记录控制器
 * 对应前端 /api/tradingRecord/* 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/tradingRecord")
@RequiredArgsConstructor
public class TradingRecordController {

    /**
     * 获取交易记录列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listTradingRecords(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        // Mock数据
        List<Map<String, Object>> records = new ArrayList<>();
        
        Map<String, Object> record1 = new HashMap<>();
        record1.put("id", "1");
        record1.put("code", "600519");
        record1.put("name", "贵州茅台");
        record1.put("direction", "BUY");
        record1.put("price", new BigDecimal("1800.00"));
        record1.put("quantity", 100);
        record1.put("amount", new BigDecimal("180000.00"));
        record1.put("status", "FILLED");
        record1.put("time", LocalDateTime.now().minusHours(2));
        records.add(record1);

        Map<String, Object> record2 = new HashMap<>();
        record2.put("id", "2");
        record2.put("code", "000858");
        record2.put("name", "五粮液");
        record2.put("direction", "SELL");
        record2.put("price", new BigDecimal("150.00"));
        record2.put("quantity", 200);
        record2.put("amount", new BigDecimal("30000.00"));
        record2.put("status", "FILLED");
        record2.put("time", LocalDateTime.now().minusHours(1));
        records.add(record2);

        Map<String, Object> response = new HashMap<>();
        response.put("data", records);
        response.put("total", records.size());
        response.put("success", true);
        response.put("current", current);
        response.put("pageSize", pageSize);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取交易分析数据
     */
    @GetMapping("/analysis")
    public ResponseEntity<Map<String, Object>> analysis() {
        // Mock分析数据
        List<Map<String, Object>> data = new ArrayList<>();
        // ...
        
        Map<String, Object> response = new HashMap<>();
        response.put("data", data);
        response.put("success", true);
        return ResponseEntity.ok(response);
    }
}
