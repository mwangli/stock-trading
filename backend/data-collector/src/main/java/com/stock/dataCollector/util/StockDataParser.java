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

        // 打印原始股票数据，便于排查解析问题
        log.debug("原始股票数据: {}", data);

        // 去掉首尾方括号，避免首字段带 "["、末字段带 "]"
        String cleanedLine = data.replaceAll("[\\[\\]]", "");
        String[] split = cleanedLine.split(",");
        if (split.length < 5) return null;

        StockInfo entity = new StockInfo();
        entity.setCode(clean(split[4]));
        entity.setName(clean(split[2]));
        entity.setMarket(parseMarket(clean(split[3])));
        
        // 解析当前价格 (索引1)
        BigDecimal price = parseDecimalField(split[1]);
        if (price != null) {
            entity.setPrice(price);
        }

        // 解析涨跌幅 (索引0)，兼容带百分号 / 特殊占位符
        BigDecimal changePercent = parseDecimalField(split[0]);
        if (changePercent != null) {
            entity.setChangePercent(changePercent);
        }

        // 解析量比 (索引5)
        if (split.length > 5) {
            BigDecimal volumeRatio = parseDecimalField(split[5]);
            if (volumeRatio != null) {
                entity.setVolumeRatio(volumeRatio);
            }
        }
        
        // 解析换手率 (索引6)
        if (split.length > 6) {
            BigDecimal turnoverRate = parseDecimalField(split[6]);
            if (turnoverRate != null) {
                entity.setTurnoverRate(turnoverRate);
            }
        }
        
        // 解析行业代码 (索引8)
        if (split.length > 8) {
            try {
                if (!clean(split[8]).isEmpty()) {
                    entity.setIndustryCode(Integer.parseInt(clean(split[8])));
                }
            } catch (NumberFormatException ignored) {}
        }
        
        // 解析总市值 (索引9)
        if (split.length > 9) {
            BigDecimal totalMv = parseDecimalField(split[9]);
            if (totalMv != null) {
                entity.setTotalMarketValue(totalMv);
            }
        }
        
        // 计算涨跌额 = 当前价格 × 涨跌幅
        if (entity.getPrice() != null && entity.getChangePercent() != null) {
            BigDecimal changeAmount = entity.getPrice().multiply(entity.getChangePercent());
            entity.setChangeAmount(changeAmount);
        }
        
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

    /**
     * 通用十进制字段解析：
     * - 去掉引号、空格
     * - 过滤 "--" 等占位符
     * - 去掉逗号、百分号
     * - 保持原有数值尺度（不做额外乘除）
     */
    private static BigDecimal parseDecimalField(String raw) {
        String value = clean(raw);
        if (value.isEmpty()) {
            return null;
        }
        // 过滤常见无效占位符
        if ("--".equals(value) || "N/A".equalsIgnoreCase(value)) {
            return null;
        }

        // 去掉逗号和百分号等格式字符
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