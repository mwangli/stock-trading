// AI_GENERATE_START -
package com.stock.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 情感模型统一输入契约。
 * 保存标题优先、关键句筛选和分块后的文本，确保不同模型消费相同事实。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Builder
public class SentimentModelInput {

    /** 原始标题，可为空。 */
    private String title;

    /** 原始正文，可为空。 */
    private String content;

    /** 最多三个按重要性排序的模型输入分块。 */
    private List<String> chunks;

    /** 原始有效字符数。 */
    private int originalCharacterCount;

    /** 实际送入模型的去重字符数。 */
    private int includedCharacterCount;

    /** 文本覆盖率，范围 0 到 1。 */
    private double textCoverage;
}
// AI_GENERATE_END -
