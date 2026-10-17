// AI_GENERATE_START --
package com.stock.strategyAgent.strategy.persistence;

import com.stock.strategyAgent.strategy.domain.entity.StrategyEvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 策略评估快照仓库。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Repository
public interface StrategyEvaluationRepository extends JpaRepository<StrategyEvaluationEntity, Long> {

    /**
     * 查询最近一条评估快照，用于定位最近一次运行。
     *
     * @return 最近评估快照
     */
    Optional<StrategyEvaluationEntity> findTopByOrderByCalculatedAtDesc();

    /**
     * 查询指定运行的全部模式结果。
     *
     * @param evaluationRunId 评估运行标识
     * @return 三模式评估结果
     */
    List<StrategyEvaluationEntity> findByEvaluationRunIdOrderByStrategyModeAsc(String evaluationRunId);
}
// AI_GENERATE_END --
