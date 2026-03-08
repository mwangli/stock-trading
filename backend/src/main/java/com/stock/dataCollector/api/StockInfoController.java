package com.stock.dataCollector.api;

import com.stock.dataCollector.domain.vo.MarketStatsDto;
import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.service.StockDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
     * 获取股票列表 (使用数据库分页)
     * 前端参数: current, pageSize, keywords
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listStockInfo(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keywords) {
        log.info("[StockInfo] 请求到达 GET /api/stockInfo/list current={} pageSize={} keywords={}", current, pageSize, keywords);

        // 使用数据库分页查询 (Page 0-based, so subtract 1)
        int pageNumber = Math.max(0, current - 1);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "id"));

        // 构建动态查询条件，使用 keywords 同时匹配 name 或 code
        Page<StockInfo> pageResult = stockDataService.findStocksWithKeywords(keywords, pageRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("data", pageResult.getContent());
        response.put("total", pageResult.getTotalElements());
        response.put("success", true);
        response.put("current", pageResult.getNumber() + 1);
        response.put("pageSize", pageResult.getSize());
        log.info("[StockInfo] /list 即将返回 total={}", pageResult.getTotalElements());
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
     * 获取涨幅榜TOP10
     * 返回格式: [{code, name, changePercent}, ...]
     */
    @GetMapping("/listIncreaseRate")
    public ResponseEntity<Map<String, Object>> listIncreaseRate() {
        log.info("[StockInfo] 请求到达 GET /api/stockInfo/listIncreaseRate");
        List<StockInfo> top10Stocks = stockDataService.findTop10ByIncreaseRate();

        List<Map<String, Object>> top10 = top10Stocks.stream()
            .map(stock -> {
                Map<String, Object> item = new HashMap<>();
                item.put("code", stock.getCode());
                item.put("name", stock.getName());
                item.put("changePercent", stock.getChangePercent());
                return item;
            })
            .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("data", top10);
        response.put("success", true);
        log.info("[StockInfo] /listIncreaseRate 即将返回 size={}", top10.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 获取市场统计信息
     * 从stock_info表中聚合提取市场基本数据。
     * 发生异常时返回兜底数据，确保前端始终能拿到响应，避免请求挂起。
     */
    @GetMapping("/marketStats")
    public ResponseEntity<Map<String, Object>> getMarketStats() {
        log.info("[StockInfo] 请求到达 GET /api/stockInfo/marketStats");
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        try {
            MarketStatsDto stats = stockDataService.getMarketStats();
            response.put("data", stats);
            log.info("[StockInfo] marketStats 即将返回 success=true");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("[StockInfo] 获取市场统计失败，返回兜底数据: {}", e.getMessage());
            MarketStatsDto fallback = MarketStatsDto.builder()
                    .marketStatus("休市")
                    .changePercent(BigDecimal.ZERO)
                    .upCount(0)
                    .downCount(0)
                    .flatCount(0)
                    .totalAmount(BigDecimal.ZERO)
                    .totalVolume(BigDecimal.ZERO)
                    .totalCount(0)
                    .avgTurnoverRate(BigDecimal.ZERO)
                    .build();
            response.put("data", fallback);
            return ResponseEntity.ok(response);
        }
    }
}
