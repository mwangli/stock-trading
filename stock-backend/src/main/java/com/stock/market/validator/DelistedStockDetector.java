// AI_GENERATE_START-----
package com.stock.market.validator;

import com.stock.market.entity.StockInfo;

import java.util.Locale;

/**
 * 统一判断股票是否属于不可展示或不可继续采集的退市类标的。
 *
 * @author mwangli
 * @since 2026-10-18
 */
public final class DelistedStockDetector {

    private DelistedStockDetector() {
    }

    /**
     * 根据股票名称、证券类别和市场字段识别退市类标的。
     * 名称中的 ST 也纳入过滤，避免风险警示标的继续进入行情和模型数据链路。
     *
     * @param stock 股票信息
     * @return true 表示应过滤并清理关联数据
     */
    public static boolean isDelisted(StockInfo stock) {
        if (stock == null) {
            return false;
        }
        return containsDelistedKeyword(stock.getName())
                || containsDelistedKeyword(stock.getSecurityCategory())
                || containsDelistedKeyword(stock.getMarket());
    }

    /**
     * 判断文本是否包含退市或风险警示状态。
     *
     * @param value 数据源状态文本
     * @return true 表示命中状态关键词
     */
    public static boolean containsDelistedKeyword(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.contains("退")
                || normalized.contains("ST")
                || normalized.contains("PT")
                || normalized.contains("DELIST")
                || normalized.contains("TERMINATED")
                || normalized.contains("终止上市");
    }
}
// AI_GENERATE_END-----
