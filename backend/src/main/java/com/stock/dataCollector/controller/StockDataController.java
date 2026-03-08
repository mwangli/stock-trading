package com.stock.dataCollector.controller;

import com.stock.dataCollector.entity.StockInfo;
import com.stock.dataCollector.entity.StockPrice;
import com.stock.dataCollector.entity.StockPriceWeekly;
import com.stock.dataCollector.entity.StockPriceMonthly;
import com.stock.dataCollector.repository.PriceRepository;
import com.stock.dataCollector.service.StockDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
    private final PriceRepository priceRepository;

    /**
     * 手动触发股票列表同步
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncStockList() {
        log.info("手动触发股票列表同步");
        
        try {
            StockDataService.SyncResult result = stockDataService.syncStockList();
            
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
     * 获取股票数据统计
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        long totalCount = stockDataService.count();
        
        Map<String, Long> marketCount = new HashMap<>();
        List<StockInfo> allStocks = stockDataService.findAllStocks();
        
        for (StockInfo stock : allStocks) {
            String market = stock.getMarket() != null ? stock.getMarket() : "未知";
            marketCount.merge(market, 1L, Long::sum);
        }
        
        int withPrice = 0, withName = 0, withMarket = 0, withChangePercent = 0, withTotalMarketValue = 0, withTurnoverRate = 0, withVolumeRatio = 0, withIndustryCode = 0;
        
        for (StockInfo stock : allStocks) {
            if (stock.getPrice() != null) withPrice++;
            if (stock.getName() != null && !stock.getName().isEmpty()) withName++;
            if (stock.getMarket() != null && !stock.getMarket().isEmpty()) withMarket++;
            if (stock.getChangePercent() != null) withChangePercent++;
            if (stock.getTotalMarketValue() != null) withTotalMarketValue++;
            if (stock.getTurnoverRate() != null) withTurnoverRate++;
            if (stock.getVolumeRatio() != null) withVolumeRatio++;
            if (stock.getIndustryCode() != null) withIndustryCode++;
        }
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalCount", totalCount);
        statistics.put("marketDistribution", marketCount);
statistics.put("fieldCompleteness", Map.of(
"name", withName,
"price", withPrice,
"market", withMarket,
"changePercent", withChangePercent,
"totalMarketValue", withTotalMarketValue,
            "turnoverRate", withTurnoverRate,
            "volumeRatio", withVolumeRatio,
            "industryCode", withIndustryCode
));
statistics.put("completenessRate", Map.of(
"name", String.format("%.2f%%", totalCount > 0 ? (withName * 100.0 / totalCount) : 0),
"price", String.format("%.2f%%", totalCount > 0 ? (withPrice * 100.0 / totalCount) : 0),
"market", String.format("%.2f%%", totalCount > 0 ? (withMarket * 100.0 / totalCount) : 0),
"changePercent", String.format("%.2f%%", totalCount > 0 ? (withChangePercent * 100.0 / totalCount) : 0),
"totalMarketValue", String.format("%.2f%%", totalCount > 0 ? (withTotalMarketValue * 100.0 / totalCount) : 0),
            "turnoverRate", String.format("%.2f%%", totalCount > 0 ? (withTurnoverRate * 100.0 / totalCount) : 0),
            "volumeRatio", String.format("%.2f%%", totalCount > 0 ? (withVolumeRatio * 100.0 / totalCount) : 0),
            "industryCode", String.format("%.2f%%", totalCount > 0 ? (withIndustryCode * 100.0 / totalCount) : 0)
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
        
        List<StockInfo> allStocks = stockDataService.findAllStocks();
        
        int total = allStocks.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        
        List<StockInfo> pageData = fromIndex < total 
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
        return stockDataService.findByCode(code)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 验证数据完整性
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateData() {
        List<StockInfo> allStocks = stockDataService.findAllStocks();
        
        int missingName = 0, missingPrice = 0, missingMarket = 0, missingChangePercent = 0, missingTotalMarketValue = 0, missingTurnoverRate = 0, missingCode = 0;
        
        for (StockInfo stock : allStocks) {
            if (stock.getCode() == null || stock.getCode().isEmpty()) missingCode++;
            if (stock.getName() == null || stock.getName().isEmpty()) missingName++;
            if (stock.getPrice() == null) missingPrice++;
            if (stock.getMarket() == null || stock.getMarket().isEmpty()) missingMarket++;
            if (stock.getChangePercent() == null) missingChangePercent++;
            if (stock.getTotalMarketValue() == null) missingTotalMarketValue++;
            if (stock.getTurnoverRate() == null) missingTurnoverRate++;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalRecords", allStocks.size());
        result.put("validation", Map.of(
            "missingCode", missingCode,
            "missingName", missingName,
            "missingPrice", missingPrice,
            "missingMarket", missingMarket,
            "missingChangePercent", missingChangePercent,
            "missingTotalMarketValue", missingTotalMarketValue,
            "missingTurnoverRate", missingTurnoverRate
        ));
        result.put("isValid", missingCode == 0);
        result.put("dataQuality", String.format("%.2f%%", 
            allStocks.size() > 0 
                ? ((allStocks.size() - missingName) * 100.0 / allStocks.size()) 
                : 0));
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取股票历史K线数据
     * 优化：周K和月K使用预聚合数据，直接从数据库查询，无需实时计算
     * 日K线支持timeRange参数过滤，并限制最大返回数据量
     */
    @GetMapping("/kline/{code}")
    public ResponseEntity<Map<String, Object>> getKlineData(
            @PathVariable String code,
            @RequestParam(required = false, defaultValue = "daily") String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String timeRange,
            @RequestParam(required = false, defaultValue = "500") Integer limit) {

        log.info("获取K线数据: code={}, type={}, startDate={}, endDate={}, timeRange={}, limit={}",
                code, type, startDate, endDate, timeRange, limit);

        try {
            // 1. 计算日期范围：优先使用startDate/endDate，其次使用timeRange
            LocalDate queryStartDate = null;
            LocalDate queryEndDate = null;

            if (startDate != null && endDate != null) {
                // 显式指定日期范围
                queryStartDate = LocalDate.parse(startDate);
                queryEndDate = LocalDate.parse(endDate);
            } else if (timeRange != null) {
                // 根据timeRange计算日期范围
                queryEndDate = LocalDate.now();
                queryStartDate = switch (timeRange) {
                    case "thisWeek" -> queryEndDate.minusWeeks(1);
                    case "thisMonth" -> queryEndDate.minusMonths(1);
                    case "thisYear" -> queryEndDate.minusYears(1);
                    case "last3Years" -> queryEndDate.minusYears(3);
                    case "last5Years" -> queryEndDate.minusYears(5);
                    default -> queryEndDate.minusYears(1); // 默认查一年
                };
            }

            // 2. 根据type使用不同的数据源
            List<? extends StockPrice> prices;

            if (queryStartDate != null && queryEndDate != null) {
                // 有日期范围，使用日期范围查询
                if ("weekly".equals(type)) {
                    prices = stockDataService.getWeeklyPricesByDateRange(code, queryStartDate, queryEndDate);
                } else if ("monthly".equals(type)) {
                    prices = stockDataService.getMonthlyPricesByDateRange(code, queryStartDate, queryEndDate);
                } else {
                    // 日K线使用日期范围查询
                    prices = priceRepository.findByCodeAndDateBetweenOrderByDateAsc(code, queryStartDate, queryEndDate);
                    // 限制返回数量（从最新数据开始）
                    if (prices != null && prices.size() > limit) {
                        prices = prices.subList(prices.size() - limit, prices.size());
                    }
                }
            } else {
                // 无日期范围时，根据type使用预聚合数据或带限制的日K数据
                if ("weekly".equals(type)) {
                    prices = stockDataService.getWeeklyPrices(code);
                } else if ("monthly".equals(type)) {
                    prices = stockDataService.getMonthlyPrices(code);
                } else {
                    // 日K线：默认查询最近一年的数据，避免返回过多
                    LocalDate oneYearAgo = LocalDate.now().minusYears(1);
                    prices = priceRepository.findByCodeAndDateBetweenOrderByDateAsc(code, oneYearAgo, LocalDate.now());
                    if (prices != null && prices.size() > limit) {
                        prices = prices.subList(prices.size() - limit, prices.size());
                    }
                }
            }

            // 如果没有数据，返回空结果
            if (prices == null || prices.isEmpty()) {
                log.warn("股票 {} 没有K线数据", code);
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("data", Map.of(
                    "dates", List.of(),
                    "kline", List.of(),
                    "volumes", List.of()
                ));
                result.put("total", 0);
                return ResponseEntity.ok(result);
            }

            // 转换为ECharts需要的格式
            List<String> dates = prices.stream()
                .filter(p -> p.getDate() != null)
                .map(p -> p.getDate().toString())
                .toList();

            List<List<Object>> klineData = prices.stream()
                .filter(p -> p.getOpenPrice() != null && p.getClosePrice() != null
                    && p.getLowPrice() != null && p.getHighPrice() != null)
                .map(p -> {
                    List<Object> item = new java.util.ArrayList<>();
                    item.add(p.getOpenPrice().doubleValue());
                    item.add(p.getClosePrice().doubleValue());
                    item.add(p.getLowPrice().doubleValue());
                    item.add(p.getHighPrice().doubleValue());
                    return item;
                })
                .toList();

            List<Object> volumes = prices.stream()
                .filter(p -> p.getVolume() != null)
                .map(p -> (Object) p.getVolume().doubleValue())
                .toList();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", Map.of(
                "dates", dates,
                "kline", klineData,
                "volumes", volumes
            ));
            result.put("total", prices.size());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("获取K线数据失败: code={}", code, e);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "获取K线数据失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(result);
        }
    }
}