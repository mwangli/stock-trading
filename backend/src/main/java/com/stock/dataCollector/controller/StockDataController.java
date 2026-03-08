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
     * 优化：周K和月K使用预聚合数据
     */
    @GetMapping("/kline/{code}")
    public ResponseEntity<Map<String, Object>> getKlineData(
            @PathVariable String code,
            @RequestParam(required = false, defaultValue = "daily") String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        log.info("获取K线数据: code={}, type={}, startDate={}, endDate={}", code, type, startDate, endDate);

        try {
            // 根据type使用不同的数据源
            List<? extends StockPrice> prices;

            if (startDate != null && endDate != null) {
                LocalDate start = LocalDate.parse(startDate);
                LocalDate end = LocalDate.parse(endDate);
                // 日期范围查询仍使用实时聚合
                if ("weekly".equals(type)) {
                    List<StockPrice> dailyPrices = priceRepository.findByCodeAndDateBetweenOrderByDateAsc(code, start, end);
                    prices = aggregateToWeekly(dailyPrices);
                } else if ("monthly".equals(type)) {
                    List<StockPrice> dailyPrices = priceRepository.findByCodeAndDateBetweenOrderByDateAsc(code, start, end);
                    prices = aggregateToMonthly(dailyPrices);
                } else {
                    prices = priceRepository.findByCodeAndDateBetweenOrderByDateAsc(code, start, end);
                }
            } else {
                // 无日期范围时，使用预聚合数据
                if ("weekly".equals(type)) {
                    prices = stockDataService.getWeeklyPrices(code);
                } else if ("monthly".equals(type)) {
                    prices = stockDataService.getMonthlyPrices(code);
                } else {
                    prices = priceRepository.findByCodeOrderByDateAsc(code);
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
    
    /**
     * 将日K数据聚合为周K
     */
    private List<StockPrice> aggregateToWeekly(List<StockPrice> dailyPrices) {
        if (dailyPrices == null || dailyPrices.isEmpty()) {
            return List.of();
        }
        
        return dailyPrices.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                p -> {
                    // 按周分组：每周一为起始
                    int weekOfYear = p.getDate().getDayOfYear();
                    int weekNum = (weekOfYear - 1) / 7 + 1;
                    return p.getDate().getYear() + "-W" + weekNum;
                }
            ))
            .values()
            .stream()
            .map(weekPrices -> {
                // 取周第一天作为日期
                StockPrice first = weekPrices.get(0);
                StockPrice weekPrice = new StockPrice();
                weekPrice.setDate(first.getDate());
                weekPrice.setOpenPrice(first.getOpenPrice());
                weekPrice.setClosePrice(weekPrices.get(weekPrices.size() - 1).getClosePrice());
                weekPrice.setHighPrice(weekPrices.stream()
                    .map(StockPrice::getHighPrice)
                    .reduce(java.math.BigDecimal::max)
                    .orElse(java.math.BigDecimal.ZERO));
                weekPrice.setLowPrice(weekPrices.stream()
                    .map(StockPrice::getLowPrice)
                    .reduce(java.math.BigDecimal::min)
                    .orElse(java.math.BigDecimal.ZERO));
                weekPrice.setVolume(weekPrices.stream()
                    .map(StockPrice::getVolume)
                    .reduce(java.math.BigDecimal::add)
                    .orElse(java.math.BigDecimal.ZERO));
                weekPrice.setAmount(weekPrices.stream()
                    .map(StockPrice::getAmount)
                    .reduce(java.math.BigDecimal::add)
                    .orElse(java.math.BigDecimal.ZERO));
                return weekPrice;
            })
            .sorted(java.util.Comparator.comparing(StockPrice::getDate))
            .toList();
    }
    
    /**
     * 将日K数据聚合为月K
     */
    private List<StockPrice> aggregateToMonthly(List<StockPrice> dailyPrices) {
        if (dailyPrices == null || dailyPrices.isEmpty()) {
            return List.of();
        }
        
        return dailyPrices.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                p -> p.getDate().getYear() + "-" + String.format("%02d", p.getDate().getMonthValue())
            ))
            .values()
            .stream()
            .map(monthPrices -> {
                StockPrice first = monthPrices.get(0);
                StockPrice monthPrice = new StockPrice();
                monthPrice.setDate(first.getDate());
                monthPrice.setOpenPrice(first.getOpenPrice());
                monthPrice.setClosePrice(monthPrices.get(monthPrices.size() - 1).getClosePrice());
                monthPrice.setHighPrice(monthPrices.stream()
                    .map(StockPrice::getHighPrice)
                    .reduce(java.math.BigDecimal::max)
                    .orElse(java.math.BigDecimal.ZERO));
                monthPrice.setLowPrice(monthPrices.stream()
                    .map(StockPrice::getLowPrice)
                    .reduce(java.math.BigDecimal::min)
                    .orElse(java.math.BigDecimal.ZERO));
                monthPrice.setVolume(monthPrices.stream()
                    .map(StockPrice::getVolume)
                    .reduce(java.math.BigDecimal::add)
                    .orElse(java.math.BigDecimal.ZERO));
                monthPrice.setAmount(monthPrices.stream()
                    .map(StockPrice::getAmount)
                    .reduce(java.math.BigDecimal::add)
                    .orElse(java.math.BigDecimal.ZERO));
                return monthPrice;
            })
            .sorted(java.util.Comparator.comparing(StockPrice::getDate))
            .toList();
    }
}