// AI_GENERATE_START------
package com.stock.agent.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.time.LocalDate;

/**
 * Agent 本次任务的受限证据集合。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Value
@Builder
public class EvidenceBundle {

    /** 传给模型的证据摘要。 */
    String summary;

    /** 证据明细。 */
    List<EvidenceItem> items;

    /** 证据编号列表。 */
    List<String> evidenceRefs;

    /** 候选批次事实中的预测批次标识。 */
    String predictionBatchId;

    /** 候选批次事实中的策略版本。 */
    String strategyVersion;

    /** 候选批次事实中的目标交易日。 */
    LocalDate targetTradeDate;
}
// AI_GENERATE_END-----