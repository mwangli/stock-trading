package com.stock.databus.collector;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stock.common.enums.MarketType;
import com.stock.databus.client.EastMoneyClient;
import com.stock.databus.entity.StockInfo;
import com.stock.databus.entity.StockPrice;
import com.stock.databus.repository.PriceRepository;
import com.stock.databus.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCollector {

    private final EastMoneyClient eastMoneyClient;
    private final StockRepository stockRepository;
    private final PriceRepository priceRepository;
    private final OkHttpClient httpClient;

    @Transactional
    public int collectStockList() {
        log.info("开始采集股票列表...");
        try {
            List<StockInfo> stocks = eastMoneyClient.fetchStockList();
            if (stocks.isEmpty()) {
                log.warn("未获取到股票数据");
                return 0;
            }

            for (StockInfo stock : stocks) {
                StockInfo existing = stockRepository.findByCode(stock.getCode());
                if (existing != null) {
                    stock.setId(existing.getId());
                    stockRepository.updateById(stock);
                } else {
                    stockRepository.insert(stock);
                }
            }
            log.info("成功采集 {} 只股票", stocks.size());
            return stocks.size();
        } catch (Exception e) {
            log.error("采集股票列表失败", e);
            return 0;
        }
    }

    @Transactional
    public int collectHistoricalData(String stockCode, int days) {
        log.info("开始采集股票 {} 历史数据, 天数: {}", stockCode, days);
        try {
            List<StockPrice> prices = fetchHistoricalFromEastMoney(stockCode, days);
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

    public int collectRealtimeQuotes() {
        log.info("开始采集实时行情...");
        try {
            List<StockInfo> stocks = eastMoneyClient.fetchStockList();
            int count = 0;
            for (StockInfo stock : stocks) {
                StockInfo existing = stockRepository.findByCode(stock.getCode());
                if (existing != null) {
                    existing.setPrice(stock.getPrice());
                    existing.setIncrease(stock.getIncrease());
                    existing.setUpdateTime(LocalDateTime.now());
                    stockRepository.updateById(existing);
                    count++;
                }
            }
            log.info("成功更新 {} 只股票行情", count);
            return count;
        } catch (Exception e) {
            log.error("采集实时行情失败", e);
            return 0;
        }
    }

    private List<StockPrice> fetchHistoricalFromEastMoney(String stockCode, int days) {
        List<StockPrice> prices = new ArrayList<>();
        try {
            String symbol = stockCode.startsWith("6") ? stockCode : "0" + stockCode;
            String url = String.format(
                    "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=%s.%s&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61&klt=101&fqt=0&end=%s&lmt=%d",
                    stockCode.startsWith("6") ? "1" : "0", stockCode,
                    LocalDate.now().format(DateTimeFormatter.ISO_DATE), days);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return prices;
                }

                String body = response.body().string();
                JSONObject json = JSONUtil.parseObj(body);
                JSONObject data = json.getJSONObject("data");
                if (data == null) {
                    return prices;
                }

                JSONArray klines = data.getJSONArray("klines");
                for (int i = 0; i < klines.size(); i++) {
                    try {
                        String kline = klines.getStr(i);
                        String[] parts = kline.split(",");
                        if (parts.length >= 6) {
                            StockPrice price = new StockPrice();
                            price.setDate(LocalDate.parse(parts[0]));
                            price.setPrice1(new BigDecimal(parts[1]));
                            price.setPrice2(new BigDecimal(parts[2]));
                            price.setPrice3(new BigDecimal(parts[3]));
                            price.setPrice4(new BigDecimal(parts[4]));
                            price.setTradingVolume(new BigDecimal(parts[5]));
                            if (parts.length >= 7) {
                                price.setTradingAmount(new BigDecimal(parts[6]));
                            }
                            prices.add(price);
                        }
                    } catch (Exception e) {
                        log.warn("解析K线数据异常: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取历史数据失败: {}", stockCode, e);
        }
        return prices;
    }
}
