// AI_GENERATE_START-----
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.stock.model.enums.SentimentLabelQuality;

import java.time.LocalDateTime;

/**
 * 情感模型训练样本。
 *
 * @author mwangli
 * @since 2026-10-09
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrainingSample {

    /** 清洗后的新闻或公告文本。 */
    private String text;

    /** 情感标签：0 中性、1 正面、2 负面。 */
    private Integer label;

    /** 标签证据质量，规则关键词标签固定为 WEAK。 */
    private SentimentLabelQuality labelQuality;

    /** 标签生成方法，例如 RULE_KEYWORD、CONSENSUS_RETURN 或 HUMAN_AUDIT。 */
    private String labelMethod;

    /** 标签置信度，范围 0 到 1。 */
    private Double labelConfidence;

    /** 关联股票代码，保留旧字段名以兼容现有 DJL 数据集。 */
    private String source;

    /** 可追溯的新闻样本标识，优先使用股票代码与外部新闻 ID。 */
    private String sampleId;

    /** 新闻或公告发布时间，用于按时间划分数据集。 */
    private LocalDateTime publishedAt;
}
// AI_GENERATE_END-----
