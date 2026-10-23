// AI_GENERATE_START-----
package com.stock.agent.decision.repository;

import com.stock.agent.decision.entity.TradeDecisionModeConfigEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Agent 交易决策参与模式配置仓储。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Repository
public interface TradeDecisionModeConfigRepository
        extends MongoRepository<TradeDecisionModeConfigEntity, Long> {
}
// AI_GENERATE_END-----
