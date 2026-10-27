// AI_GENERATE_START -
package com.stock.model.enums;

/**
 * 情感训练标签证据质量。
 *
 * @author mwangli
 * @since 2026-10-25
 */
public enum SentimentLabelQuality {
    /** 独立固定审计集中的人工复核标签。 */
    GOLD,
    /** 多来源模型、事件规则和未来收益一致时生成的高置信自动标签。 */
    SILVER,
    /** 单一关键词或启发式规则生成的弱标签。 */
    WEAK
}
// AI_GENERATE_END -
