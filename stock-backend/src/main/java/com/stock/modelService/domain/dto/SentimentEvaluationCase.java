// AI_GENERATE_START -
package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 情感模型人工标注评估样本。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SentimentEvaluationCase {

    /** 样本唯一标识。 */
    private String id;

    /** 待评估新闻或公告文本。 */
    private String text;

    /** 人工标注标签：positive、neutral 或 negative。 */
    private String expectedLabel;
}
// AI_GENERATE_END -
