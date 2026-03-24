package com.stock.dataCollector.support;

import com.alibaba.fastjson2.JSONArray;
import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.domain.entity.StockPrice;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class StockDataParser {

    private StockDataParser() {
    }

    public static String parseMarket(String market) {
        if (market == null) return "未知";
        return switch (market.toUpperCase()) {
            case "SH" -> "上海";
            case "SZ" -> "深圳";
            case "BJ" -> "北京";
            default -> market;
        };
    }

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

    public static StockInfo parseStockInfo(String data) {
        if (data == null || data.isEmpty()) return null;
        log.debug("原始股票数据: {}", data);
        String cleanedLine = data.replaceAll("[\\[\\]]", "");
        String[] split = cleanedLine.split(",");
        if (split.length < 5) return null;
        StockInfo entity = new StockInfo();
        entity.setCode(clean(split[4]));
        entity.setName(clean(split[2]));
        entity.setMarket(parseMarket(clean(split[3])));
        BigDecimal price = parseDecimalField(split[1]);
        if (price != null) entity.setPrice(price);
        BigDecimal changePercent = parseDecimalField(split[0]);
        if (changePercent != null) entity.setChangePercent(changePercent);
        if (split.length > 5) {
            BigDecimal volumeRatio = parseDecimalField(split[5]);
            if (volumeRatio != null) entity.setVolumeRatio(volumeRatio);
        }
        if (split.length > 6) {
            BigDecimal turnoverRate = parseDecimalField(split[6]);
            if (turnoverRate != null) entity.setTurnoverRate(turnoverRate);
        }
        if (split.length > 8) {
            try {
                if (!clean(split[8]).isEmpty()) {
                    entity.setIndustryCode(Integer.parseInt(clean(split[8])));
                }
            } catch (NumberFormatException ignored) {}
        }
        if (split.length > 9) {
            BigDecimal totalMv = parseDecimalField(split[9]);
            if (totalMv != null) entity.setTotalMarketValue(totalMv);
        }
        if (entity.getPrice() != null && entity.getChangePercent() != null) {
            entity.setChangeAmount(entity.getPrice().multiply(entity.getChangePercent()));
        }
        return entity;
    }

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

    public static StockPrice parseStockPrice(String data, String stockCode) {
        if (data == null || data.isEmpty()) return null;
        String cleaned = data.replaceAll("[\\[\\]]", "");
        String[] split = cleaned.split(",");
        if (split.length < 5) return null;
        StockPrice price = new StockPrice();
        price.setCode(stockCode);
        try {
            price.setDate(LocalDate.parse(split[0], DateTimeFormatter.BASIC_ISO_DATE));
        } catch (Exception e) {
            log.debug("解析价格日期失败: {}", split[0]);
            return null;
        }
        price.setOpenPrice(parsePrice(split[1]));
        price.setHighPrice(parsePrice(split[2]));
        price.setClosePrice(parsePrice(split[3]));
        price.setLowPrice(parsePrice(split[4]));
        if (split.length > 6) {
            try {
                price.setVolume(new BigDecimal(split[5]));
            } catch (Exception ignored) {}
            try {
                price.setAmount(new BigDecimal(split[6]));
            } catch (Exception ignored) {}
        }
        return price;
    }

    private static String clean(String field) {
        return field == null ? "" : field.replaceAll("\"", "").trim();
    }

    private static BigDecimal parseDecimalField(String raw) {
        String value = clean(raw);
        if (value.isEmpty()) return null;
        if ("--".equals(value) || "N/A".equalsIgnoreCase(value)) return null;
        value = value.replace(",", "").replace("%", "");
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.debug("解析数值字段失败，原始值: {}", raw);
            return null;
        }
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
