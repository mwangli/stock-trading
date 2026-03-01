package com.stock.databus.service;

import com.stock.databus.entity.StockInfo;
import com.stock.databus.entity.StockPrice;
import com.stock.databus.repository.PriceRepository;
import com.stock.databus.repository.StockRepository;
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

    /**
     * 获取股票历史价格数据
     * 
     * @param stockCode 股票代码
     * @return 历史价格列表
     */
    public List<StockPrice> getHistoryPrices(String stockCode) {
        log.info("获取股票 {} 的历史价格数据", stockCode);
        
        // TODO: 实现从证券平台获取历史价格数据
        // 参考：D:\stock-trading\src\main\java\online\mwang\stockTrading\schedule\jobs\RunHistoryJob.java
        
        // 临时返回空列表
        return new ArrayList<>();
    }

    /**
     * 保存股票价格数据到 MongoDB
     * 
     * @param prices 价格数据列表
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
                // 检查是否已存在
                var existingOpt = priceRepository.findByCodeAndDate(
                    price.getCode(), 
                    price.getDate()
                );

                if (existingOpt.isPresent()) {
                    StockPrice existing = existingOpt.get();
                    
                    // 如果数据不完整，则更新
                    if (existing.getClosePrice() == null || existing.getVolume() == null) {
                        existing.setOpenPrice(price.getOpenPrice());
                        existing.setHighPrice(price.getHighPrice());
                        existing.setLowPrice(price.getLowPrice());
                        existing.setClosePrice(price.getClosePrice());
                        existing.setVolume(price.getVolume());
                        existing.setAmount(price.getAmount());
                        priceRepository.save(existing);
                        updated++;
                        log.debug("更新股票 {}-{} 的历史数据", price.getCode(), price.getDate());
                    } else {
                        // 数据完整，跳过
                        skipped++;
                    }
                } else {
                    // 插入新数据
                    priceRepository.save(price);
                    saved++;
                    log.debug("保存股票 {}-{} 的历史数据", price.getCode(), price.getDate());
                }
            } catch (Exception e) {
                log.error("保存股票数据失败：{}-{}", price.getCode(), price.getDate(), e);
            }
        }

        log.info("股票价格数据保存完成 - 新增：{}, 更新：{}, 跳过：{}", saved, updated, skipped);
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
     * 
     * @param stockCode 股票代码
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 同步的数据条数
     */
    @Transactional
    public int syncHistoricalData(String stockCode, LocalDate startDate, LocalDate endDate) {
        log.info("开始同步股票 {} 从 {} 到 {} 的历史数据", stockCode, startDate, endDate);

        // 获取历史价格数据
        List<StockPrice> prices = getHistoryPrices(stockCode);
        
        // 过滤日期范围
        List<StockPrice> filteredPrices = prices.stream()
            .filter(p -> !p.getDate().isBefore(startDate) && !p.getDate().isAfter(endDate))
            .toList();

        // 保存到 MongoDB
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