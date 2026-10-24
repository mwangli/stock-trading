// AI_GENERATE_START----
package com.stock.market.service;

import com.alibaba.fastjson2.JSONArray;
import com.stock.platform.config.CacheNames;
import com.stock.market.dto.StockListSyncResult;
import com.stock.market.entity.StockInfo;
import com.stock.market.repository.StockInfoRepository;
import com.stock.market.client.SecuritiesClient;
import com.stock.market.parser.StockDataParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 股票基础信息全量同步服务。
 *
 * 负责接口数据解析、数据库现状分类、新股上市日期补全、退市确认及批量写入。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockListSyncService {

    /** 股票信息批量写入大小。 */
    private static final int WRITE_BATCH_SIZE = 200;

    private final StockInfoRepository stockInfoRepository;
    private final SecuritiesClient securitiesClient;
    private final DelistedStockCleanupService delistedStockCleanupService;

    /**
     * 同步证券接口中的全量股票基础信息。
     *
     * @return 同步结果统计
     * @throws IllegalStateException 证券接口返回为空时抛出
     */
    @CacheEvict(cacheNames = CacheNames.STOCK_INFO, allEntries = true)
    public StockListSyncResult syncStockList() {
        log.info("========== 开始同步股票列表 ==========");
        long startTime = System.currentTimeMillis();
        List<StockInfo> incomingStocks = parseStockList(fetchStockList());
        Map<String, StockInfo> existingStocks = loadExistingStocks();
        StockSyncPlan syncPlan = classifyStocks(incomingStocks, existingStocks);

        List<StockInfo> confirmedDelistedStocks = findConfirmedDelistedStocks(syncPlan.missingStocks());
        int cleanedCount = cleanupDelistedStocks(confirmedDelistedStocks);
        List<StockInfo> insertStocks = prepareInsertStocks(syncPlan.insertStocks());
        List<StockInfo> updateStocks = prepareUpdateStocks(syncPlan.updateStocks(), existingStocks);
        int ignoredMissingCount = syncPlan.missingStocks().size() - confirmedDelistedStocks.size();

        saveInBatches(insertStocks, "新增");
        saveInBatches(updateStocks, "更新");

        StockListSyncResult result = new StockListSyncResult();
        result.setTotalCount(incomingStocks.size());
        result.setSavedCount(insertStocks.size());
        result.setUpdatedCount(updateStocks.size());
        result.setCleanedCount(cleanedCount);
        result.setIgnoredMissingCount(ignoredMissingCount);
        result.setFailedCount(0);
        result.setCostTimeMs(System.currentTimeMillis() - startTime);
        log.info("股票列表同步完成: 总数={}, 新增={}, 更新={}, 清理退市={}, 忽略缺失={}, 耗时={}ms",
                result.getTotalCount(), result.getSavedCount(), result.getUpdatedCount(),
                result.getCleanedCount(), result.getIgnoredMissingCount(), result.getCostTimeMs());
        return result;
    }

    private JSONArray fetchStockList() {
        JSONArray stockList = securitiesClient.getStockList();
        if (stockList == null || stockList.isEmpty()) {
            throw new IllegalStateException("证券接口未返回股票列表");
        }
        return stockList;
    }

    private List<StockInfo> parseStockList(JSONArray stockList) {
        return StockDataParser.parseStockList(stockList).stream()
                .filter(stock -> stock.getCode() != null && !stock.getCode().isBlank())
                .collect(Collectors.toMap(StockInfo::getCode, stock -> stock, (first, ignored) -> first))
                .values().stream().toList();
    }

    private Map<String, StockInfo> loadExistingStocks() {
        return stockInfoRepository.findAll().stream()
                .filter(stock -> stock.getCode() != null && !stock.getCode().isBlank())
                .collect(Collectors.toMap(StockInfo::getCode, stock -> stock, (first, ignored) -> first));
    }

    private StockSyncPlan classifyStocks(List<StockInfo> incomingStocks, Map<String, StockInfo> existingStocks) {
        List<StockInfo> updateStocks = new ArrayList<>();
        List<StockInfo> insertStocks = new ArrayList<>();
        Set<String> incomingCodes = incomingStocks.stream().map(StockInfo::getCode).collect(Collectors.toSet());
        for (StockInfo incoming : incomingStocks) {
            if (existingStocks.containsKey(incoming.getCode())) {
                updateStocks.add(incoming);
            } else {
                insertStocks.add(incoming);
            }
        }
        List<StockInfo> missingStocks = existingStocks.values().stream()
                .filter(stock -> !incomingCodes.contains(stock.getCode()))
                .toList();
        return new StockSyncPlan(updateStocks, insertStocks, missingStocks);
    }

    private List<StockInfo> prepareInsertStocks(List<StockInfo> insertStocks) {
        for (StockInfo stock : insertStocks) {
            if (stock.getListedDate() == null) {
                stock.setListedDate(securitiesClient.getListingDate(stock.getCode()));
            }
        }
        return insertStocks;
    }

    private List<StockInfo> prepareUpdateStocks(List<StockInfo> updateStocks,
                                                Map<String, StockInfo> existingStocks) {
        for (StockInfo incoming : updateStocks) {
            updateStockInfo(existingStocks.get(incoming.getCode()), incoming);
        }
        return updateStocks.stream().map(stock -> existingStocks.get(stock.getCode())).toList();
    }

    private List<StockInfo> findConfirmedDelistedStocks(List<StockInfo> missingStocks) {
        return missingStocks.stream().filter(this::hasConfirmedDelistedDescription).toList();
    }

    private boolean hasConfirmedDelistedDescription(StockInfo stock) {
        return containsConfirmedDelistedKeyword(stock.getName())
                || containsConfirmedDelistedKeyword(stock.getSecurityCategory())
                || containsConfirmedDelistedKeyword(stock.getMarket());
    }

    private boolean containsConfirmedDelistedKeyword(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.contains("退市")
                || normalized.contains("终止上市")
                || normalized.contains("DELIST")
                || normalized.contains("TERMINATED");
    }

    private int cleanupDelistedStocks(List<StockInfo> delistedStocks) {
        for (StockInfo stock : delistedStocks) {
            delistedStockCleanupService.cleanup(stock.getCode());
        }
        return delistedStocks.size();
    }

    private void saveInBatches(List<StockInfo> stocks, String operation) {
        for (int index = 0; index < stocks.size(); index += WRITE_BATCH_SIZE) {
            int end = Math.min(index + WRITE_BATCH_SIZE, stocks.size());
            stockInfoRepository.saveAll(stocks.subList(index, end));
        }
        log.info("股票信息{}批量写入完成: {}条", operation, stocks.size());
    }

    private void updateStockInfo(StockInfo target, StockInfo source) {
        if (source.getName() != null) target.setName(source.getName());
        if (source.getMarket() != null) target.setMarket(source.getMarket());
        if (source.getPrice() != null) target.setPrice(source.getPrice());
        if (source.getChangeAmount() != null) target.setChangeAmount(source.getChangeAmount());
        if (source.getChangePercent() != null) target.setChangePercent(source.getChangePercent());
        if (source.getVolume() != null) target.setVolume(source.getVolume());
        if (source.getTotalMarketValue() != null) target.setTotalMarketValue(source.getTotalMarketValue());
        if (source.getTurnoverRate() != null) target.setTurnoverRate(source.getTurnoverRate());
        if (source.getVolumeRatio() != null) target.setVolumeRatio(source.getVolumeRatio());
        if (source.getBidPrice() != null) target.setBidPrice(source.getBidPrice());
        if (source.getAskPrice() != null) target.setAskPrice(source.getAskPrice());
        if (source.getSecurityCategory() != null) target.setSecurityCategory(source.getSecurityCategory());
        if (source.getMarketCode() != null) target.setMarketCode(source.getMarketCode());
        if (source.getMomentumStrength() != null) target.setMomentumStrength(source.getMomentumStrength());
        if (source.getVolumePriceSignal() != null) target.setVolumePriceSignal(source.getVolumePriceSignal());
        if (source.getTurnoverAnomaly() != null) target.setTurnoverAnomaly(source.getTurnoverAnomaly());
        if (source.getVolumePriceSignal() != null) target.setAbnormalVolumeFlag(source.isAbnormalVolumeFlag());
        if (source.getBidAskSpread() != null) target.setBidAskSpread(source.getBidAskSpread());
        if (source.getBidAskSpreadRate() != null) target.setBidAskSpreadRate(source.getBidAskSpreadRate());
        if (source.getIndustryCode() != null) target.setIndustryCode(source.getIndustryCode());
        if (source.getListedDate() != null) target.setListedDate(source.getListedDate());
    }

    private record StockSyncPlan(List<StockInfo> updateStocks,
                                 List<StockInfo> insertStocks,
                                 List<StockInfo> missingStocks) {
    }
}
// AI_GENERATE_END----
