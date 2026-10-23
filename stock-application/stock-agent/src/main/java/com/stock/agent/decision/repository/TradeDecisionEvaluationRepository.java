// AI_GENERATE_START-----
package com.stock.agent.decision.repository;

import com.stock.agent.decision.entity.TradeDecisionEvaluationEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 影子交易判断评估仓储。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Repository
public interface TradeDecisionEvaluationRepository
        extends MongoRepository<TradeDecisionEvaluationEntity, String> {

    /**
     * 根据 Agent 判断查询评估结果。
     *
     * @param decisionId Agent 判断唯一标识
     * @return 已存在的评估结果
     */
    Optional<TradeDecisionEvaluationEntity> findByDecisionId(String decisionId);

    boolean existsByDecisionId(String decisionId);

    /**
     * 查询候选批次的全部评估结果。
     *
     * @param candidateBatchId 候选批次标识
     * @return 按评估时间升序排列的结果
     */
    List<TradeDecisionEvaluationEntity> findByCandidateBatchIdOrderByEvaluatedAtAsc(
            String candidateBatchId);

    /**
     * 查询等待行情补齐的历史评估。
     *
     * @param marketDataStatus 行情状态
     * @return 最早等待的最多一百条评估
     */
    List<TradeDecisionEvaluationEntity> findTop100ByMarketDataStatusOrderByEvaluatedAtAsc(
            String marketDataStatus);
}
// AI_GENERATE_END-----
