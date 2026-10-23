// AI_GENERATE_START-----------
package com.stock.data.repository;

import com.stock.data.entity.HistoricalStockUniverseEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 历史股票池 Repository。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface HistoricalStockUniverseRepository
        extends MongoRepository<HistoricalStockUniverseEntity, String> {

    /**
     * 查询同一来源和来源版本下指定生效日的股票版本。
     *
     * @param stockCode 股票代码
     * @param effectiveFrom 生效日期
     * @param source 数据来源
     * @param sourceVersion 数据来源版本
     * @return 历史股票版本
     */
    Optional<HistoricalStockUniverseEntity> findByStockCodeAndEffectiveFromAndSourceAndSourceVersion(
            String stockCode, LocalDate effectiveFrom, String source, String sourceVersion);

    /**
     * 查询最早的历史股票池版本。
     *
     * @return 最早版本
     */
    Optional<HistoricalStockUniverseEntity> findFirstByOrderByEffectiveFromAsc();

}
// AI_GENERATE_END-----------
