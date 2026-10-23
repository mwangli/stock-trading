// AI_GENERATE_START-------
package com.stock.strategy.repository;

import com.stock.strategy.entity.StrategyRuntimeState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 策略运行状态仓储。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Repository
public interface StrategyRuntimeStateRepository extends MongoRepository<StrategyRuntimeState, String> {

    Optional<StrategyRuntimeState> findByStateKey(String stateKey);
}
// AI_GENERATE_END-------
