package com.stock.dataCollector.service;

import com.alibaba.fastjson2.JSONArray;
import com.stock.dataCollector.client.SecuritiesClient;
import com.stock.dataCollector.entity.StockInfo;
import com.stock.dataCollector.entity.StockPrice;
import com.stock.dataCollector.entity.mysql.StockInfoMySql;
import com.stock.dataCollector.repository.PriceRepository;
import com.stock.dataCollector.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 股票数据采集服务
 * 从证券平台获取股票数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockDataService {

    private final PriceRepository priceRepository;
    private final StockRepository stockRepository;
    private final SecuritiesClient securitiesClient;
    private final StockInfoMySqlService stockInfoMySqlService;

    /**
     * 获取股票历史价格数据
     */
    public List<StockPrice> getHistoryPrices(String stockCode) {
        log.info("获取股票 {} 的历史价格数据", stockCode);
        
        try {
            JSONArray results = securitiesClient.getHistoryPrices(stockCode, 20);
            
            if (results == null || results.isEmpty()) {
                log.warn("股票 {} 无历史价格数据", stockCode);
                return new ArrayList<>();
            }
            
            List<StockPrice> prices = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                String s = results.getString(i);
                s = s.replaceAll("\\[", "").replaceAll("\\]", "");
                String[] split = s.split(",");
                
                if (split.length < 3) {
                    continue;
                }
                
                StockPrice price = new StockPrice();
                price.setCode(stockCode);
                price.setDate(LocalDate.parse(split[0]));
                price.setOpenPrice(BigDecimal.valueOf(Double.parseDouble(split[1]) / 100));
                price.setClosePrice(BigDecimal.valueOf(Double.parseDouble(split[2]) / 100));
                
                if (split.length > 3) {
                    price.setHighPrice(BigDecimal.valueOf(Double.parseDouble(split[3]) / 100));
                    price.setLowPrice(BigDecimal.valueOf(Double.parseDouble(split[4]) / 100));
                }
                
                prices.add(price);
            }
            
            log.info("股票 {} 获取到 {} 条历史价格数据", stockCode, prices.size());
            return prices;
            
        } catch (Exception e) {
            log.error("获取股票 {} 历史价格数据失败", stockCode, e);
            return new ArrayList<>();
        }
    }

    /**
     * 保存股票价格数据到 MongoDB
     */
    @Transactional
    public void saveStockPrices(List<StockPrice> prices) {
        if (prices == null || prices.isEmpty()) {
            log.warn("价格数据为空，跳过保存");
            return;
        }

        log.info("开始保存 {} 条股票价格数据", prices.size());
        
        int saved = 0;
        int skipped = 0;
        int updated = 0;

        for (StockPrice price : prices) {
            try {
                var existingOpt = priceRepository.findByCodeAndDate(
                    price.getCode(), 
                    price.getDate()
                );

                if (existingOpt.isPresent()) {
                    StockPrice existing = existingOpt.get();
                    
                    if (existing.getClosePrice() == null || existing.getVolume() == null) {
                        existing.setOpenPrice(price.getOpenPrice());
                        existing.setHighPrice(price.getHighPrice());
                        existing.setLowPrice(price.getLowPrice());
                        existing.setClosePrice(price.getClosePrice());
                        existing.setVolume(price.getVolume());
                        existing.setAmount(price.getAmount());
                        priceRepository.save(existing);
                        updated++;
                    } else {
                        skipped++;
                    }
                } else {
                    priceRepository.save(price);
                    saved++;
                }
            } catch (Exception e) {
                log.error("保存股票数据失败：{}-{}", price.getCode(), price.getDate(), e);
            }
        }

        log.info("股票价格数据保存完成 - 新增：{}, 更新：{}, 跳过：{}", saved, updated, skipped);
    }

    /**
     * 从证券平台获取股票列表并保存到MongoDB和MySQL
     * 
     * @return 获取的股票数量
     */
    public int fetchAndSaveStockList() {
        log.info("从证券平台获取股票列表");
        
        try {
            JSONArray results = securitiesClient.getStockList();
            
            if (results == null || results.isEmpty()) {
                log.warn("未获取到股票列表数据");
                return 0;
            }
            
            int savedMongo = 0;
            int savedMysql = 0;
            List<StockInfoMySql> mysqlList = new ArrayList<>();
            
            for (int i = 0; i < results.size(); i++) {
                String s = results.getString(i);
                String[] split = s.split(",");
                
                if (split.length < 5) {
                    continue;
                }
                
                // 解析字段: 涨跌幅|价格|名称|市场|代码
                String changePercentStr = split[0].replaceAll("\"", "");
                String priceStr = split[1].replaceAll("\"", "");
                String name = split[2].replaceAll("\"", "");
                String market = split[3].replaceAll("\"", "");
                String code = split[4].replaceAll("\"", "");
                
                // 保存到MongoDB
                if (!stockRepository.existsByCode(code)) {
                    StockInfo stockInfo = new StockInfo();
                    stockInfo.setCode(code);
                    stockInfo.setName(name);
                    stockRepository.save(stockInfo);
                    savedMongo++;
                }
                
                // 构建MySQL实体
                StockInfoMySql mysqlEntity = new StockInfoMySql();
                mysqlEntity.setCode(code);
                mysqlEntity.setName(name);
                mysqlEntity.setMarket(parseMarket(market));
                
                try {
                    if (!changePercentStr.isEmpty()) {
                        mysqlEntity.setChangePercent(new BigDecimal(changePercentStr));
                    }
                    if (!priceStr.isEmpty()) {
                        mysqlEntity.setPrice(new BigDecimal(priceStr));
                    }
                } catch (NumberFormatException e) {
                    log.debug("解析价格或涨跌幅失败: code={}, price={}, change={}", code, priceStr, changePercentStr);
                }
                
                // 解析更多字段
                if (split.length > 5) {
                    try {
                        String volumeStr = split[5].replaceAll("\"", "");
                        if (!volumeStr.isEmpty()) {
                            mysqlEntity.setVolume(new BigDecimal(volumeStr));
                        }
                    } catch (Exception e) {
                        log.debug("解析成交量失败: {}", e.getMessage());
                    }
                }
                
                if (split.length > 9) {
                    try {
                        String totalValueStr = split[9].replaceAll("\"", "");
                        if (!totalValueStr.isEmpty()) {
                            mysqlEntity.setTotalMarketValue(new BigDecimal(totalValueStr));
                        }
                    } catch (Exception e) {
                        log.debug("解析总市值失败: {}", e.getMessage());
                    }
                }
                
                mysqlEntity.setDataSource("证券平台");
                mysqlList.add(mysqlEntity);
            }
            
            // 批量保存到MySQL
            savedMysql = stockInfoMySqlService.batchSaveOrUpdate(mysqlList);
            
            log.info("股票列表获取完成，共 {} 条，MongoDB新增 {} 条，MySQL保存/更新 {} 条", 
                results.size(), savedMongo, savedMysql);
            return savedMysql;
            
        } catch (Exception e) {
            log.error("从证券平台获取股票列表失败", e);
            return 0;
        }
    }

    /**
     * 解析市场代码
     */
    private String parseMarket(String market) {
        return switch (market.toUpperCase()) {
            case "SH" -> "上海";
            case "SZ" -> "深圳";
            case "BJ" -> "北京";
            default -> market;
        };
    }

    /**
     * 同步股票列表到MySQL（定时任务调用）
     * 
     * @return 同步结果
     */
    @Transactional
    public SyncResult syncStockListToMySql() {
        log.info("========== 开始同步股票列表到MySQL ==========");
        long startTime = System.currentTimeMillis();
        
        SyncResult result = new SyncResult();
        
        try {
            JSONArray stockList = securitiesClient.getStockList();
            
            if (stockList == null || stockList.isEmpty()) {
                log.warn("未获取到股票列表数据");
                return result;
            }
            
            result.setTotalCount(stockList.size());
            
            int savedCount = 0;
            int updatedCount = 0;
            int failedCount = 0;
            
            for (int i = 0; i < stockList.size(); i++) {
                try {
                    String s = stockList.getString(i);
                    String[] split = s.split(",");
                    
                    if (split.length < 5) {
                        failedCount++;
                        continue;
                    }
                    
                    String changePercentStr = split[0].replaceAll("\"", "");
                    String priceStr = split[1].replaceAll("\"", "");
                    String name = split[2].replaceAll("\"", "");
                    String market = split[3].replaceAll("\"", "");
                    String code = split[4].replaceAll("\"", "");
                    
                    StockInfoMySql entity = new StockInfoMySql();
                    entity.setCode(code);
                    entity.setName(name);
                    entity.setMarket(parseMarket(market));
                    
                    try {
                        if (!changePercentStr.isEmpty()) {
                            entity.setChangePercent(new BigDecimal(changePercentStr));
                        }
                        if (!priceStr.isEmpty()) {
                            entity.setPrice(new BigDecimal(priceStr));
                        }
                    } catch (NumberFormatException e) {
                        log.debug("解析价格失败: code={}", code);
                    }
                    
                    // 解析更多字段
                    if (split.length > 5) {
                        try {
                            String volumeStr = split[5].replaceAll("\"", "");
                            if (!volumeStr.isEmpty()) {
                                entity.setVolume(new BigDecimal(volumeStr));
                            }
                        } catch (Exception ignored) {}
                    }
                    
                    if (split.length > 9) {
                        try {
                            String totalValueStr = split[9].replaceAll("\"", "");
                            if (!totalValueStr.isEmpty()) {
                                entity.setTotalMarketValue(new BigDecimal(totalValueStr));
                            }
                        } catch (Exception ignored) {}
                    }
                    
                    entity.setDataSource("证券平台");
                    
                    // 检查是否存在
                    boolean exists = stockInfoMySqlService.findByCode(code).isPresent();
                    stockInfoMySqlService.saveOrUpdate(entity);
                    
                    if (exists) {
                        updatedCount++;
                    } else {
                        savedCount++;
                    }
                    
                } catch (Exception e) {
                    failedCount++;
                    log.error("处理股票数据失败", e);
                }
            }
            
            result.setSavedCount(savedCount);
            result.setUpdatedCount(updatedCount);
            result.setFailedCount(failedCount);
            
            long costTime = System.currentTimeMillis() - startTime;
            result.setCostTimeMs(costTime);
            
            log.info("========== 股票列表同步完成 ==========");
            log.info("总数: {}, 新增: {}, 更新: {}, 失败: {}, 耗时: {}ms", 
                result.getTotalCount(), result.getSavedCount(), result.getUpdatedCount(), 
                result.getFailedCount(), result.getCostTimeMs());
            
            return result;
            
        } catch (Exception e) {
            log.error("同步股票列表到MySQL失败", e);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    /**
     * 同步结果
     */
    @lombok.Data
    public static class SyncResult {
        private int totalCount;
        private int savedCount;
        private int updatedCount;
        private int failedCount;
        private long costTimeMs;
        private String errorMessage;
    }

    /**
     * 获取所有股票信息
     */
    public List<StockInfo> getAllStocks() {
        log.info("获取所有股票信息");
        return stockRepository.findAllByOrderByCodeAsc();
    }

    /**
     * 保存股票基本信息
     */
    @Transactional
    public void saveStockInfo(StockInfo stockInfo) {
        log.info("保存股票信息：{} - {}", stockInfo.getCode(), stockInfo.getName());
        stockRepository.save(stockInfo);
    }

    /**
     * 同步股票历史数据到 MongoDB
     */
    @Transactional
    public int syncHistoricalData(String stockCode, LocalDate startDate, LocalDate endDate) {
        log.info("开始同步股票 {} 从 {} 到 {} 的历史数据", stockCode, startDate, endDate);

        List<StockPrice> prices = getHistoryPrices(stockCode);
        
        List<StockPrice> filteredPrices = prices.stream()
            .filter(p -> !p.getDate().isBefore(startDate) && !p.getDate().isAfter(endDate))
            .toList();

        saveStockPrices(filteredPrices);

        log.info("股票 {} 历史数据同步完成，共 {} 条", stockCode, filteredPrices.size());
        return filteredPrices.size();
    }

    /**
     * 获取股票最新价格
     */
    public StockPrice getLatestPrice(String stockCode) {
        List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(stockCode);
        return prices.isEmpty() ? null : prices.get(prices.size() - 1);
    }

    /**
     * 批量获取股票最新价格
     */
    public List<StockPrice> getLatestPrices(List<String> stockCodes) {
        List<StockPrice> result = new ArrayList<>();
        for (String code : stockCodes) {
            StockPrice latest = getLatestPrice(code);
            if (latest != null) {
                result.add(latest);
            }
        }
        return result;
    }
}