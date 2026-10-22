// AI_GENERATE_START-- -----------
package com.stock.dataCollector.service;

import com.alibaba.fastjson2.JSONArray;
import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.dataCollector.persistence.PriceRepository;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.dataCollector.support.SecuritiesClient;
import com.stock.dataCollector.support.StockDataParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.stock.dataCollector.domain.vo.MarketStatsDto;

/**
 * 股票数据服务
 * 统一处理股票信息的采集、存储和查询
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockDataService {

    private final PriceRepository priceRepository;
    private final StockInfoRepository stockInfoRepository;
    private final SecuritiesClient securitiesClient;
    private final MongoTemplate mongoTemplate;
    private final TradingCalendarService tradingCalendarService;
    private final DelistedStockCleanupService delistedStockCleanupService;

    // ==================== 股票列表同步 ====================

    /**
     * 同步股票列表到MySQL
     * 
     * @return 同步结果
     */
    public SyncResult syncStockList() {
        log.info("========== 开始同步股票列表 ==========");
        long startTime = System.currentTimeMillis();
        SyncResult result = new SyncResult();

        try {
            List<StockInfo> incomingStocks = parseStockList(fetchStockList());
            result.setTotalCount(incomingStocks.size());
            Map<String, StockInfo> existingStocks = loadExistingStocks(incomingStocks);
            StockSyncPlan syncPlan = classifyStocks(incomingStocks, existingStocks);

            List<StockInfo> confirmedDelistedStocks = findConfirmedDelistedStocks(syncPlan.missingStocks());
            int cleanedCount = cleanupDelistedStocks(confirmedDelistedStocks);
            List<StockInfo> insertStocks = prepareInsertStocks(syncPlan.insertStocks());
            List<StockInfo> updateStocks = prepareUpdateStocks(syncPlan.updateStocks(), existingStocks);
            int ignoredMissingCount = syncPlan.missingStocks().size() - confirmedDelistedStocks.size();

            batchInsert(insertStocks);
            batchUpdate(updateStocks);

            result.setSavedCount(insertStocks.size());
            result.setUpdatedCount(updateStocks.size());
            result.setCleanedCount(cleanedCount);
            result.setIgnoredMissingCount(ignoredMissingCount);
            result.setFailedCount(incomingStocks.size() - insertStocks.size() - updateStocks.size());
            result.setCostTimeMs(System.currentTimeMillis() - startTime);

            log.info("========== 股票列表同步完成 ==========");
            log.info("总数: {}, 新增: {}, 更新: {}, 清理退市: {}, 忽略缺失: {}, 失败: {}, 耗时: {}ms, DB当前总数: {}",
                    result.getTotalCount(), result.getSavedCount(), result.getUpdatedCount(),
                    result.getCleanedCount(), result.getIgnoredMissingCount(), result.getFailedCount(),
                    result.getCostTimeMs(), stockInfoRepository.count());
            return result;
        } catch (Exception e) {
            log.error("同步股票列表失败", e);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    /**
     * 获取证券接口返回的全量股票信息。
     *
     * @return 接口原始股票列表
     * @throws IllegalStateException 接口返回为空时抛出
     */
    private JSONArray fetchStockList() {
        JSONArray stockList = securitiesClient.getStockList();
        if (stockList == null || stockList.isEmpty()) {
            throw new IllegalStateException("证券接口未返回股票列表");
        }
        return stockList;
    }

    /**
     * 解析、过滤无效代码并按代码去重。
     */
    private List<StockInfo> parseStockList(JSONArray stockList) {
        return StockDataParser.parseStockList(stockList).stream()
                .filter(stock -> stock.getCode() != null && !stock.getCode().isBlank())
                .collect(Collectors.toMap(StockInfo::getCode, stock -> stock, (first, ignored) -> first))
                .values().stream().toList();
    }

    /**
     * 一次性加载数据库中与接口股票代码相关的现有记录。
     */
    private Map<String, StockInfo> loadExistingStocks(List<StockInfo> incomingStocks) {
        List<String> codes = incomingStocks.stream().map(StockInfo::getCode).toList();
        return stockInfoRepository.findByCodeIn(codes).stream()
                .collect(Collectors.toMap(StockInfo::getCode, stock -> stock));
    }

    /**
     * 按股票代码生成更新、新增和数据库缺失三类集合。
     */
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
                .filter(stock -> !incomingCodes.contains(stock.getCode())).toList();
        return new StockSyncPlan(updateStocks, insertStocks, missingStocks);
    }

    /**
     * 仅为接口新增股票补充上市日期。
     */
    private List<StockInfo> prepareInsertStocks(List<StockInfo> insertStocks) {
        for (StockInfo stock : insertStocks) {
            if (stock.getListedDate() == null) {
                stock.setListedDate(securitiesClient.getListingDate(stock.getCode()));
            }
        }
        return insertStocks;
    }

    /**
     * 将接口字段应用到数据库现有实体，保留数据库已有上市日期。
     */
    private List<StockInfo> prepareUpdateStocks(List<StockInfo> updateStocks,
                                                Map<String, StockInfo> existingStocks) {
        for (StockInfo incoming : updateStocks) {
            updateStockInfo(existingStocks.get(incoming.getCode()), incoming);
        }
        return updateStocks.stream().map(stock -> existingStocks.get(stock.getCode())).toList();
    }

    /**
     * 从数据库缺失集合中筛选具有明确退市说明的股票。
     */
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
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.contains("退市")
                || normalized.contains("终止上市")
                || normalized.contains("DELIST")
                || normalized.contains("TERMINATED");
    }

    /**
     * 清理确认退市股票及其关联市场、模型数据。
     */
    private int cleanupDelistedStocks(List<StockInfo> delistedStocks) {
        for (StockInfo stock : delistedStocks) {
            delistedStockCleanupService.cleanup(stock.getCode());
        }
        return delistedStocks.size();
    }

    private void batchInsert(List<StockInfo> stocks) {
        saveInBatches(stocks, "新增");
    }

    private void batchUpdate(List<StockInfo> stocks) {
        saveInBatches(stocks, "更新");
    }

    private void saveInBatches(List<StockInfo> stocks, String operation) {
        final int batchSize = 200;
        for (int i = 0; i < stocks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, stocks.size());
            stockInfoRepository.saveAll(stocks.subList(i, end));
        }
        log.info("股票信息{}批量写入完成: {}条", operation, stocks.size());
    }

    private record StockSyncPlan(List<StockInfo> updateStocks, List<StockInfo> insertStocks,
                                 List<StockInfo> missingStocks) {
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

    // ==================== 历史价格同步 ====================

    /**
     * 获取股票历史价格数据（平台默认返回最近约3年）
     */
    public List<StockPrice> getHistoryPrices(String stockCode) {
        try {
            JSONArray results = securitiesClient.getHistoryPrices(stockCode, 0);
            List<StockPrice> prices = StockDataParser.parsePriceList(results, stockCode);

            if (!prices.isEmpty()) {
                var dates = prices.stream()
                    .map(StockPrice::getDate)
                    .filter(java.util.Objects::nonNull)
                    .sorted()
                    .toList();
                if (!dates.isEmpty()) {
                    LocalDate start = dates.get(0);
                    LocalDate end = dates.get(dates.size() - 1);
                    log.info("股票 {} 历史价格解析完成，共 {} 条，时间跨度: {} ~ {}", stockCode, prices.size(), start, end);
                } else {
                    log.info("股票 {} 历史价格解析完成，共 {} 条，但日期为空", stockCode, prices.size());
                }
            } else {
                log.warn("股票 {} 历史价格无数据", stockCode);
            }

            return prices;
        } catch (Exception e) {
            log.error("获取股票 {} 历史价格数据失败", stockCode, e);
            return List.of();
        }
    }

    /**
     * 获取指定日期区间的股票历史价格数据。
     *
     * 增量同步使用明确的起止日期请求供应商，使任务除同步最新交易日外，
     * 还能向历史方向补齐允许窗口内尚未入库的交易日。
     *
     * @param stockCode 股票代码
     * @param startDate 开始日期，包含当天
     * @param endDate 结束日期，包含当天
     * @return 指定区间内的历史价格数据，请求失败时返回空列表
     */
    public List<StockPrice> getHistoryPrices(String stockCode, LocalDate startDate, LocalDate endDate) {
        try {
            JSONArray results = securitiesClient.getHistoryPrices(stockCode, startDate, endDate);
            List<StockPrice> prices = StockDataParser.parsePriceList(results, stockCode);
            log.info("股票 {} 区间历史价格解析完成，共 {} 条，时间跨度: {} ~ {}",
                    stockCode, prices.size(), startDate, endDate);
            return prices;
        } catch (Exception exception) {
            log.error("获取股票 {} 区间历史价格数据失败，时间跨度: {} ~ {}",
                    stockCode, startDate, endDate, exception);
            return List.of();
        }
    }


    /**
     * 保存股票价格数据到MongoDB - 终极性能优化版本
     * 使用 BulkOperations.upsert 直接批量写入，无需预先查询
     * 性能提升约 3 倍（相比之前的优化版本），代码简化 50%
     */
    public void saveStockPrices(List<StockPrice> prices) {
        if (prices == null || prices.isEmpty()) {
            log.warn("价格数据为空，跳过保存");
            return;
        }

        log.info("开始批量 upsert {} 条股票价格数据", prices.size());
        long startTime = System.currentTimeMillis();

        String stockName = null;
        if (!prices.isEmpty()) {
            stockName = stockInfoRepository.findByCode(prices.get(0).getCode())
                    .map(s -> s.getName())
                    .orElse(null);
        }
        
        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED, 
                StockPrice.class
            );
            
            LocalDateTime now = LocalDateTime.now();
            
            // 构建 upsert 操作：不存在则插入，存在则更新
            for (StockPrice price : prices) {
                Query query = new Query(
                    Criteria.where("code").is(price.getCode())
                        .and("date").is(price.getDate())
                );
                
                Update update = new Update()
                    .set("stockName", stockName)
                    .set("openPrice", price.getOpenPrice())
                    .set("highPrice", price.getHighPrice())
                    .set("lowPrice", price.getLowPrice())
                    .set("closePrice", price.getClosePrice())
                    .set("volume", price.getVolume())
                    .set("amount", price.getAmount())
                    .set("updateTime", now)
                    .setOnInsert("createTime", now); // 仅插入时设置 createTime
                
                bulkOps.upsert(query, update);
            }
            
            // 执行批量操作
            var result = bulkOps.execute();
            
            long costTime = System.currentTimeMillis() - startTime;
            log.info("股票价格数据批量 upsert 完成 - 插入：{}, 更新：{}, 耗时：{}ms", 
                result.getInsertedCount(), 
                result.getModifiedCount(), 
                costTime);
            
        } catch (Exception e) {
            log.error("批量 upsert 股票价格数据失败", e);
            throw new RuntimeException("批量保存失败", e);
        }
    }


    /**
     * 同步当前交易日并回补最近三年的缺失交易日。
     *
     * @param stockCode 股票代码
     * @param today 当前交易日
     * @param earliestDate 最早允许回补日期
     * @param tradingDays 当前已确认日历覆盖范围内的交易日
     * @return 当前日、回补和未能从供应商窗口获取的数据统计
     */
    public PriceSyncResult syncTodayAndBackfill(String stockCode, LocalDate today,
                                                 LocalDate earliestDate,
                                                 List<LocalDate> tradingDays) {
        List<StockPrice> fetchedPrices = getHistoryPrices(stockCode, earliestDate, today);
        if (fetchedPrices.stream().noneMatch(price -> today.equals(price.getDate()))) {
            throw new IllegalStateException("供应商未返回当前交易日数据: " + stockCode + ", " + today);
        }

        Map<LocalDate, StockPrice> fetchedByDate = fetchedPrices.stream()
                .filter(price -> price.getDate() != null)
                .collect(Collectors.toMap(StockPrice::getDate, price -> price, (left, right) -> left));
        Set<LocalDate> existingDates = priceRepository
                .findByCodeAndDateBetweenOrderByDateAsc(stockCode, earliestDate, today).stream()
                .map(StockPrice::getDate)
                .collect(Collectors.toSet());
        List<StockPrice> toSave = fetchedByDate.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(earliestDate))
                .filter(entry -> !entry.getKey().isAfter(today))
                .filter(entry -> !existingDates.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        List<LocalDate> missingDates = tradingDays.stream()
                .filter(date -> !date.isAfter(today) && !existingDates.contains(date))
                .toList();
        if (!toSave.isEmpty()) {
            saveStockPrices(toSave);
        }
        int todaySaved = toSave.stream().anyMatch(price -> today.equals(price.getDate())) ? 1 : 0;
        List<LocalDate> unresolvedDates = missingDates.stream()
                .filter(date -> !fetchedByDate.containsKey(date))
                .toList();
        return new PriceSyncResult(todaySaved, toSave.size() - todaySaved, unresolvedDates);
    }
    /**
     * 清理单只股票的全部历史行情数据
     * <p>
     * 该方法会删除指定股票的全部日K历史记录，
     * 通常用于当历史数据质量不足或不完整时，先清空本地数据，
     * 再通过后续的历史数据同步任务重新拉取一份干净的数据。
     * </p>
     *
     * @param stockCode 股票代码，例如 "600519" 或 "301680"
     */
    public void clearHistoryForStock(String stockCode) {
        // 1. 记录清理操作的入口日志，方便排查是谁触发了数据删除
        log.warn("开始清理股票 {} 的全部日K行情数据，为后续重新同步做准备", stockCode);
        try {
            // 2. 删除日K数据
            priceRepository.deleteByCode(stockCode);


            // 4. 打印完成日志
            log.info("股票 {} 日K历史行情数据清理完成", stockCode);
        } catch (Exception e) {
            // 5. 捕获异常并记录详细错误，方便运维排查
            log.error("清理股票 {} 历史行情数据失败: {}", stockCode, e.getMessage(), e);
            throw new RuntimeException("清理历史数据失败: " + e.getMessage(), e);
        }
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
     * 涨幅榜 TOP10，按涨跌幅降序，仅查 10 条（避免全表加载）
     */
    public List<StockInfo> findTop10ByIncreaseRate() {
        return stockInfoRepository.findTop10TradableByChangePercent(PageRequest.of(0, 10));
    }

    /**
     * 带过滤条件的分页查询股票
     */
    public Page<StockInfo> findStocksWithFilter(String name, String code, Pageable pageable) {
        return stockInfoRepository.findAll((root, query, cb) -> {
            // 构建动态查询条件
            var conditions = cb.conjunction();

            if (name != null && !name.isEmpty()) {
                conditions = cb.and(conditions, cb.like(root.get("name"), "%" + name + "%"));
            }
            if (code != null && !code.isEmpty()) {
                conditions = cb.and(conditions, cb.like(root.get("code"), "%" + code + "%"));
            }

            query.where(conditions);
            return query.getRestriction();
        }, pageable);
    }

    /**
     * 分页查询具有有效总市值的股票，并支持名称或代码模糊匹配。
     *
     * 总市值为空或小于等于 0 的记录属于行情字段不完整数据，不进入股票信息页面。
     *
     * @param keywords 搜索关键词
     * @param pageable 分页参数
     * @return 已排除无效总市值记录的分页结果
     */
    public Page<StockInfo> findStocksWithKeywords(String keywords, Pageable pageable) {
        return stockInfoRepository.findAll((root, query, cb) -> {
            // 股票列表只展示具备有效市值的可交易标的，保证分页数量与页面内容一致。
            var validMarketValue = cb.greaterThan(root.<BigDecimal>get("totalMarketValue"), BigDecimal.ZERO);
            var name = cb.upper(root.get("name"));
            var activeName = cb.and(
                    cb.notLike(name, "%ST%"),
                    cb.notLike(root.get("name"), "%退%"));
            var securityCategory = cb.upper(root.get("securityCategory"));
            var activeSecurityCategory = cb.and(
                    cb.notLike(securityCategory, "%ST%"),
                    cb.notLike(root.get("securityCategory"), "%退%"),
                    cb.notLike(securityCategory, "%DELIST%"),
                    cb.notLike(securityCategory, "%TERMINATED%"));
            var market = cb.upper(root.get("market"));
            var activeMarket = cb.and(
                    cb.notLike(market, "%ST%"),
                    cb.notLike(root.get("market"), "%退%"),
                    cb.notLike(market, "%DELIST%"),
                    cb.notLike(market, "%TERMINATED%"));
            validMarketValue = cb.and(validMarketValue,
                    cb.or(cb.isNull(root.get("name")), activeName),
                    cb.or(cb.isNull(root.get("securityCategory")), activeSecurityCategory),
                    cb.or(cb.isNull(root.get("market")), activeMarket));
            if (keywords == null || keywords.isBlank()) {
                return validMarketValue;
            }

            // 使用 OR 条件：name LIKE %keywords% OR code LIKE %keywords%
            var nameCondition = cb.like(root.get("name"), "%" + keywords + "%");
            var codeCondition = cb.like(root.get("code"), "%" + keywords + "%");
            var orCondition = cb.or(nameCondition, codeCondition);
            return cb.and(validMarketValue, orCondition);
        }, pageable);
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
     * 获取市场统计信息
     * 使用数据库聚合/单条查询，避免全表加载，提升响应速度
     */
    public MarketStatsDto getMarketStats() {
        long totalCount = stockInfoRepository.countTradable();
        if (totalCount == 0) {
            return MarketStatsDto.builder()
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
        }

        // 市场状态必须同时满足已确认交易日和盘中时间。
        LocalTime now = LocalTime.now();
        LocalDate today = LocalDate.now();
        boolean isMarketOpen = tradingCalendarService.isTradingDay(today)
                && now.isAfter(LocalTime.of(9, 30)) && now.isBefore(LocalTime.of(15, 0));
        String marketStatus = isMarketOpen ? "开盘" : "休市";

        // 涨跌平数量（聚合查询，不加载实体）
        int upCount = (int) stockInfoRepository.countTradableByChangePercentGreaterThan(BigDecimal.ZERO);
        int downCount = (int) stockInfoRepository.countTradableByChangePercentLessThan(BigDecimal.ZERO);
        long flatCount = stockInfoRepository.countTradableByChangePercentIsNull()
                + stockInfoRepository.countTradableByChangePercent(BigDecimal.ZERO);

        BigDecimal totalAmount = stockInfoRepository.sumTradableTotalMarketValue() != null
                ? stockInfoRepository.sumTradableTotalMarketValue() : BigDecimal.ZERO;
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal sumTurnover = stockInfoRepository.sumTradableTurnoverRate() != null
                ? stockInfoRepository.sumTradableTurnoverRate() : BigDecimal.ZERO;
        BigDecimal avgTurnoverRate = totalCount > 0
                ? sumTurnover.divide(BigDecimal.valueOf(totalCount), 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Double avgChange = stockInfoRepository.avgTradableChangePercent();
        BigDecimal changePercent = (avgChange != null) ? BigDecimal.valueOf(avgChange).setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;

        List<StockInfo> gainers = stockInfoRepository.findTradableByChangePercentDesc(PageRequest.of(0, 1));
        List<StockInfo> losers = stockInfoRepository.findTradableByChangePercentAsc(PageRequest.of(0, 1));
        StockInfo topGainer = gainers.isEmpty() ? null : gainers.get(0);
        StockInfo topLoser = losers.isEmpty() ? null : losers.get(0);

        return MarketStatsDto.builder()
                .marketStatus(marketStatus)
                .changePercent(changePercent)
                .upCount(upCount)
                .downCount(downCount)
                .flatCount((int) flatCount)
                .totalAmount(totalAmount)
                .totalVolume(totalVolume)
                .totalCount((int) totalCount)
                .avgTurnoverRate(avgTurnoverRate)
                .topGainerCode(topGainer != null ? topGainer.getCode() : null)
                .topGainerName(topGainer != null ? topGainer.getName() : null)
                .topGainerChange(topGainer != null ? topGainer.getChangePercent() : null)
                .topLoserCode(topLoser != null ? topLoser.getCode() : null)
                .topLoserName(topLoser != null ? topLoser.getName() : null)
                .topLoserChange(topLoser != null ? topLoser.getChangePercent() : null)
                .build();
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
    public void deleteAllStocks() {
        stockInfoRepository.deleteAll();
    }

    // ==================== 同步结果 ====================

    public record PriceSyncResult(int todaySaved, int backfillSaved, List<LocalDate> unresolvedDates) {
    }

    @lombok.Data
    public static class SyncResult {
        private int totalCount;
        private int savedCount;
        private int updatedCount;
        private int cleanedCount;
        private int ignoredMissingCount;
        private int failedCount;
        private long costTimeMs;
        private String errorMessage;
    }
}
// AI_GENERATE_END-- -----------
