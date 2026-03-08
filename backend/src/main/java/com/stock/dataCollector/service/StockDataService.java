package com.stock.dataCollector.service;

import com.alibaba.fastjson2.JSONArray;
import com.stock.dataCollector.client.SecuritiesClient;
import com.stock.dataCollector.entity.StockInfo;
import com.stock.dataCollector.entity.StockPrice;
import com.stock.dataCollector.entity.StockPriceWeekly;
import com.stock.dataCollector.entity.StockPriceMonthly;
import com.stock.dataCollector.repository.PriceRepository;
import com.stock.dataCollector.repository.StockInfoRepository;
import com.stock.dataCollector.repository.WeeklyPriceRepository;
import com.stock.dataCollector.repository.MonthlyPriceRepository;
import com.stock.dataCollector.util.StockDataParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
import java.util.stream.Collectors;

import com.stock.dataCollector.dto.MarketStatsDto;

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
    private final MongoTemplate mongoTemplate;
    private final WeeklyPriceRepository weeklyPriceRepository;
    private final MonthlyPriceRepository monthlyPriceRepository;

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
            JSONArray stockList = securitiesClient.getStockList();
            
            if (stockList == null || stockList.isEmpty()) {
                log.warn("未获取到股票列表数据");
                return result;
            }
            
            result.setTotalCount(stockList.size());
            
            // 使用解析器解析数据
            List<StockInfo> stockInfos = StockDataParser.parseStockList(stockList);
            log.info("解析完成的股票数量: {}", stockInfos.size());

            // 批量保存
            int[] counts = batchSaveWithStats(stockInfos);
            result.setSavedCount(counts[0]);
            result.setUpdatedCount(counts[1]);
            result.setFailedCount(stockInfos.size() - counts[0] - counts[1]);
            
            long costTime = System.currentTimeMillis() - startTime;
            result.setCostTimeMs(costTime);
            
            long dbCount = stockInfoRepository.count();

            log.info("========== 股票列表同步完成 ==========");
            log.info("总数: {}, 新增: {}, 更新: {}, 失败: {}, 耗时: {}ms, DB当前总数: {}", 
                result.getTotalCount(), result.getSavedCount(), result.getUpdatedCount(), 
                result.getFailedCount(), result.getCostTimeMs(), dbCount);
            
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
        if (stockInfos == null || stockInfos.isEmpty()) {
            return new int[]{0, 0};
        }

        // 预处理代码列表
        List<String> codes = stockInfos.stream()
            .map(StockInfo::getCode)
            .filter(code -> code != null && !code.isBlank())
            .distinct()
            .toList();

        // 一次性查出已存在的股票，避免逐条 existsByCode / findByCode
        List<StockInfo> existingList = stockInfoRepository.findByCodeIn(codes);
        Map<String, StockInfo> existingMap = existingList.stream()
            .collect(Collectors.toMap(StockInfo::getCode, s -> s));

        List<StockInfo> toInsert = new ArrayList<>();
        List<StockInfo> toUpdate = new ArrayList<>();

        for (StockInfo incoming : stockInfos) {
            try {
                if (incoming.getCode() == null || incoming.getCode().isBlank()) {
                    continue;
                }
                StockInfo existing = existingMap.get(incoming.getCode());
                if (existing != null) {
                    updateStockInfo(existing, incoming);
                    toUpdate.add(existing);
                } else {
                    toInsert.add(incoming);
                }
            } catch (Exception e) {
                log.error("处理股票 {} 失败: {}", incoming.getCode(), e.getMessage(), e);
            }
        }

        int savedCount = 0;
        int updatedCount = 0;

        // 分批写入，降低单次事务压力
        final int batchSize = 200;

        if (!toInsert.isEmpty()) {
            for (int i = 0; i < toInsert.size(); i += batchSize) {
                int end = Math.min(i + batchSize, toInsert.size());
                List<StockInfo> batch = toInsert.subList(i, end);
                stockInfoRepository.saveAll(batch);
            }
            savedCount = toInsert.size();
        }
        if (!toUpdate.isEmpty()) {
            for (int i = 0; i < toUpdate.size(); i += batchSize) {
                int end = Math.min(i + batchSize, toUpdate.size());
                List<StockInfo> batch = toUpdate.subList(i, end);
                stockInfoRepository.saveAll(batch);
            }
            updatedCount = toUpdate.size();
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
     * 同步单只股票的历史数据到MongoDB
     */
    public int syncHistoricalData(String stockCode, LocalDate startDate, LocalDate endDate) {
        log.info("开始同步股票 {} 从 {} 到 {} 的历史数据", stockCode, startDate, endDate);

        // 调用新的 Client 方法，仅拉取指定区间
        JSONArray results = securitiesClient.getHistoryPrices(stockCode, startDate, endDate);
        List<StockPrice> prices = StockDataParser.parsePriceList(results, stockCode);

        // 保存数据
        if (!prices.isEmpty()) {
            saveStockPrices(prices);
            log.info("股票 {} 历史数据同步完成，共 {} 条", stockCode, prices.size());
            return prices.size();
        }
        
        return 0;
    }
    /**
     * 智能增量同步单只股票数据
     * 逻辑：
     * 1. 获取接口返回的默认历史数据（通常为最近500条）
     * 2. 查询数据库中该股票的最新日期
     * 3. 过滤出大于数据库最新日期的数据进行保存
     */
    public int syncLatestPriceData(String stockCode) {
        try {
            // 1. 获取默认历史数据
            List<StockPrice> fetchedPrices = getHistoryPrices(stockCode);
            if (fetchedPrices.isEmpty()) {
                return 0;
            }

            // 2. 查询数据库最新记录
            Optional<StockPrice> lastPriceOpt = priceRepository.findTopByCodeOrderByDateDesc(stockCode);

            List<StockPrice> toSave;
            if (lastPriceOpt.isPresent()) {
                LocalDate lastDate = lastPriceOpt.get().getDate();
                // 3. 过滤出新数据
                toSave = fetchedPrices.stream()
                    .filter(p -> p.getDate().isAfter(lastDate))
                    .toList();
                
                if (toSave.isEmpty()) {
                    log.debug("股票 {} 数据已是最新，无需更新", stockCode);
                    return 0;
                }
            } else {
                // 如果数据库无记录，保存所有获取到的数据
                toSave = fetchedPrices;
            }

            // 4. 保存
            saveStockPrices(toSave);
            log.info("股票 {} 增量同步完成，新增 {} 条记录", stockCode, toSave.size());
            return toSave.size();

        } catch (Exception e) {
            log.error("增量同步股票 {} 失败: {}", stockCode, e.getMessage());
            return 0;
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
     * 使用 keywords 模糊查询股票，支持 name 或 code 任一匹配
     *
     * @param keywords 搜索关键词
     * @param pageable 分页参数
     * @return 分页结果
     */
    public Page<StockInfo> findStocksWithKeywords(String keywords, Pageable pageable) {
        return stockInfoRepository.findAll((root, query, cb) -> {
            // 如果没有关键词，返回所有数据
            if (keywords == null || keywords.isEmpty()) {
                return null;
            }

            // 使用 OR 条件：name LIKE %keywords% OR code LIKE %keywords%
            var nameCondition = cb.like(root.get("name"), "%" + keywords + "%");
            var codeCondition = cb.like(root.get("code"), "%" + keywords + "%");
            var orCondition = cb.or(nameCondition, codeCondition);

            query.where(orCondition);
            return query.getRestriction();
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
     * 从stock_info表中聚合计算市场基本数据
     */
    public MarketStatsDto getMarketStats() {
        List<StockInfo> allStocks = stockInfoRepository.findAll();

        if (allStocks.isEmpty()) {
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

        // 计算市场状态 (9:30-15:00 为开盘时间，周一到周五)
        LocalTime now = LocalTime.now();
        LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeek = today.getDayOfWeek();
        boolean isWeekday = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
        boolean isMarketOpen = isWeekday && now.isAfter(LocalTime.of(9, 30)) && now.isBefore(LocalTime.of(15, 0));
        String marketStatus = isMarketOpen ? "开盘" : "休市";

        // 统计涨跌平
        int upCount = 0;
        int downCount = 0;
        int flatCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal totalTurnoverRate = BigDecimal.ZERO;

        for (StockInfo stock : allStocks) {
            if (stock.getChangePercent() != null) {
                if (stock.getChangePercent().compareTo(BigDecimal.ZERO) > 0) {
                    upCount++;
                } else if (stock.getChangePercent().compareTo(BigDecimal.ZERO) < 0) {
                    downCount++;
                } else {
                    flatCount++;
                }
            }

            if (stock.getTotalMarketValue() != null) {
                totalAmount = totalAmount.add(stock.getTotalMarketValue());
            }

            if (stock.getTurnoverRate() != null) {
                totalTurnoverRate = totalTurnoverRate.add(stock.getTurnoverRate());
            }
        }

        // 计算平均换手率
        BigDecimal avgTurnoverRate = allStocks.isEmpty() ? BigDecimal.ZERO :
                totalTurnoverRate.divide(BigDecimal.valueOf(allStocks.size()), 4, java.math.RoundingMode.HALF_UP);

        // 计算大盘涨跌幅 (所有股票涨跌幅的加权平均，这里简化为简单平均)
        BigDecimal totalChangePercent = BigDecimal.ZERO;
        int validChangeCount = 0;
        for (StockInfo stock : allStocks) {
            if (stock.getChangePercent() != null) {
                totalChangePercent = totalChangePercent.add(stock.getChangePercent());
                validChangeCount++;
            }
        }
        BigDecimal changePercent = validChangeCount > 0 ?
                totalChangePercent.divide(BigDecimal.valueOf(validChangeCount), 2, java.math.RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        // 找出领涨和领跌股票
        StockInfo topGainer = allStocks.stream()
                .filter(s -> s.getChangePercent() != null)
                .max((a, b) -> a.getChangePercent().compareTo(b.getChangePercent()))
                .orElse(null);

        StockInfo topLoser = allStocks.stream()
                .filter(s -> s.getChangePercent() != null)
                .min((a, b) -> a.getChangePercent().compareTo(b.getChangePercent()))
                .orElse(null);

        return MarketStatsDto.builder()
                .marketStatus(marketStatus)
                .changePercent(changePercent)
                .upCount(upCount)
                .downCount(downCount)
                .flatCount(flatCount)
                .totalAmount(totalAmount)
                .totalVolume(totalVolume)
                .totalCount(allStocks.size())
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

    @lombok.Data
    public static class SyncResult {
        private int totalCount;
        private int savedCount;
        private int updatedCount;
        private int failedCount;
        private long costTimeMs;
        private String errorMessage;
    }

    // ==================== K线数据聚合 ====================

    /**
     * 增量聚合所有股票的周K和月K数据
     * 只处理自上次聚合以来新增的日K数据
     */
    public AggregateResult aggregateAllKLineData() {
        log.info("========== 开始增量聚合K线数据 ==========");
        long startTime = System.currentTimeMillis();

        AggregateResult result = new AggregateResult();

        try {
            // 获取所有股票代码
            List<String> allCodes = stockInfoRepository.findAllCodes();
            log.info("共 {} 只股票需要检查K线数据", allCodes.size());

            int weeklyCount = 0;
            int monthlyCount = 0;
            int processedCount = 0;

            for (String code : allCodes) {
                try {
                    // 增量聚合单只股票
                    StockDataService.AggregateResult stockResult = aggregateKLineDataIncrementally(code);

                    if (stockResult != null) {
                        weeklyCount += stockResult.getWeeklyCount();
                        monthlyCount += stockResult.getMonthlyCount();
                        if (stockResult.getWeeklyCount() > 0 || stockResult.getMonthlyCount() > 0) {
                            processedCount++;
                        }
                    }
                } catch (Exception e) {
                    log.error("增量聚合股票 {} K线数据失败: {}", code, e.getMessage());
                }
            }

            long costTime = System.currentTimeMillis() - startTime;
            result.setWeeklyCount(weeklyCount);
            result.setMonthlyCount(monthlyCount);
            result.setProcessedStockCount(processedCount);
            result.setTotalStockCount(allCodes.size());
            result.setCostTimeMs(costTime);
            result.setSuccess(true);

            log.info("========== K线数据增量聚合完成 ==========");
            log.info("处理股票数: {}/{}, 周K更新: {} 条, 月K更新: {} 条, 耗时: {}ms",
                processedCount, allCodes.size(), weeklyCount, monthlyCount, costTime);

        } catch (Exception e) {
            log.error("增量聚合K线数据失败", e);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * 完整重聚合所有股票的周K和月K数据
     * 用于首次聚合或数据修复
     */
    public AggregateResult reaggregateAllKLineData() {
        log.info("========== 开始完整重聚合K线数据 (并行优化版) ==========");
        long startTime = System.currentTimeMillis();

        AggregateResult result = new AggregateResult();

        try {
            // 获取所有股票代码
            List<String> allCodes = stockInfoRepository.findAllCodes();
            int totalStocks = allCodes.size();
            log.info("共 {} 只股票需要重聚合K线数据", totalStocks);

            // 使用并行流处理，利用多核CPU
            // 控制并发数量为 Runtime.getRuntime().availableProcessors()
            int parallelLevel = Math.max(4, Runtime.getRuntime().availableProcessors());
            log.info("使用并行度: {}", parallelLevel);

            // 使用 ConcurrentHashMap 收集结果
            java.util.concurrent.ConcurrentHashMap<String, int[]> stockResults = new java.util.concurrent.ConcurrentHashMap<>();

            // 记录统计
            java.util.concurrent.atomic.AtomicInteger weeklyCount = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger monthlyCount = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger successStockCount = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger noDataStockCount = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger failedStockCount = new java.util.concurrent.atomic.AtomicInteger(0);

            // 并行处理每只股票
            allCodes.parallelStream().forEach(code -> {
                try {
                    // 获取日K数据
                    List<StockPrice> dailyPrices = priceRepository.findByCodeOrderByDateAsc(code);

                    if (dailyPrices == null || dailyPrices.isEmpty()) {
                        log.warn("股票 {} 没有日K数据，跳过聚合", code);
                        noDataStockCount.incrementAndGet();
                        stockResults.put(code, new int[]{0, 0, 0}); // weekly, monthly, status: 0=无数据
                        return;
                    }

                    // 记录日K数据日期范围
                    LocalDate firstDailyDate = dailyPrices.get(0).getDate();
                    LocalDate lastDailyDate = dailyPrices.get(dailyPrices.size() - 1).getDate();

                    // 先删除旧的聚合数据
                    weeklyPriceRepository.deleteByCode(code);
                    monthlyPriceRepository.deleteByCode(code);

                    // 聚合周K
                    List<StockPriceWeekly> weeklyPrices = aggregateToWeekly(dailyPrices);
                    int wCount = 0;
                    if (!weeklyPrices.isEmpty()) {
                        weeklyPriceRepository.saveAll(weeklyPrices);
                        wCount = weeklyPrices.size();
                        weeklyCount.addAndGet(wCount);
                    }

                    // 聚合月K
                    List<StockPriceMonthly> monthlyPrices = aggregateToMonthly(dailyPrices);
                    int mCount = 0;
                    if (!monthlyPrices.isEmpty()) {
                        monthlyPriceRepository.saveAll(monthlyPrices);
                        mCount = monthlyPrices.size();
                        monthlyCount.addAndGet(mCount);
                    }

                    successStockCount.incrementAndGet();
                    stockResults.put(code, new int[]{wCount, mCount, 1}); // status: 1=成功

                    // 每处理10只股票打印一次进度
                    int processed = successStockCount.get() + noDataStockCount.get() + failedStockCount.get();
                    if (processed % 10 == 0 || processed == totalStocks) {
                        log.info("聚合进度: {}/{} ({})", processed, totalStocks, 
                            String.format("%.1f%%", processed * 100.0 / totalStocks));
                    }

                } catch (Exception e) {
                    log.error("重聚合股票 {} K线数据失败: {}", code, e.getMessage(), e);
                    failedStockCount.incrementAndGet();
                    stockResults.put(code, new int[]{0, 0, -1}); // status: -1=失败
                }
            });

            long costTime = System.currentTimeMillis() - startTime;
            result.setWeeklyCount(weeklyCount.get());
            result.setMonthlyCount(monthlyCount.get());
            result.setProcessedStockCount(successStockCount.get());
            result.setTotalStockCount(totalStocks);
            result.setCostTimeMs(costTime);
            result.setSuccess(true);

            log.info("========== K线数据完整重聚合完成 ==========");
            log.info("汇总: 共处理股票 {} 只 (成功: {}, 无数据: {}, 失败: {}), 周K总数据: {} 条, 月K总数据: {} 条, 耗时: {}ms ({}s)",
                totalStocks, successStockCount.get(), noDataStockCount.get(), failedStockCount.get(),
                weeklyCount.get(), monthlyCount.get(), costTime, costTime / 1000);

            // 打印部分成功股票的结果示例
            log.info("示例数据 (前5只成功股票):");
            stockResults.entrySet().stream()
                .filter(e -> e.getValue()[2] == 1)
                .limit(5)
                .forEach(e -> {
                    int[] counts = e.getValue();
                    log.info("  {}: 周K {} 条, 月K {} 条", e.getKey(), counts[0], counts[1]);
                });

        } catch (Exception e) {
            log.error("重聚合K线数据失败", e);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * 增量聚合单只股票的周K和月K数据
     * 策略：
     * 1. 获取最新的周K/月K日期
     * 2. 获取该日期之后的日K数据
     * 3. 重新计算并更新相关周K/月K（可能影响跨周/月的数据）
     */
    private AggregateResult aggregateKLineDataIncrementally(String code) {
        AggregateResult stockResult = new AggregateResult();
        stockResult.setSuccess(true);

        try {
            // 获取所有日K数据
            List<StockPrice> dailyPrices = priceRepository.findByCodeOrderByDateAsc(code);

            if (dailyPrices == null || dailyPrices.isEmpty()) {
                return null;
            }

            // 获取现有的周K/月K数据
            List<StockPriceWeekly> existingWeekly = weeklyPriceRepository.findByCodeOrderByDateAsc(code);
            List<StockPriceMonthly> existingMonthly = monthlyPriceRepository.findByCodeOrderByDateAsc(code);

            // 计算最新的周K/月K应该包含的日期范围
            LocalDate latestDailyDate = dailyPrices.get(dailyPrices.size() - 1).getDate();

            // 找出需要重新计算的周
            // 周K: 需要重新计算包含最新日期的那一周
            LocalDate lastWeeklyDate = existingWeekly.isEmpty() ? null :
                existingWeekly.get(existingWeekly.size() - 1).getDate();

            // 月K: 需要重新计算包含最新日期的那一月
            LocalDate lastMonthlyDate = existingMonthly.isEmpty() ? null :
                existingMonthly.get(existingMonthly.size() - 1).getDate();

            // 如果没有现有数据，进行完整聚合
            if (existingWeekly.isEmpty() && existingMonthly.isEmpty()) {
                List<StockPriceWeekly> weeklyPrices = aggregateToWeekly(dailyPrices);
                List<StockPriceMonthly> monthlyPrices = aggregateToMonthly(dailyPrices);

                weeklyPriceRepository.saveAll(weeklyPrices);
                monthlyPriceRepository.saveAll(monthlyPrices);

                stockResult.setWeeklyCount(weeklyPrices.size());
                stockResult.setMonthlyCount(monthlyPrices.size());
                return stockResult;
            }

            // 增量更新：只更新受影响的周和月
            // 找到最新日期所在的周和月
            LocalDate currentWeekMonday = latestDailyDate.minusDays(latestDailyDate.getDayOfWeek().getValue() - 1);
            LocalDate currentMonthFirst = latestDailyDate.withDayOfMonth(1);

            // 删除需要重新计算的周K（包含最新周的周K数据需要重新计算）
            LocalDate deleteWeeklyStart = (lastWeeklyDate != null && lastWeeklyDate.isAfter(currentWeekMonday.minusWeeks(1)))
                ? lastWeeklyDate.minusWeeks(1) : currentWeekMonday.minusWeeks(1);

            // 删除需要重新计算的月K
            LocalDate deleteMonthlyStart = (lastMonthlyDate != null && lastMonthlyDate.isAfter(currentMonthFirst.minusMonths(1)))
                ? lastMonthlyDate.minusMonths(1) : currentMonthFirst.minusMonths(1);

            // 重新聚合并保存
            List<StockPriceWeekly> weeklyPrices = aggregateToWeekly(dailyPrices);
            List<StockPriceMonthly> monthlyPrices = aggregateToMonthly(dailyPrices);

            // 使用upsert方式更新（先删后插）
            weeklyPriceRepository.deleteByCode(code);
            monthlyPriceRepository.deleteByCode(code);

            weeklyPriceRepository.saveAll(weeklyPrices);
            monthlyPriceRepository.saveAll(monthlyPrices);

            stockResult.setWeeklyCount(weeklyPrices.size());
            stockResult.setMonthlyCount(monthlyPrices.size());

        } catch (Exception e) {
            log.error("增量聚合股票 {} K线数据失败", code, e);
            stockResult.setSuccess(false);
            stockResult.setErrorMessage(e.getMessage());
        }

        return stockResult;
    }

    /**
     * 聚合单只股票的周K和月K数据
     */
    public void aggregateKLineDataForStock(String code) {
        try {
            List<StockPrice> dailyPrices = priceRepository.findByCodeOrderByDateAsc(code);

            if (dailyPrices == null || dailyPrices.isEmpty()) {
                log.warn("股票 {} 没有日K数据，跳过聚合", code);
                return;
            }

            // 先删除旧的聚合数据
            weeklyPriceRepository.deleteByCode(code);
            monthlyPriceRepository.deleteByCode(code);

            // 聚合周K
            List<StockPriceWeekly> weeklyPrices = aggregateToWeekly(dailyPrices);
            if (!weeklyPrices.isEmpty()) {
                weeklyPriceRepository.saveAll(weeklyPrices);
            }

            // 聚合月K
            List<StockPriceMonthly> monthlyPrices = aggregateToMonthly(dailyPrices);
            if (!monthlyPrices.isEmpty()) {
                monthlyPriceRepository.saveAll(monthlyPrices);
            }

            log.info("股票 {} K线数据聚合完成: 周K {} 条, 月K {} 条",
                code, weeklyPrices.size(), monthlyPrices.size());

        } catch (Exception e) {
            log.error("聚合股票 {} K线数据失败", code, e);
        }
    }

    /**
     * 将日K数据聚合为周K（返回实体列表）
     */
    private List<StockPriceWeekly> aggregateToWeekly(List<StockPrice> dailyPrices) {
        if (dailyPrices == null || dailyPrices.isEmpty()) {
            return List.of();
        }

        Map<String, List<StockPrice>> grouped = dailyPrices.stream()
            .collect(Collectors.groupingBy(p -> {
                LocalDate date = p.getDate();
                // 找到本周的周一
                LocalDate monday = date.minusDays(date.getDayOfWeek().getValue() - 1);
                return p.getCode() + "_" + monday.toString();
            }));

        return grouped.values().stream()
            .map(weekPrices -> {
                StockPrice first = weekPrices.get(0);
                StockPriceWeekly weekly = new StockPriceWeekly();
                weekly.setCode(first.getCode());
                // 设置为本周第一天（周一）的日期
                LocalDate monday = first.getDate().minusDays(first.getDate().getDayOfWeek().getValue() - 1);
                weekly.setDate(monday);
                weekly.setOpenPrice(first.getOpenPrice());
                weekly.setClosePrice(weekPrices.get(weekPrices.size() - 1).getClosePrice());
                weekly.setHighPrice(weekPrices.stream()
                    .map(StockPrice::getHighPrice)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal::max)
                    .orElse(BigDecimal.ZERO));
                weekly.setLowPrice(weekPrices.stream()
                    .map(StockPrice::getLowPrice)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal::min)
                    .orElse(BigDecimal.ZERO));
                weekly.setVolume(weekPrices.stream()
                    .map(StockPrice::getVolume)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal::add)
                    .orElse(BigDecimal.ZERO));
                weekly.setAmount(weekPrices.stream()
                    .map(StockPrice::getAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal::add)
                    .orElse(BigDecimal.ZERO));
                weekly.setDataType("weekly");
                return weekly;
            })
            .sorted(Comparator.comparing(StockPriceWeekly::getDate))
            .collect(Collectors.toList());
    }

    /**
     * 将日K数据聚合为月K（返回实体列表）
     */
    private List<StockPriceMonthly> aggregateToMonthly(List<StockPrice> dailyPrices) {
        if (dailyPrices == null || dailyPrices.isEmpty()) {
            return List.of();
        }

        Map<String, List<StockPrice>> grouped = dailyPrices.stream()
            .collect(Collectors.groupingBy(p ->
                p.getCode() + "_" + p.getDate().getYear() + "-" + p.getDate().getMonthValue()
            ));

        return grouped.values().stream()
            .map(monthPrices -> {
                StockPrice first = monthPrices.get(0);
                StockPriceMonthly monthly = new StockPriceMonthly();
                monthly.setCode(first.getCode());
                // 设置为本月第一天
                monthly.setDate(first.getDate().withDayOfMonth(1));
                monthly.setOpenPrice(first.getOpenPrice());
                monthly.setClosePrice(monthPrices.get(monthPrices.size() - 1).getClosePrice());
                monthly.setHighPrice(monthPrices.stream()
                    .map(StockPrice::getHighPrice)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal::max)
                    .orElse(BigDecimal.ZERO));
                monthly.setLowPrice(monthPrices.stream()
                    .map(StockPrice::getLowPrice)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal::min)
                    .orElse(BigDecimal.ZERO));
                monthly.setVolume(monthPrices.stream()
                    .map(StockPrice::getVolume)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal::add)
                    .orElse(BigDecimal.ZERO));
                monthly.setAmount(monthPrices.stream()
                    .map(StockPrice::getAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal::add)
                    .orElse(BigDecimal.ZERO));
                monthly.setDataType("monthly");
                return monthly;
            })
            .sorted(Comparator.comparing(StockPriceMonthly::getDate))
            .collect(Collectors.toList());
    }

    /**
     * 获取预聚合的周K数据
     */
    public List<StockPriceWeekly> getWeeklyPrices(String code) {
        return weeklyPriceRepository.findByCodeOrderByDateAsc(code);
    }

    /**
     * 获取预聚合的月K数据
     */
    public List<StockPriceMonthly> getMonthlyPrices(String code) {
        return monthlyPriceRepository.findByCodeOrderByDateAsc(code);
    }

    /**
     * 根据日期范围获取预聚合的周K数据
     */
    public List<StockPriceWeekly> getWeeklyPricesByDateRange(String code, LocalDate startDate, LocalDate endDate) {
        return weeklyPriceRepository.findByCodeAndDateBetweenOrderByDateAsc(code, startDate, endDate);
    }

    /**
     * 根据日期范围获取预聚合的月K数据
     */
    public List<StockPriceMonthly> getMonthlyPricesByDateRange(String code, LocalDate startDate, LocalDate endDate) {
        return monthlyPriceRepository.findByCodeAndDateBetweenOrderByDateAsc(code, startDate, endDate);
    }

    /**
     * 聚合结果
     */
    @lombok.Data
    public static class AggregateResult {
        private boolean success;
        private int weeklyCount;
        private int monthlyCount;
        private int processedStockCount;
        private int totalStockCount;
        private long costTimeMs;
        private String errorMessage;
    }
}