// AI_GENERATE_START --
package com.stock.strategyAgent.decision;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 不可变交易决策上下文仓储。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Repository
public interface TradeDecisionContextRepository extends JpaRepository<TradeDecisionContextEntity, String> {

    /**
     * 根据上下文哈希查询已固化快照。
     *
     * @param contextHash 上下文 SHA-256
     * @return 已存在的上下文
     */
    Optional<TradeDecisionContextEntity> findByContextHash(String contextHash);

}
// AI_GENERATE_END --
