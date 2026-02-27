package com.stock.databus.client;

import com.github.tusharepro.core.TusharePro;
import com.github.tusharepro.core.TushareProService;
import com.github.tusharepro.core.bean.StockBasic;
import com.github.tusharepro.core.bean.Daily;
import com.github.tusharepro.core.common.KeyValue;
import com.github.tusharepro.core.entity.DailyBasicEntity;
import com.github.tusharepro.core.entity.DailyEntity;
import com.github.tusharepro.core.entity.StockBasicEntity;
import com.github.tusharepro.core.http.Request;
import com.github.tusharepro.core.bean.StockBasic;
import com.github.tusharepro.core.bean.Daily;
import com.stock.databus.entity.StockInfo;
import com.stock.databus.entity.StockPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TushareClient {

    @Value("${tuShare.keys[0]}")
    private String tushareToken;

    @PostConstruct
    public void init() {
        if (tushareToken != null && !tushareToken.isEmpty()) {
            final TusharePro.Builder builder = new TusharePro.Builder()
                    .setToken(tushareToken);
            TusharePro.setGlobal(builder.build());
            log.info("Tushare客户端初始化完成");
        } else {
            log.warn("Tushare token未配置，请配置tuShare.keys");
        }
    }

    public List<StockInfo> fetchStockList() {

        try {
            final KeyValue<String, String> list_status = StockBasic.Params.list_status.value("L");

            Request<StockBasicEntity> stockRequest = new Request<>() {
            };
            stockRequest.param("list_status", list_status);
            stockRequest.param("fields", "ts_code,symbol,name,area,industry,list_date");

            List<StockBasicEntity> stockList = com.github.tusharepro.core.TushareProService.stockBasic(stockRequest);

            List<StockInfo> stocks = new ArrayList<>();
            for (StockBasicEntity sb : stockList) {
                String name = sb.getName();
                if (name != null && (name.contains("ST") || name.contains("*ST"))) {
                    continue;
                }

                StockInfo stock = new StockInfo();
                String tsCode = sb.getTsCode();
                stock.setCode(tsCode.substring(0, 6));
                stock.setName(name);
                stock.setMarket(tsCode.endsWith("SH") ? "SH" : "SZ");
                assert name != null;
                stock.setIsSt(name.contains("ST") || name.contains("*ST") ? 1 : 0);
                stock.setIsTradable(1);
                stock.setDeleted("0");

                stocks.add(stock);
            }

            log.info("成功从Tushare获取 {} 只股票", stocks.size());
            return stocks;

        } catch (Exception e) {
            log.error("从Tushare获取股票列表失败", e);
            return null;
        }
    }

    public List<StockPrice> fetchDailyKlines(String stockCode, int days) {
        try {
            Request<DailyEntity> dailyRequest = new Request<DailyEntity>() {};
            String tsCode = stockCode + (stockCode.startsWith("6") ? ".SH" : ".SZ");
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days);

            dailyRequest.param("ts_code", tsCode);
            dailyRequest.param("start_date", startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            dailyRequest.param("end_date", endDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")));

            List<DailyEntity> dailyList = com.github.tusharepro.core.TushareProService.daily(dailyRequest);

            List<StockPrice> prices = new ArrayList<>();
            for (DailyEntity daily : dailyList) {
                StockPrice price = new StockPrice();
                price.setCode(stockCode);
                price.setDate(daily.getTradeDate());
                price.setPrice1(BigDecimal.valueOf(daily.getOpen()));
                price.setPrice2(BigDecimal.valueOf(daily.getHigh()));
                price.setPrice3(BigDecimal.valueOf(daily.getLow()));
                price.setPrice4(BigDecimal.valueOf(daily.getClose()));
                price.setTradingVolume(BigDecimal.valueOf(daily.getVol()));
                price.setTradingAmount(BigDecimal.valueOf(daily.getAmount()));

                prices.add(price);
            }

            log.info("成功从Tushare获取 {} 条K线数据, stockCode: {}", prices.size(), stockCode);
            return prices;

        } catch (Exception e) {
            log.error("从Tushare获取K线数据失败, stockCode: {}", stockCode, e);
            return null;
        }
    }

}
