package com.stock.databus.collector;

import com.stock.databus.client.TushareClient;
import com.stock.databus.entity.StockInfo;
import com.stock.databus.entity.StockPrice;
import com.stock.databus.repository.PriceRepository;
import com.stock.databus.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCollector {

    private final TushareClient tushareClient;
    private final StockRepository stockRepository;
    private final PriceRepository priceRepository;

    @Transactional
    public int collectStockList() {
        log.info("开始从Tushare采集股票列表...");
        try {
            List<StockInfo> stocks = tushareClient.fetchStockList();
            if (stocks.isEmpty()) {
                log.warn("未获取到股票数据");
                return 0;
            }

            int count = 0;
            for (StockInfo stock : stocks) {
                StockInfo existing = stockRepository.findByCode(stock.getCode());
                if (existing != null) {
                    stock.setId(existing.getId());
                    stock.setCreateTime(existing.getCreateTime());
                    stockRepository.updateById(stock);
                } else {
                    stock.setCreateTime(LocalDateTime.now());
                    stockRepository.insert(stock);
                }
                count++;
            }
            log.info("成功采集 {} 只股票", count);
            return count;
        } catch (Exception e) {
            log.error("采集股票列表失败", e);
            return 0;
        }
    }

    @Transactional
    public int collectHistoricalData(String stockCode, int days) {
        log.info("开始采集股票 {} 历史数据, 天数: {}", stockCode, days);
        try {
            List<StockPrice> prices = tushareClient.fetchDailyKlines(stockCode, days);
            if (prices.isEmpty()) {
                log.warn("未获取到历史数据");
                return 0;
            }

            for (StockPrice price : prices) {
                price.setCode(stockCode);
                price.setCreateTime(LocalDateTime.now());
            }

            priceRepository.saveAll(prices);
            log.info("成功采集 {} 条历史数据", prices.size());
            return prices.size();
        } catch (Exception e) {
            log.error("采集历史数据失败", e);
            return 0;
        }
    }
}
