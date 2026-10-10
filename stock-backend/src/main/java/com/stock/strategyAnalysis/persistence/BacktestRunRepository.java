// AI_GENERATE_START --
package com.stock.strategyAnalysis.persistence;

import com.stock.strategyAnalysis.domain.entity.BacktestRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 回测运行记录 Repository。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface BacktestRunRepository extends JpaRepository<BacktestRunEntity, Long> {

    /**
     * 按结果标识查询回测运行。
     *
     * @param resultId 回测结果标识
     * @return 回测运行记录
     */
    Optional<BacktestRunEntity> findByResultId(String resultId);
}
// AI_GENERATE_END --
