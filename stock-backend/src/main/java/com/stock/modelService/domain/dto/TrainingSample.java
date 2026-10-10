// AI_GENERATE_START -
package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    /** 关联股票代码，保留旧字段名以兼容现有 DJL 数据集。 */
    private String source;

    /** 可追溯的新闻样本标识，优先使用股票代码与外部新闻 ID。 */
    private String sampleId;

    /** 新闻或公告发布时间，用于按时间划分数据集。 */
    private LocalDateTime publishedAt;
}
// AI_GENERATE_END -
