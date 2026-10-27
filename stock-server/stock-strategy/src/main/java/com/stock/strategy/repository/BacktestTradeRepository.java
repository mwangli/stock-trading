// AI_GENERATE_START-----------
package com.stock.strategy.repository;

import com.stock.strategy.entity.BacktestTradeEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 回测逐笔交易 Repository。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface BacktestTradeRepository extends MongoRepository<BacktestTradeEntity, String> {

    /**
     * 按回测结果标识查询逐笔交易。
     *
     * @param resultId 回测结果标识
     * @return 按序号升序排列的逐笔交易
     */
    List<BacktestTradeEntity> findByResultIdOrderBySequenceNoAsc(String resultId);
}
// AI_GENERATE_END-----------
