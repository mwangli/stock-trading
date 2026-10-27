// AI_GENERATE_START----------
package com.stock.agent.repository;

import com.stock.agent.entity.AgentTradeDecisionEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Agent 交易建议仓储。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Repository
public interface AgentTradeDecisionRepository extends MongoRepository<AgentTradeDecisionEntity, String> {

    /**
     * 根据上下文和提示词版本查询幂等判断。
     *
     * @param contextHash 上下文 SHA-256
     * @param promptVersion 提示词版本
     * @return 已存在的判断
     */
    Optional<AgentTradeDecisionEntity> findByContextHashAndPromptVersion(
            String contextHash, String promptVersion);

    /**
     * 查询候选批次的全部 Agent 判断。
     *
     * @param candidateBatchId 候选批次标识
     * @return 按创建时间升序排列的判断
     */
    List<AgentTradeDecisionEntity> findByCandidateBatchIdOrderByCreatedAtAsc(String candidateBatchId);

    /**
     * 查询全部交易建议并按创建时间升序排列。
     *
     * @return 全部交易建议
     */
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
// AI_GENERATE_END----------