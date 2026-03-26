package com.stock.tradingExecutor.api;

import com.stock.tradingExecutor.domain.dto.TradingRecordDto;
import com.stock.tradingExecutor.domain.dto.TradingRecordListResponseDto;
import com.stock.tradingExecutor.domain.dto.TradingAnalysisResponseDto;
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
@RequestMapping("/api/trading-record")
@RequiredArgsConstructor
public class TradingRecordController {

    /**
     * 获取交易记录列表
     */
    @GetMapping("/list")
    public ResponseEntity<TradingRecordListResponseDto> listTradingRecords(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {

        // Mock数据
        List<TradingRecordDto> records = new ArrayList<>();

        TradingRecordDto record1 = TradingRecordDto.builder()
                .id("1")
                .code("600519")
                .name("贵州茅台")
                .direction("BUY")
                .price(new BigDecimal("1800.00"))
                .quantity(100)
                .amount(new BigDecimal("180000.00"))
                .status("FILLED")
                .time(LocalDateTime.now().minusHours(2))
                .build();
        records.add(record1);

        TradingRecordDto record2 = TradingRecordDto.builder()
                .id("2")
                .code("000858")
                .name("五粮液")
                .direction("SELL")
                .price(new BigDecimal("150.00"))
                .quantity(200)
                .amount(new BigDecimal("30000.00"))
                .status("FILLED")
                .time(LocalDateTime.now().minusHours(1))
                .build();
        records.add(record2);

        TradingRecordListResponseDto response = TradingRecordListResponseDto.builder()
                .data(records)
                .total(records.size())
                .success(true)
                .current(current)
                .pageSize(pageSize)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 获取交易分析数据
     */
    @GetMapping("/analysis")
    public ResponseEntity<TradingAnalysisResponseDto> analysis() {
        // Mock分析数据
        List<Map<String, Object>> data = new ArrayList<>();
        // ...

        TradingAnalysisResponseDto response = TradingAnalysisResponseDto.builder()
                .data(data)
                .success(true)
                .build();
        return ResponseEntity.ok(response);
    }
}
