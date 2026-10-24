// AI_GENERATE_START---------
package com.stock.decision.repository;

import com.stock.decision.entity.StrategyRuntimeState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 策略运行状态仓储。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Repository
public interface StrategyRuntimeStateRepository extends JpaRepository<StrategyRuntimeState, String> {

    Optional<StrategyRuntimeState> findByStateKey(String stateKey);
}
// AI_GENERATE_END---------
