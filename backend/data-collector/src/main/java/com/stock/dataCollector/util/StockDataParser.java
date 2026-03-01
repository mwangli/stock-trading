package com.stock.dataCollector.util;

import com.alibaba.fastjson2.JSONArray;
import com.stock.dataCollector.entity.StockInfo;
import com.stock.dataCollector.entity.StockPrice;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 股票数据解析工具类
 * 负责解析证券平台API返回的数据
 */
@Slf4j
public final class StockDataParser {

    private StockDataParser() {
    }

    /**
     * 解析市场代码
     */
    public static String parseMarket(String market) {
        if (market == null) return "未知";
        return switch (market.toUpperCase()) {
            case "SH" -> "上海";
            case "SZ" -> "深圳";
            case "BJ" -> "北京";
            default -> market;
        };
    }

    /**
     * 从API响应解析股票列表
     */
    public static List<StockInfo> parseStockList(JSONArray results) {
        List<StockInfo> stockList = new ArrayList<>();
        if (results == null || results.isEmpty()) return stockList;

        for (int i = 0; i < results.size(); i++) {
            try {
                StockInfo stock = parseStockInfo(results.getString(i));
                if (stock != null) stockList.add(stock);
            } catch (Exception e) {
                log.debug("解析股票数据失败: {}", e.getMessage());
            }
        }
        return stockList;
    }

    /**
     * 解析单条股票信息
     */
    public static StockInfo parseStockInfo(String data) {
        if (data == null || data.isEmpty()) return null;

        String[] split = data.split(",");
        if (split.length < 5) return null;

        StockInfo entity = new StockInfo();
        entity.setCode(clean(split[4]));
        entity.setName(clean(split[2]));
        entity.setMarket(parseMarket(clean(split[3])));
        entity.setDataSource("证券平台");

        setDecimal(entity, clean(split[0]), (e, v) -> e.setChangePercent(v));
        setDecimal(entity, clean(split[1]), (e, v) -> e.setPrice(v));
        
        if (split.length > 5) setDecimal(entity, clean(split[5]), (e, v) -> e.setVolume(v));
        if (split.length > 9) setDecimal(entity, clean(split[9]), (e, v) -> e.setTotalMarketValue(v));

        return entity;
    }

    /**
     * 从API响应解析历史价格列表
     */
    public static List<StockPrice> parsePriceList(JSONArray results, String stockCode) {
        List<StockPrice> prices = new ArrayList<>();
        if (results == null || results.isEmpty()) return prices;

        for (int i = 0; i < results.size(); i++) {
            try {
                StockPrice price = parseStockPrice(results.getString(i), stockCode);
                if (price != null) prices.add(price);
            } catch (Exception e) {
                log.debug("解析价格数据失败: {}", e.getMessage());
            }
        }
        return prices;
    }

    /**
     * 解析单条价格数据
     */
    public static StockPrice parseStockPrice(String data, String stockCode) {
        if (data == null || data.isEmpty()) return null;

        String cleaned = data.replaceAll("[\\[\\]]", "");
        String[] split = cleaned.split(",");
        if (split.length < 3) return null;

        StockPrice price = new StockPrice();
        price.setCode(stockCode);
        price.setDate(LocalDate.parse(split[0]));
        price.setOpenPrice(parsePrice(split[1]));
        price.setClosePrice(parsePrice(split[2]));
        if (split.length > 4) {
            price.setHighPrice(parsePrice(split[3]));
            price.setLowPrice(parsePrice(split[4]));
        }
        return price;
    }

    private static String clean(String field) {
        return field == null ? "" : field.replaceAll("\"", "").trim();
    }

    private static BigDecimal parsePrice(String value) {
        try {
            return BigDecimal.valueOf(Double.parseDouble(value) / 100);
        } catch (Exception e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface DecimalSetter {
        void set(StockInfo entity, BigDecimal value);
    }

    private static void setDecimal(StockInfo entity, String value, DecimalSetter setter) {
        if (value != null && !value.isEmpty()) {
            try {
                setter.set(entity, new BigDecimal(value));
            } catch (NumberFormatException ignored) {}
        }
    }
}