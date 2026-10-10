// AI_GENERATE_START --
package com.stock.strategyAnalysis.persistence;

import com.stock.strategyAnalysis.domain.entity.BacktestTradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 回测逐笔交易 Repository。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface BacktestTradeRepository extends JpaRepository<BacktestTradeEntity, Long> {

    /**
     * 按回测结果标识查询逐笔交易。
     *
     * @param resultId 回测结果标识
     * @return 按序号升序排列的逐笔交易
     */
    List<BacktestTradeEntity> findByResultIdOrderBySequenceNoAsc(String resultId);
}
// AI_GENERATE_END --
