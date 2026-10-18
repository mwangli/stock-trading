// AI_GENERATE_START --
package com.stock.modelService.service;

import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.dataCollector.persistence.PriceRepository;
import com.stock.dataCollector.persistence.StockInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.stream.Collectors;

/**
 * LSTM 训练与预测行情数据加载服务。
 * 集中处理最近窗口读取、时间升序恢复和股票上下文批量装载。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LstmTrainingDataService {

    private final PriceRepository priceRepository;
    private final StockInfoRepository stockInfoRepository;

    /**
     * 加载多只股票最近指定交易日窗口。
     *
     * @param stockCodes 股票代码
     * @param days 每只股票最大交易日数量
     * @return 按股票代码组织且日期升序的行情
     */
    public Map<String, List<StockPrice>> loadPriceSeries(List<String> stockCodes, int days) {
        return loadPriceSeries(stockCodes, days, (PreparationProgressListener) null);
    }

    /**
     * 加载多只股票最近指定交易日窗口并上报股票级准备进度。
     *
     * @param stockCodes 股票代码
     * @param days 每只股票最大交易日数量
     * @param listener 进度监听器，可为空
     * @return 按股票代码组织且日期升序的行情
     */
    public Map<String, List<StockPrice>> loadPriceSeries(
            List<String> stockCodes, int days, PreparationProgressListener listener) {
        return loadPriceSeries(stockCodes, days, null, listener);
    }

    /**
     * 加载指定历史截止日的股票行情窗口。
     *
     * @param stockCodes 股票代码
     * @param days 每只股票最大交易日数量
     * @param endDate 行情截止日期，包含当天；为空时读取最新行情
     * @return 按股票代码组织且日期升序排列的行情
     */
    public Map<String, List<StockPrice>> loadPriceSeries(
            List<String> stockCodes, int days, LocalDate endDate) {
        return loadPriceSeries(stockCodes, days, endDate, null);
    }

    private Map<String, List<StockPrice>> loadPriceSeries(
            List<String> stockCodes, int days, LocalDate endDate, PreparationProgressListener listener) {
        Map<String, List<StockPrice>> result = new LinkedHashMap<>();
        int queryLimit = Math.max(1, days);
        int totalStocks = stockCodes.size();
        int reportInterval = Math.max(1, totalStocks / 40);
        PageRequest pageRequest = PageRequest.of(0, queryLimit);
        int emptyStocks = 0;
        for (int index = 0; index < totalStocks; index++) {
            String stockCode = stockCodes.get(index);
            List<StockPrice> prices = new ArrayList<>(endDate == null
                    ? priceRepository.findByCodeOrderByDateDesc(stockCode, pageRequest)
                    : priceRepository.findByCodeAndDateLessThanEqualOrderByDateDesc(
                            stockCode, endDate, pageRequest));
            if (prices.isEmpty()) {
                emptyStocks++;
            } else {
                Collections.reverse(prices);
                result.put(stockCode, prices);
            }
            int completedStocks = index + 1;
            if (listener != null && (completedStocks == totalStocks || completedStocks % reportInterval == 0)) {
                listener.onProgress(completedStocks, totalStocks, result.size(), emptyStocks);
            }
        }
        log.info("LSTM 行情窗口加载完成: requestedStocks={}, loadedStocks={}, emptyStocks={}, days={}",
                totalStocks, result.size(), emptyStocks, queryLimit);
        return result;
    }

    /**
     * 批量加载股票行业和市场上下文。
     *
     * @param stockCodes 股票代码
     * @return 股票代码到模型上下文的映射
     */
    public Map<String, LstmDataPreprocessor.StockContext> loadStockContexts(List<String> stockCodes) {
        return stockInfoRepository.findByCodeIn(stockCodes).stream()
                .collect(Collectors.toMap(
                        StockInfo::getCode,
                        stock -> LstmDataPreprocessor.StockContext.builder()
                                .stockCode(stock.getCode())
                                .industryCode(stock.getIndustryCode())
                                .market(stock.getMarket())
                                .build(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    /**
     * 股票级数据准备进度监听器。
     *
     * @author mwangli
     * @since 2026-10-14
     */
    @FunctionalInterface
    public interface PreparationProgressListener {

        /**
         * 接收当前加载进度。
         *
         * @param completedStocks 已处理股票数
         * @param totalStocks 股票总数
         * @param loadedStocks 有效股票数
         * @param emptyStocks 无行情股票数
         */
        void onProgress(int completedStocks, int totalStocks, int loadedStocks, int emptyStocks);
    }
}
// AI_GENERATE_END --
