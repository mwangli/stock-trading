// AI_GENERATE_START------
package com.stock.agent.decision.repository;

import com.stock.agent.decision.entity.AgentTradeDecisionEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Agent 影子交易判断仓储。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Repository
public interface AgentTradeDecisionRepository extends MongoRepository<AgentTradeDecisionEntity, String> {

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
     * 查询候选在指定模式和策略版本下最近生成的持久化判断。
     *
     * @param candidateBatchId 候选批次标识
     * @param stockCode 股票代码
     * @param decisionMode 决策参与模式
     * @param strategyVersion 策略配置版本
     * @return 最近的匹配判断
     */
    Optional<AgentTradeDecisionEntity>
    findFirstByCandidateBatchIdAndStockCodeAndDecisionModeAndStrategyVersionOrderByCreatedAtDesc(
            String candidateBatchId, String stockCode, String decisionMode, String strategyVersion);

    /**
     * 查询候选批次的全部 Agent 判断。
     *
     * @param candidateBatchId 候选批次标识
     * @return 按创建时间升序排列的判断
     */
    List<AgentTradeDecisionEntity> findByCandidateBatchIdOrderByCreatedAtAsc(String candidateBatchId);

    List<AgentTradeDecisionEntity> findAllByOrderByCreatedAtAsc();

    /**
     * 查询统计时间窗口内的 Agent 判断。
     *
     * @param from 起始时间，包含
     * @param to 结束时间，包含
     * @return 按创建时间升序排列的判断
     */
    List<AgentTradeDecisionEntity> findByCreatedAtBetweenOrderByCreatedAtAsc(
            LocalDateTime from, LocalDateTime to);
}
// AI_GENERATE_END------
