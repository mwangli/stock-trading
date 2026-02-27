package com.stock.databus.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.stock.databus.entity.StockInfo;
import com.stock.databus.entity.StockPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TushareClient {

    private static final String TUSHARE_API_URL = "http://api.tushare.pro";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final HttpClient httpClient;

    @Value("${tuShare.keys[0]}")
    private String token;

    public TushareClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 调用Tushare API (使用JDK HttpClient)
     */
    private JSONObject callApi(String apiName, Map<String, Object> params) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("api_name", apiName);
            request.put("token", token);
            request.put("params", params);

            String requestBody = JSON.toJSONString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(TUSHARE_API_URL))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Tushare API调用失败，HTTP状态码: {}", response.statusCode());
                return null;
            }

            JSONObject jsonResponse = JSON.parseObject(response.body());
            if (jsonResponse.getIntValue("code", -1) != 0) {
                log.error("Tushare API返回错误: {}", jsonResponse.getString("msg"));
                return null;
            }

            return jsonResponse;
        } catch (Exception e) {
            log.error("调用Tushare API异常", e);
            return null;
        }
    }

    /**
     * 获取股票列表
     */
    public List<StockInfo> fetchStockList() {
        log.info("开始获取股票列表...");

        Map<String, Object> params = new HashMap<>();
        params.put("list_status", "L");
        params.put("fields", "ts_code,symbol,name,market,st,trade,pre_close,area,industry,list_date");

        JSONObject response = callApi("stock_basic", params);
        if (response == null) {
            return new ArrayList<>();
        }

        JSONArray data = response.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            return new ArrayList<>();
        }

        List<StockInfo> stocks = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            StockInfo stock = new StockInfo();

            // Tushare返回的ts_code格式: 000001.SZ
            String tsCode = item.getString("ts_code");
            if (tsCode != null && tsCode.contains(".")) {
                stock.setCode(tsCode.split("\\.")[0]);
            }

            stock.setName(item.getString("name"));
            stock.setMarket(item.getString("market"));
            stock.setIsSt("ST".equals(item.getString("st")) ? 1 : 0);
            stock.setIsTradable("L".equals(item.getString("trade")) ? 1 : 0);
            stock.setArea(item.getString("area"));
            stock.setIndustry(item.getString("industry"));
            stock.setListDate(item.getString("list_date"));
            stock.setDeleted("0");

            stocks.add(stock);
        }

        log.info("成功获取 {} 只股票", stocks.size());
        return stocks;
    }

    /**
     * 获取历史K线数据
     */
    public List<StockPrice> fetchDailyKlines(String stockCode, int days) {
        log.info("开始获取股票 {} 的历史K线数据, 天数: {}", stockCode, days);

        // 计算日期范围
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        Map<String, Object> params = new HashMap<>();
        params.put("ts_code", stockCode + ".SZ");
        params.put("start_date", startDate.format(DATE_FORMATTER));
        params.put("end_date", endDate.format(DATE_FORMATTER));
        params.put("fields", "ts_code,trade_date,open,high,low,close,vol,amount");

        JSONObject response = callApi("daily", params);
        if (response == null) {
            return new ArrayList<>();
        }

        JSONArray data = response.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            log.warn("未获取到股票 {} 的历史数据", stockCode);
            return new ArrayList<>();
        }

        List<StockPrice> prices = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            StockPrice price = new StockPrice();

            String tsCode = item.getString("ts_code");
            if (tsCode != null && tsCode.contains(".")) {
                price.setCode(tsCode.split("\\.")[0]);
            }

            // 转换日期格式: yyyyMMdd -> LocalDate
            String tradeDate = item.getString("trade_date");
            if (tradeDate != null && tradeDate.length() == 8) {
                price.setDate(LocalDate.parse(tradeDate, DateTimeFormatter.ofPattern("yyyyMMdd")));
            }

            price.setTodayOpenPrice(item.getBigDecimal("open"));
            price.setPrice1(item.getBigDecimal("high"));
            price.setPrice2(item.getBigDecimal("low"));
            price.setPrice3(item.getBigDecimal("close"));
            price.setPrice4(item.getBigDecimal("close"));
            price.setTradingVolume(item.getBigDecimal("vol"));
            price.setTradingAmount(item.getBigDecimal("amount"));

            // 计算涨跌幅
            BigDecimal close = item.getBigDecimal("close");
            BigDecimal preClose = item.getBigDecimal("pre_close");
            if (close != null && preClose != null && preClose.compareTo(BigDecimal.ZERO) != 0) {
                price.setIncreaseRate(close.subtract(preClose).divide(preClose, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)));
                price.setChangeAmount(close.subtract(preClose));
            }

            prices.add(price);
        }

        log.info("成功获取股票 {} 的 {} 条历史数据", stockCode, prices.size());
        return prices;
    }
}
