package com.stock.databus.collector;

import com.stock.databus.client.SecuritiesPlatformClient;
import com.stock.databus.entity.StockInfo;
import com.stock.databus.entity.StockPrice;
import com.stock.databus.entity.StockPrices;
import com.stock.databus.repository.PriceRepository;
import com.stock.databus.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 股票数据采集器
 * 使用证券平台接口获取数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockCollector {

    private final SecuritiesPlatformClient securitiesPlatformClient;
    private final StockRepository stockRepository;
    private final PriceRepository priceRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Transactional
    public int collectStockList() {
        log.info("开始从证券平台采集股票列表...");
        try {
            List<StockInfo> stocks = securitiesPlatformClient.getDataList();
            if (stocks == null || stocks.isEmpty()) {
                log.warn("未获取到股票数据");
                return 0;
            }

            int count = 0;
            for (StockInfo stock : stocks) {
                Optional<StockInfo> existingOpt = stockRepository.findByCode(stock.getCode());
                if (existingOpt.isPresent()) {
                    // 更新现有股票
                    StockInfo existing = existingOpt.get();
                    stock.setId(existing.getId());
                    stock.setCreateTime(existing.getCreateTime());
                    stock.setUpdateTime(LocalDateTime.now());
                } else {
                    // 新增股票
                    stock.setCreateTime(LocalDateTime.now());
                    stock.setUpdateTime(LocalDateTime.now());
                    stock.setDeleted(false);
                }
                stockRepository.save(stock);
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
        log.info("开始采集股票 {} 历史数据，天数：{}", stockCode, days);
        try {
            // 从证券平台获取历史价格数据
            List<StockPrices> prices = securitiesPlatformClient.getHistoryPrices(stockCode);
            if (prices == null || prices.isEmpty()) {
                log.warn("未获取到历史数据");
                return 0;
            }

            int count = 0;
            for (StockPrices price : prices) {
                // 转换为 StockPrice 实体
                StockPrice stockPrice = new StockPrice();
                stockPrice.setCode(stockCode);
                stockPrice.setTradeDate(price.getDate());
                stockPrice.setOpen(price.getPrice1());
                stockPrice.setClose(price.getPrice2());
                stockPrice.setHigh(price.getPrice3());
                stockPrice.setLow(price.getPrice4());
                stockPrice.setCreateTime(LocalDateTime.now());

                // 检查是否已存在该日期的数据
                Optional<StockPrice> existingOpt = priceRepository.findByCodeAndTradeDate(stockCode, price.getDate());
                if (existingOpt.isPresent()) {
                    // 更新现有数据
                    StockPrice existing = existingOpt.get();
                    existing.setOpen(stockPrice.getOpen());
                    existing.setClose(stockPrice.getClose());
                    existing.setHigh(stockPrice.getHigh());
                    existing.setLow(stockPrice.getLow());
                    existing.setUpdateTime(LocalDateTime.now());
                    priceRepository.save(existing);
                } else {
                    // 新增数据
                    priceRepository.save(stockPrice);
                }
                count++;
            }
            log.info("成功采集 {} 条历史数据", count);
            return count;
        } catch (Exception e) {
            log.error("采集历史数据失败", e);
            return 0;
        }
    }

    /**
     * 获取实时价格
     */
    public Double getRealTimePrice(String stockCode) {
        try {
            return securitiesPlatformClient.getNowPrice(stockCode);
        } catch (Exception e) {
            log.error("获取实时价格失败：{}", stockCode, e);
            return null;
        }
    }
}
