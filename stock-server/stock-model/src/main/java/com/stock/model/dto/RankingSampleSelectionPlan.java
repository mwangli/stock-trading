// AI_GENERATE_START -
package com.stock.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Ranking LSTM 样本选择与新闻特征读取计划。
 * 固化一次样本选择结果，确保新闻键规划与正式样本构建使用完全相同的目标索引。
 *
 * @author mwangli
 * @since 2026-10-28
 */
@Data
@Builder
public class RankingSampleSelectionPlan {

    /** 股票代码到目标样本索引集合的映射，索引基于按交易日升序的连续行情。 */
    private Map<String, Set<Integer>> selectedTargetIndices;

    /** 入选样本 60 日序列实际需要的 `股票代码|交易日` 新闻键集合。 */
    private Set<String> requiredNewsSnapshotKeys;

    /** 新闻键涉及的股票代码集合。 */
    private Set<String> requiredStockCodes;

    /** 新闻键最早交易日；未启用新闻特征时为空。 */
    private LocalDate startDate;

    /** 新闻键最晚交易日；未启用新闻特征时为空。 */
    private LocalDate endDate;

    /** 入选目标样本数量。 */
    private int selectedTargetCount;

    /** 去重后的序列股票日键数量。 */
    private int uniqueSequenceDayCount;
}
// AI_GENERATE_END -
