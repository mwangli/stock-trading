// AI_GENERATE_START -
package com.stock.strategyAnalysis.persistence;

import com.stock.strategyAnalysis.domain.entity.StrategyRuntimeState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * 加锁读取策略运行状态，保证多实例更新顺序一致。
     *
     * @param stateKey 单例状态键
     * @return 当前策略运行状态
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from StrategyRuntimeState state where state.stateKey = :stateKey")
    Optional<StrategyRuntimeState> findForUpdate(@Param("stateKey") String stateKey);
}
// AI_GENERATE_END -
