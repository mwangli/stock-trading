// AI_GENERATE_START -
package com.stock.modelService.domain.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 股票新闻事件情感聚合结果。
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Data
@Builder
public class SentimentAggregateResult {

    /** 按事件权重聚合后的情感得分，范围为 -1 到 1。 */
    private double score;

    /** 新闻覆盖和模型置信度合成值，范围为 0 到 1。 */
    private double confidence;

    /** 原始新闻和公告数量。 */
    private int newsCount;

    /** 去重后的独立事件数量。 */
    private int uniqueEventCount;

    /** 是否包含应阻断买入的强负面事件。 */
    private boolean strongNegative;

    /** 触发强负面标志的事件标题。 */
    private String strongNegativeTitle;
}
// AI_GENERATE_END -
