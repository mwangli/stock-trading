// AI_GENERATE_START----------
package com.stock.data.parser;

import com.alibaba.fastjson2.JSONArray;
import com.stock.data.entity.StockInfo;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 证券接口文本数据解析器。
 * 负责把股票列表的外部数组记录转换为明确领域对象，非法字段按字段级空值处理。
 *
 * @author mwangli
 * @since 2026-10-29
 */
@Slf4j
public final class StockDataParser {

    private StockDataParser() {
    }

    /**
     * 将证券接口市场代码转换为中文市场名称。
     *
     * @param market 市场代码
     * @return 市场名称，未知代码保持原值
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
     * 批量解析股票列表记录，单条异常不会中断其余记录。
     *
     * @param results 外部接口股票数组
     * @return 成功解析的股票列表
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
     * 解析单条股票资料记录。
     *
     * @param data 外部接口逗号分隔记录
     * @return 股票资料，字段不足时返回空
     */
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
        BigDecimal price = parsePositiveDecimalField(split[1]);
        if (price != null) entity.setPrice(price);
        BigDecimal changePercent = parseDecimalField(split[0]);
        if (changePercent != null) entity.setChangePercent(changePercent);
        if (split.length > 5) {
            BigDecimal changeAmount = parseDecimalField(split[5]);
            if (changeAmount != null) entity.setChangeAmount(changeAmount);
        }
        if (split.length > 6) {
            BigDecimal volume = parseDecimalField(split[6]);
            if (volume != null) entity.setVolume(volume);
        }
        if (split.length > 7) {
            BigDecimal volumeRatio = parseDecimalField(split[7]);
            if (volumeRatio != null) entity.setVolumeRatio(volumeRatio);
        }
        if (split.length > 9) {
            BigDecimal turnoverRate = parseDecimalField(split[8]);
            if (turnoverRate != null) entity.setTurnoverRate(turnoverRate);
        }
        if (split.length > 9 && !clean(split[9]).isEmpty()) {
            entity.setSecurityCategory(clean(split[9]));
        }
        if (split.length > 10) {
            BigDecimal totalMv = parsePositiveDecimalField(split[10]);
            if (totalMv != null) entity.setTotalMarketValue(totalMv);
        }
        if (split.length > 3) {
            entity.setMarketCode(clean(split[3]));
        }
        if (split.length > 11) {
            entity.setBidPrice(parsePositiveDecimalField(split[11]));
        }
        if (split.length > 12) {
            entity.setAskPrice(parsePositiveDecimalField(split[12]));
        }
        calculateCombinationSignals(entity);
        return entity;
    }

    private static void calculateCombinationSignals(StockInfo entity) {
        BigDecimal changePercent = entity.getChangePercent();
        BigDecimal volumeRatio = entity.getVolumeRatio();
        BigDecimal turnoverRate = entity.getTurnoverRate();
        if (changePercent != null && volumeRatio != null) {
            entity.setMomentumStrength(changePercent.multiply(volumeRatio));
            entity.setVolumePriceSignal(changePercent.signum() * volumeRatio.signum() > 0
                    ? changePercent.abs().multiply(volumeRatio)
                    : changePercent.abs().negate().multiply(volumeRatio));
        }
        if (volumeRatio != null && turnoverRate != null) {
            entity.setTurnoverAnomaly(volumeRatio.multiply(turnoverRate));
        }
        entity.setAbnormalVolumeFlag((changePercent != null && volumeRatio != null)
                && ((changePercent.signum() > 0 && volumeRatio.compareTo(BigDecimal.ONE) < 0)
                || (changePercent.signum() < 0 && volumeRatio.compareTo(BigDecimal.ONE) > 0)));
        if (entity.getBidPrice() != null && entity.getAskPrice() != null) {
            BigDecimal spread = entity.getAskPrice().subtract(entity.getBidPrice());
            entity.setBidAskSpread(spread);
            if (entity.getPrice() != null && entity.getPrice().signum() != 0) {
                entity.setBidAskSpreadRate(spread.divide(entity.getPrice(), 8, java.math.RoundingMode.HALF_UP));
            }
        }
    }

    private static BigDecimal parsePositiveDecimalField(String value) {
        BigDecimal parsed = parseDecimalField(value);
        return parsed != null && parsed.signum() > 0 ? parsed : null;
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


}
// AI_GENERATE_END----------
