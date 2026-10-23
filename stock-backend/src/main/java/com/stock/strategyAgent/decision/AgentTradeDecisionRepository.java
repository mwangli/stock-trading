// AI_GENERATE_START -
package com.stock.strategyAgent.decision;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 影子交易判断仓储。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Repository
public interface AgentTradeDecisionRepository extends JpaRepository<AgentTradeDecisionEntity, String> {

    /**
     * 根据上下文、提示词版本和参与模式查询幂等判断。
     *
     * @param contextHash 上下文 SHA-256
     * @param promptVersion 提示词版本
     * @param decisionMode 决策参与模式
     * @return 已存在的判断
     */
    Optional<AgentTradeDecisionEntity> findByContextHashAndPromptVersionAndDecisionMode(
            String contextHash, String promptVersion, String decisionMode);

    /**
     * 查询候选批次的全部 Agent 判断。
     *
     * @param candidateBatchId 候选批次标识
     * @return 按创建时间升序排列的判断
     */
    List<AgentTradeDecisionEntity> findByCandidateBatchIdOrderByCreatedAtAsc(String candidateBatchId);
}
// AI_GENERATE_END -
