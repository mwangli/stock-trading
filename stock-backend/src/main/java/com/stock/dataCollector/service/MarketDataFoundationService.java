// AI_GENERATE_START -----
package com.stock.dataCollector.service;

import com.stock.dataCollector.domain.dto.AdjustmentFactorImportRequestDto;
import com.stock.dataCollector.domain.dto.HistoricalUniverseImportRequestDto;
import com.stock.dataCollector.domain.dto.MarketDataImportResultDto;
import com.stock.dataCollector.domain.dto.MinuteBarImportRequestDto;
import com.stock.dataCollector.domain.dto.ModelReferenceDailyBarImportRequestDto;
import com.stock.dataCollector.domain.dto.ModelStockDailyBarImportRequestDto;
import com.stock.dataCollector.domain.dto.TradeCalendarImportRequestDto;
import com.stock.dataCollector.domain.dto.StockDailyTradabilityImportRequestDto;
import com.stock.dataCollector.domain.entity.HistoricalStockUniverseEntity;
import com.stock.dataCollector.domain.entity.ModelReferenceDailyBar;
import com.stock.dataCollector.domain.entity.ModelStockDailyBar;
import com.stock.dataCollector.domain.entity.StockAdjustmentFactor;
import com.stock.dataCollector.domain.entity.StockMinuteBar;
import com.stock.dataCollector.domain.entity.StockDailyTradabilityFact;
import com.stock.dataCollector.domain.entity.OpenTradabilityStatus;
import com.stock.dataCollector.domain.entity.TradeCalendarEntity;
import com.stock.dataCollector.persistence.HistoricalStockUniverseRepository;
import com.stock.dataCollector.persistence.TradeCalendarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产级回测市场数据基础导入服务。
 * 提供历史股票池、交易日历、复权因子和 5 分钟 K 线的受控幂等写入。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataFoundationService {

    private final HistoricalStockUniverseRepository historicalStockUniverseRepository;
    private final TradeCalendarRepository tradeCalendarRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * 导入历史股票池有效期版本。
     *
     * @param request 历史股票池批次
     * @return 导入统计
     */
    @Transactional
    public MarketDataImportResultDto importHistoricalUniverse(HistoricalUniverseImportRequestDto request) {
        LocalDateTime now = LocalDateTime.now();
        List<HistoricalUniverseImportRequestDto.Item> items = deduplicateUniverse(request.getItems());
        List<HistoricalStockUniverseEntity> entities = new ArrayList<>();
        for (HistoricalUniverseImportRequestDto.Item item : items) {
            validateUniverseItem(item);
            HistoricalStockUniverseEntity entity = historicalStockUniverseRepository
                    .findByStockCodeAndEffectiveFromAndSourceAndSourceVersion(
                            item.getStockCode().trim(), item.getEffectiveFrom(), request.getSource().trim(),
                            request.getSourceVersion().trim())
                    .orElseGet(() -> HistoricalStockUniverseEntity.builder()
                            .stockCode(item.getStockCode().trim())
                            .effectiveFrom(item.getEffectiveFrom())
                            .source(request.getSource().trim())
                            .createdAt(now)
                            .build());
            entity.setStockName(trimToNull(item.getStockName()));
            entity.setMarket(item.getMarket().trim());
            entity.setBoardCode(trimToNull(item.getBoardCode()));
            entity.setIndustryCode(trimToNull(item.getIndustryCode()));
            entity.setListedDate(item.getListedDate());
            entity.setDelistedDate(item.getDelistedDate());
            entity.setStStatus(item.getStStatus().trim());
            entity.setEffectiveTo(item.getEffectiveTo());
            entity.setSourceVersion(request.getSourceVersion().trim());
            entity.setCapturedAt(now);
            entity.setUpdatedAt(now);
            entities.add(entity);
        }
        historicalStockUniverseRepository.saveAll(entities);
        return result("HISTORICAL_UNIVERSE", request.getSource(), request.getSourceVersion(),
                request.getItems().size(), entities.size());
    }

    /**
     * 导入证券市场交易日历。
     *
     * @param request 交易日历批次
     * @return 导入统计
     */
    @Transactional
    public MarketDataImportResultDto importTradeCalendar(TradeCalendarImportRequestDto request) {
        LocalDateTime now = LocalDateTime.now();
        List<TradeCalendarImportRequestDto.Item> items = deduplicateCalendar(request.getItems());
        List<TradeCalendarEntity> entities = new ArrayList<>();
        for (TradeCalendarImportRequestDto.Item item : items) {
            TradeCalendarEntity entity = tradeCalendarRepository
                    .findByMarketAndTradeDateAndSourceAndSourceVersion(
                            item.getMarket().trim(), item.getTradeDate(), request.getSource().trim(),
                            request.getSourceVersion().trim())
                    .orElseGet(() -> TradeCalendarEntity.builder()
                            .market(item.getMarket().trim())
                            .tradeDate(item.getTradeDate())
                            .createdAt(now)
                            .build());
            entity.setTradingDay(item.isTradingDay());
            entity.setPreviousTradingDay(item.getPreviousTradingDay());
            entity.setNextTradingDay(item.getNextTradingDay());
            entity.setSource(request.getSource().trim());
            entity.setSourceVersion(request.getSourceVersion().trim());
            entity.setUpdatedAt(now);
            entities.add(entity);
        }
        tradeCalendarRepository.saveAll(entities);
        return result("TRADE_CALENDAR", request.getSource(), request.getSourceVersion(),
                request.getItems().size(), entities.size());
    }

    /**
     * 导入数据源提供的股票复权因子。
     *
     * @param request 复权因子批次
     * @return 导入统计
     */
    public MarketDataImportResultDto importAdjustmentFactors(AdjustmentFactorImportRequestDto request) {
        List<AdjustmentFactorImportRequestDto.Item> items = deduplicateFactors(request.getItems());
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED,
                StockAdjustmentFactor.class);
        LocalDateTime now = LocalDateTime.now();
        for (AdjustmentFactorImportRequestDto.Item item : items) {
            validateAdjustmentFactor(item);
            Query query = Query.query(Criteria.where("stockCode").is(item.getStockCode().trim())
                    .and("tradeDate").is(item.getTradeDate())
                    .and("source").is(request.getSource().trim())
                    .and("sourceVersion").is(request.getSourceVersion().trim()));
            Update update = new Update()
                    .setOnInsert("stockCode", item.getStockCode().trim())
                    .setOnInsert("tradeDate", item.getTradeDate())
                    .setOnInsert("source", request.getSource().trim())
                    .setOnInsert("sourceVersion", request.getSourceVersion().trim())
                    .set("forwardFactor", item.getForwardFactor())
                    .set("backwardFactor", item.getBackwardFactor())
                    .set("ingestedAt", now);
            bulk.upsert(query, update);
        }
        bulk.execute();
        return result("ADJUSTMENT_FACTOR", request.getSource(), request.getSourceVersion(),
                request.getItems().size(), items.size());
    }

    /**
     * 导入股票 5 分钟 K 线。
     *
     * @param request 5 分钟行情批次
     * @return 导入统计
     */
    public MarketDataImportResultDto importMinuteBars(MinuteBarImportRequestDto request) {
        List<MinuteBarImportRequestDto.Item> items = deduplicateMinuteBars(request.getItems());
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, StockMinuteBar.class);
        LocalDateTime now = LocalDateTime.now();
        for (MinuteBarImportRequestDto.Item item : items) {
            validateMinuteBar(item);
            Query query = Query.query(Criteria.where("stockCode").is(item.getStockCode().trim())
                    .and("barTime").is(item.getBarTime())
                    .and("source").is(request.getSource().trim())
                    .and("sourceVersion").is(request.getSourceVersion().trim()));
            Update update = new Update()
                    .setOnInsert("stockCode", item.getStockCode().trim())
                    .setOnInsert("barTime", item.getBarTime())
                    .setOnInsert("source", request.getSource().trim())
                    .setOnInsert("sourceVersion", request.getSourceVersion().trim())
                    .set("tradingDate", item.getBarTime().toLocalDate())
                    .set("openPrice", item.getOpenPrice())
                    .set("highPrice", item.getHighPrice())
                    .set("lowPrice", item.getLowPrice())
                    .set("closePrice", item.getClosePrice())
                    .set("volume", item.getVolume())
                    .set("amount", item.getAmount())
                    .set("volumeUnit", request.getVolumeUnit().trim())
                    .set("amountUnit", request.getAmountUnit().trim())
                    .set("ingestedAt", now);
            bulk.upsert(query, update);
        }
        bulk.execute();
        return result("MINUTE_BAR_5M", request.getSource(), request.getSourceVersion(),
                request.getItems().size(), items.size());
    }

    /**
     * 导入模型特征专用的版本化股票日线。
     *
     * @param request 股票日线批次
     * @return 导入统计
     */
    public MarketDataImportResultDto importModelStockDailyBars(ModelStockDailyBarImportRequestDto request) {
        List<ModelStockDailyBarImportRequestDto.Item> items = deduplicateModelStockDailyBars(request.getItems());
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ModelStockDailyBar.class);
        LocalDateTime now = LocalDateTime.now();
        for (ModelStockDailyBarImportRequestDto.Item item : items) {
            validateDailyBar(item.getStockCode(), item.getTradeDate().toString(), item.getOpenPrice(),
                    item.getHighPrice(), item.getLowPrice(), item.getClosePrice());
            Query query = Query.query(Criteria.where("stockCode").is(item.getStockCode().trim())
                    .and("tradeDate").is(item.getTradeDate())
                    .and("source").is(request.getSource().trim())
                    .and("sourceVersion").is(request.getSourceVersion().trim()));
            Update update = new Update()
                    .setOnInsert("stockCode", item.getStockCode().trim())
                    .setOnInsert("tradeDate", item.getTradeDate())
                    .setOnInsert("source", request.getSource().trim())
                    .setOnInsert("sourceVersion", request.getSourceVersion().trim())
                    .set("openPrice", item.getOpenPrice())
                    .set("highPrice", item.getHighPrice())
                    .set("lowPrice", item.getLowPrice())
                    .set("closePrice", item.getClosePrice())
                    .set("volume", item.getVolume())
                    .set("amount", item.getAmount())
                    .set("turnoverRate", item.getTurnoverRate())
                    .set("volumeUnit", request.getVolumeUnit().trim())
                    .set("amountUnit", request.getAmountUnit().trim())
                    .set("ingestedAt", now);
            bulk.upsert(query, update);
        }
        bulk.execute();
        return result("MODEL_STOCK_DAILY_BAR", request.getSource(), request.getSourceVersion(),
                request.getItems().size(), items.size());
    }

    /**
     * 导入模型特征使用的市场或行业参考日线。
     *
     * @param request 参考序列日线批次
     * @return 导入统计
     */
    public MarketDataImportResultDto importModelReferenceDailyBars(
            ModelReferenceDailyBarImportRequestDto request) {
        List<ModelReferenceDailyBarImportRequestDto.Item> items =
                deduplicateModelReferenceDailyBars(request.getItems());
        BulkOperations bulk = mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED, ModelReferenceDailyBar.class);
        LocalDateTime now = LocalDateTime.now();
        for (ModelReferenceDailyBarImportRequestDto.Item item : items) {
            validateDailyBar(item.getSeriesCode(), item.getTradeDate().toString(), item.getOpenPrice(),
                    item.getHighPrice(), item.getLowPrice(), item.getClosePrice());
            Query query = Query.query(Criteria.where("seriesType").is(request.getSeriesType())
                    .and("seriesCode").is(item.getSeriesCode().trim())
                    .and("tradeDate").is(item.getTradeDate())
                    .and("source").is(request.getSource().trim())
                    .and("sourceVersion").is(request.getSourceVersion().trim()));
            Update update = new Update()
                    .setOnInsert("seriesType", request.getSeriesType())
                    .setOnInsert("seriesCode", item.getSeriesCode().trim())
                    .setOnInsert("tradeDate", item.getTradeDate())
                    .setOnInsert("source", request.getSource().trim())
                    .setOnInsert("sourceVersion", request.getSourceVersion().trim())
                    .set("openPrice", item.getOpenPrice())
                    .set("highPrice", item.getHighPrice())
                    .set("lowPrice", item.getLowPrice())
                    .set("closePrice", item.getClosePrice())
                    .set("ingestedAt", now);
            bulk.upsert(query, update);
        }
        bulk.execute();
        return result("MODEL_REFERENCE_DAILY_BAR_" + request.getSeriesType().name(),
                request.getSource(), request.getSourceVersion(), request.getItems().size(), items.size());
    }

    /**
     * 导入股票单日开盘可成交性事实。
     *
     * @param request 可成交性事实批次
     * @return 导入统计
     */
    public MarketDataImportResultDto importStockDailyTradability(
            StockDailyTradabilityImportRequestDto request) {
        List<StockDailyTradabilityImportRequestDto.Item> items =
                deduplicateTradabilityFacts(request.getItems());
        BulkOperations bulk = mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED, StockDailyTradabilityFact.class);
        LocalDateTime now = LocalDateTime.now();
        for (StockDailyTradabilityImportRequestDto.Item item : items) {
            validateTradabilityFact(item, now);
            Query query = Query.query(Criteria.where("stockCode").is(item.getStockCode().trim())
                    .and("tradeDate").is(item.getTradeDate())
                    .and("source").is(request.getSource().trim())
                    .and("sourceVersion").is(request.getSourceVersion().trim()));
            Update update = new Update()
                    .setOnInsert("stockCode", item.getStockCode().trim())
                    .setOnInsert("tradeDate", item.getTradeDate())
                    .setOnInsert("source", request.getSource().trim())
                    .setOnInsert("sourceVersion", request.getSourceVersion().trim())
                    .set("openBuyStatus", item.getOpenBuyStatus())
                    .set("openSellStatus", item.getOpenSellStatus())
                    .set("observedOpenPrice", item.getObservedOpenPrice())
                    .set("limitUpPrice", item.getLimitUpPrice())
                    .set("limitDownPrice", item.getLimitDownPrice())
                    .set("buyReason", trimToNull(item.getBuyReason()))
                    .set("sellReason", trimToNull(item.getSellReason()))
                    .set("capturedAt", item.getCapturedAt())
                    .set("ingestedAt", now);
            bulk.upsert(query, update);
        }
        bulk.execute();
        return result("STOCK_DAILY_TRADABILITY", request.getSource(), request.getSourceVersion(),
                request.getItems().size(), items.size());
    }

    private List<HistoricalUniverseImportRequestDto.Item> deduplicateUniverse(
            List<HistoricalUniverseImportRequestDto.Item> items) {
        Map<String, HistoricalUniverseImportRequestDto.Item> unique = new LinkedHashMap<>();
        for (HistoricalUniverseImportRequestDto.Item item : items) {
            unique.put(item.getStockCode().trim() + "|" + item.getEffectiveFrom(), item);
        }
        return List.copyOf(unique.values());
    }

    private List<TradeCalendarImportRequestDto.Item> deduplicateCalendar(
            List<TradeCalendarImportRequestDto.Item> items) {
        Map<String, TradeCalendarImportRequestDto.Item> unique = new LinkedHashMap<>();
        for (TradeCalendarImportRequestDto.Item item : items) {
            unique.put(item.getMarket().trim() + "|" + item.getTradeDate(), item);
        }
        return List.copyOf(unique.values());
    }

    private List<AdjustmentFactorImportRequestDto.Item> deduplicateFactors(
            List<AdjustmentFactorImportRequestDto.Item> items) {
        Map<String, AdjustmentFactorImportRequestDto.Item> unique = new LinkedHashMap<>();
        for (AdjustmentFactorImportRequestDto.Item item : items) {
            unique.put(item.getStockCode().trim() + "|" + item.getTradeDate(), item);
        }
        return List.copyOf(unique.values());
    }

    private List<MinuteBarImportRequestDto.Item> deduplicateMinuteBars(
            List<MinuteBarImportRequestDto.Item> items) {
        Map<String, MinuteBarImportRequestDto.Item> unique = new LinkedHashMap<>();
        for (MinuteBarImportRequestDto.Item item : items) {
            unique.put(item.getStockCode().trim() + "|" + item.getBarTime(), item);
        }
        return List.copyOf(unique.values());
    }

    private List<ModelStockDailyBarImportRequestDto.Item> deduplicateModelStockDailyBars(
            List<ModelStockDailyBarImportRequestDto.Item> items) {
        Map<String, ModelStockDailyBarImportRequestDto.Item> unique = new LinkedHashMap<>();
        for (ModelStockDailyBarImportRequestDto.Item item : items) {
            unique.put(item.getStockCode().trim() + "|" + item.getTradeDate(), item);
        }
        return List.copyOf(unique.values());
    }

    private List<ModelReferenceDailyBarImportRequestDto.Item> deduplicateModelReferenceDailyBars(
            List<ModelReferenceDailyBarImportRequestDto.Item> items) {
        Map<String, ModelReferenceDailyBarImportRequestDto.Item> unique = new LinkedHashMap<>();
        for (ModelReferenceDailyBarImportRequestDto.Item item : items) {
            unique.put(item.getSeriesCode().trim() + "|" + item.getTradeDate(), item);
        }
        return List.copyOf(unique.values());
    }

    private List<StockDailyTradabilityImportRequestDto.Item> deduplicateTradabilityFacts(
            List<StockDailyTradabilityImportRequestDto.Item> items) {
        Map<String, StockDailyTradabilityImportRequestDto.Item> unique = new LinkedHashMap<>();
        for (StockDailyTradabilityImportRequestDto.Item item : items) {
            unique.put(item.getStockCode().trim() + "|" + item.getTradeDate(), item);
        }
        return List.copyOf(unique.values());
    }

    private void validateUniverseItem(HistoricalUniverseImportRequestDto.Item item) {
        if (item.getEffectiveTo() != null && item.getEffectiveTo().isBefore(item.getEffectiveFrom())) {
            throw new IllegalArgumentException("历史股票池失效日期不能早于生效日期: " + item.getStockCode());
        }
        if (item.getDelistedDate() != null && item.getListedDate() != null
                && item.getDelistedDate().isBefore(item.getListedDate())) {
            throw new IllegalArgumentException("退市日期不能早于上市日期: " + item.getStockCode());
        }
    }

    private void validateAdjustmentFactor(AdjustmentFactorImportRequestDto.Item item) {
        if (!isPositive(item.getForwardFactor()) && !isPositive(item.getBackwardFactor())) {
            throw new IllegalArgumentException("前复权因子和后复权因子至少需要提供一个正数: " + item.getStockCode());
        }
    }

    private void validateMinuteBar(MinuteBarImportRequestDto.Item item) {
        validateDailyBar(item.getStockCode(), item.getBarTime().toString(), item.getOpenPrice(),
                item.getHighPrice(), item.getLowPrice(), item.getClosePrice());
    }

    private void validateDailyBar(String code, String time, BigDecimal openPrice,
                                  BigDecimal highPrice, BigDecimal lowPrice, BigDecimal closePrice) {
        BigDecimal maxBody = openPrice.max(closePrice);
        BigDecimal minBody = openPrice.min(closePrice);
        if (highPrice.compareTo(maxBody) < 0 || highPrice.compareTo(lowPrice) < 0) {
            throw new IllegalArgumentException("K 线最高价无效: " + code + " " + time);
        }
        if (lowPrice.compareTo(minBody) > 0) {
            throw new IllegalArgumentException("K 线最低价无效: " + code + " " + time);
        }
    }

    private void validateTradabilityFact(
            StockDailyTradabilityImportRequestDto.Item item, LocalDateTime now) {
        if (item.getCapturedAt().isAfter(now)) {
            throw new IllegalArgumentException("可成交性事实时间不能晚于当前时间: "
                    + item.getStockCode() + " " + item.getTradeDate());
        }
        if (item.getLimitUpPrice() != null && item.getLimitDownPrice() != null
                && item.getLimitUpPrice().compareTo(item.getLimitDownPrice()) <= 0) {
            throw new IllegalArgumentException("涨停价格必须高于跌停价格: "
                    + item.getStockCode() + " " + item.getTradeDate());
        }
        if (item.getObservedOpenPrice() != null && item.getLimitUpPrice() != null
                && item.getObservedOpenPrice().compareTo(item.getLimitUpPrice()) > 0) {
            throw new IllegalArgumentException("开盘价格不能高于涨停价格: "
                    + item.getStockCode() + " " + item.getTradeDate());
        }
        if (item.getObservedOpenPrice() != null && item.getLimitDownPrice() != null
                && item.getObservedOpenPrice().compareTo(item.getLimitDownPrice()) < 0) {
            throw new IllegalArgumentException("开盘价格不能低于跌停价格: "
                    + item.getStockCode() + " " + item.getTradeDate());
        }
        validateTradabilityReason(item.getOpenBuyStatus(), item.getBuyReason(), "买入", item.getStockCode());
        validateTradabilityReason(item.getOpenSellStatus(), item.getSellReason(), "卖出", item.getStockCode());
    }

    private void validateTradabilityReason(
            OpenTradabilityStatus status, String reason, String direction, String stockCode) {
        if (status != OpenTradabilityStatus.ALLOWED && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException(direction + "不可执行或未知状态必须提供原因: " + stockCode);
        }
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MarketDataImportResultDto result(String dataType, String source, String sourceVersion,
                                             int receivedCount, int upsertedCount) {
        int skippedCount = Math.max(0, receivedCount - upsertedCount);
        log.info("[MarketDataFoundation] 导入完成: type={}, source={}, version={}, received={}, upserted={}, skipped={}",
                dataType, source, sourceVersion, receivedCount, upsertedCount, skippedCount);
        return MarketDataImportResultDto.builder()
                .dataType(dataType)
                .source(source)
                .sourceVersion(sourceVersion)
                .receivedCount(receivedCount)
                .upsertedCount(upsertedCount)
                .skippedCount(skippedCount)
                .build();
    }
}
// AI_GENERATE_END -----
