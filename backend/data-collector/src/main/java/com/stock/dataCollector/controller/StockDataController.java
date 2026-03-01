package com.stock.dataCollector.controller;

import com.stock.dataCollector.entity.mysql.StockInfoMySql;
import com.stock.dataCollector.service.StockDataService;
import com.stock.dataCollector.service.StockInfoMySqlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 股票数据采集控制器
 * 提供手动触发接口和数据验证接口
 */
@Slf4j
@RestController
@RequestMapping("/api/stock-data")
@RequiredArgsConstructor
public class StockDataController {

    private final StockDataService stockDataService;
    private final StockInfoMySqlService stockInfoMySqlService;

    /**
     * 手动触发股票列表同步到MySQL
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncStockList() {
        log.info("手动触发股票列表同步");
        
        try {
            StockDataService.SyncResult result = stockDataService.syncStockListToMySql();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "同步完成");
            response.put("data", result);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("手动同步失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "同步失败: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取MySQL中股票数据统计
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        long totalCount = stockInfoMySqlService.count();
        List<String> codes = stockInfoMySqlService.findAllCodes();
        
        // 统计各市场数量
        Map<String, Long> marketCount = new HashMap<>();
        List<StockInfoMySql> allStocks = stockInfoMySqlService.findAll();
        for (StockInfoMySql stock : allStocks) {
            String market = stock.getMarket() != null ? stock.getMarket() : "未知";
            marketCount.merge(market, 1L, Long::sum);
        }
        
        // 统计字段完整性
        int withPrice = 0;
        int withName = 0;
        int withMarket = 0;
        int withVolume = 0;
        
        for (StockInfoMySql stock : allStocks) {
            if (stock.getPrice() != null) withPrice++;
            if (stock.getName() != null && !stock.getName().isEmpty()) withName++;
            if (stock.getMarket() != null && !stock.getMarket().isEmpty()) withMarket++;
            if (stock.getVolume() != null) withVolume++;
        }
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalCount", totalCount);
        statistics.put("marketDistribution", marketCount);
        statistics.put("fieldCompleteness", Map.of(
            "name", withName,
            "price", withPrice,
            "market", withMarket,
            "volume", withVolume
        ));
        statistics.put("completenessRate", Map.of(
            "name", String.format("%.2f%%", totalCount > 0 ? (withName * 100.0 / totalCount) : 0),
            "price", String.format("%.2f%%", totalCount > 0 ? (withPrice * 100.0 / totalCount) : 0),
            "market", String.format("%.2f%%", totalCount > 0 ? (withMarket * 100.0 / totalCount) : 0),
            "volume", String.format("%.2f%%", totalCount > 0 ? (withVolume * 100.0 / totalCount) : 0)
        ));
        
        return ResponseEntity.ok(statistics);
    }

    /**
     * 分页查询股票列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getStockList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        List<StockInfoMySql> allStocks = stockInfoMySqlService.findAll();
        
        int total = allStocks.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        
        List<StockInfoMySql> pageData = fromIndex < total 
            ? allStocks.subList(fromIndex, toIndex) 
            : List.of();
        
        Map<String, Object> response = new HashMap<>();
        response.put("total", total);
        response.put("page", page);
        response.put("size", size);
        response.put("data", pageData);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 根据股票代码查询
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<?> getStockByCode(@PathVariable String code) {
        return stockInfoMySqlService.findByCode(code)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 验证数据完整性
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateData() {
        List<StockInfoMySql> allStocks = stockInfoMySqlService.findAll();
        
        int missingName = 0;
        int missingPrice = 0;
        int missingMarket = 0;
        int missingVolume = 0;
        int missingCode = 0;
        
        for (StockInfoMySql stock : allStocks) {
            if (stock.getCode() == null || stock.getCode().isEmpty()) missingCode++;
            if (stock.getName() == null || stock.getName().isEmpty()) missingName++;
            if (stock.getPrice() == null) missingPrice++;
            if (stock.getMarket() == null || stock.getMarket().isEmpty()) missingMarket++;
            if (stock.getVolume() == null) missingVolume++;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalRecords", allStocks.size());
        result.put("validation", Map.of(
            "missingCode", missingCode,
            "missingName", missingName,
            "missingPrice", missingPrice,
            "missingMarket", missingMarket,
            "missingVolume", missingVolume
        ));
        result.put("isValid", missingCode == 0);
        result.put("dataQuality", String.format("%.2f%%", 
            allStocks.size() > 0 
                ? ((allStocks.size() - missingName) * 100.0 / allStocks.size()) 
                : 0));
        
        return ResponseEntity.ok(result);
    }
}