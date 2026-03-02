package com.stock.dataCollector.service;

import com.alibaba.fastjson2.JSONArray;
import com.stock.dataCollector.client.SecuritiesClient;
import com.stock.dataCollector.entity.StockInfo;
import com.stock.dataCollector.entity.StockPrice;
import com.stock.dataCollector.repository.PriceRepository;
import com.stock.dataCollector.repository.StockInfoRepository;
import com.stock.dataCollector.util.StockDataParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 股票数据服务
 * 统一处理股票信息的采集、存储和查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockDataService {

    private final PriceRepository priceRepository;
    private final StockInfoRepository stockInfoRepository;
    private final SecuritiesClient securitiesClient;

    // ==================== 股票列表同步 ====================

    /**
     * 同步股票列表到MySQL
     * 
     * @return 同步结果
     */
    @Transactional
    public SyncResult syncStockList() {
        log.info("========== 开始同步股票列表 ==========");
        long startTime = System.currentTimeMillis();
        
        SyncResult result = new SyncResult();
        
        try {
            JSONArray stockList = securitiesClient.getStockList();
            
            if (stockList == null || stockList.isEmpty()) {
                log.warn("未获取到股票列表数据");
                return result;
            }
            
            result.setTotalCount(stockList.size());
            
            // 使用解析器解析数据
            List<StockInfo> stockInfos = StockDataParser.parseStockList(stockList);
            
            // 批量保存
            int[] counts = batchSaveWithStats(stockInfos);
            result.setSavedCount(counts[0]);
            result.setUpdatedCount(counts[1]);
            result.setFailedCount(stockList.size() - counts[0] - counts[1]);
            
            long costTime = System.currentTimeMillis() - startTime;
            result.setCostTimeMs(costTime);
            
            log.info("========== 股票列表同步完成 ==========");
            log.info("总数: {}, 新增: {}, 更新: {}, 失败: {}, 耗时: {}ms", 
                result.getTotalCount(), result.getSavedCount(), result.getUpdatedCount(), 
                result.getFailedCount(), result.getCostTimeMs());
            
            return result;
            
        } catch (Exception e) {
            log.error("同步股票列表失败", e);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    /**
     * 批量保存并统计新增/更新数量
     */
    private int[] batchSaveWithStats(List<StockInfo> stockInfos) {
        int savedCount = 0;
        int updatedCount = 0;
        
        for (StockInfo stock : stockInfos) {
            try {
                boolean exists = stockInfoRepository.existsByCode(stock.getCode());
                saveOrUpdate(stock);
                if (exists) {
                    updatedCount++;
                } else {
                    savedCount++;
                }
            } catch (Exception e) {
                log.error("保存股票 {} 失败: {}", stock.getCode(), e.getMessage());
            }
        }
        
        return new int[]{savedCount, updatedCount};
    }

    /**
     * 保存或更新股票信息
     */
    public StockInfo saveOrUpdate(StockInfo stockInfo) {
        Optional<StockInfo> existing = stockInfoRepository.findByCode(stockInfo.getCode());
        
        if (existing.isPresent()) {
            StockInfo existingStock = existing.get();
            updateStockInfo(existingStock, stockInfo);
            return stockInfoRepository.save(existingStock);
        } else {
            return stockInfoRepository.save(stockInfo);
        }
    }

    /**
     * 更新股票信息字段
     */
private void updateStockInfo(StockInfo target, StockInfo source) {
if (source.getName() != null) target.setName(source.getName());
if (source.getMarket() != null) target.setMarket(source.getMarket());
if (source.getPrice() != null) target.setPrice(source.getPrice());
if (source.getChangeAmount() != null) target.setChangeAmount(source.getChangeAmount());
if (source.getChangePercent() != null) target.setChangePercent(source.getChangePercent());
if (source.getTotalMarketValue() != null) target.setTotalMarketValue(source.getTotalMarketValue());
        if (source.getTurnoverRate() != null) target.setTurnoverRate(source.getTurnoverRate());
        if (source.getVolumeRatio() != null) target.setVolumeRatio(source.getVolumeRatio());
        if (source.getIndustryCode() != null) target.setIndustryCode(source.getIndustryCode());
}

    // ==================== 历史价格同步 ====================

    /**
     * 获取股票历史价格数据
     */
    public List<StockPrice> getHistoryPrices(String stockCode) {
        log.info("获取股票 {} 的历史价格数据", stockCode);
        
        try {
            JSONArray results = securitiesClient.getHistoryPrices(stockCode, 20);
            return StockDataParser.parsePriceList(results, stockCode);
        } catch (Exception e) {
            log.error("获取股票 {} 历史价格数据失败", stockCode, e);
            return List.of();
        }
    }

    /**
     * 保存股票价格数据到MongoDB
     */
    @Transactional
    public void saveStockPrices(List<StockPrice> prices) {
        if (prices == null || prices.isEmpty()) {
            log.warn("价格数据为空，跳过保存");
            return;
        }

        log.info("开始保存 {} 条股票价格数据", prices.size());
        
        int saved = 0, updated = 0, skipped = 0;

        for (StockPrice price : prices) {
            try {
                var existingOpt = priceRepository.findByCodeAndDate(price.getCode(), price.getDate());

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
     * 同步股票历史数据到MongoDB
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

    // ==================== 查询方法 ====================

    /**
     * 查询所有股票
     */
    public List<StockInfo> findAllStocks() {
        return stockInfoRepository.findAll();
    }

    /**
     * 分页查询股票
     */
    public Page<StockInfo> findAllStocks(Pageable pageable) {
        return stockInfoRepository.findAll(pageable);
    }

    /**
     * 根据代码查询股票
     */
    public Optional<StockInfo> findByCode(String code) {
        return stockInfoRepository.findByCode(code);
    }

    /**
     * 查询所有股票代码
     */
    public List<String> findAllCodes() {
        return stockInfoRepository.findAllCodes();
    }

    /**
     * 统计股票总数
     */
    public long count() {
        return stockInfoRepository.count();
    }

    /**
     * 根据市场查询
     */
    public List<StockInfo> findByMarket(String market) {
        return stockInfoRepository.findByMarket(market);
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
        return stockCodes.stream()
            .map(this::getLatestPrice)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    /**
     * 删除所有股票数据
     */
    @Transactional
    public void deleteAllStocks() {
        stockInfoRepository.deleteAll();
    }

    // ==================== 同步结果 ====================

    @lombok.Data
    public static class SyncResult {
        private int totalCount;
        private int savedCount;
        private int updatedCount;
        private int failedCount;
        private long costTimeMs;
        private String errorMessage;
    }
}