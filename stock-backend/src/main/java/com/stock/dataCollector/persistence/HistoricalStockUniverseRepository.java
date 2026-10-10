// AI_GENERATE_START -----
package com.stock.dataCollector.persistence;

import com.stock.dataCollector.domain.entity.HistoricalStockUniverseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 历史股票池 Repository。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface HistoricalStockUniverseRepository
        extends JpaRepository<HistoricalStockUniverseEntity, Long> {

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
     * 统计与指定回测区间相交的历史股票版本数量。
     *
     * @param startDate 回测开始日期
     * @param endDate 回测结束日期
     * @return 相交版本数量
     */
    @Query("SELECT COUNT(u) FROM HistoricalStockUniverseEntity u "
            + "WHERE u.effectiveFrom <= :endDate "
            + "AND (u.effectiveTo IS NULL OR u.effectiveTo >= :startDate)")
    long countOverlapping(@Param("startDate") LocalDate startDate,
                          @Param("endDate") LocalDate endDate);

    /**
     * 查询最早的历史股票池版本。
     *
     * @return 最早版本
     */
    Optional<HistoricalStockUniverseEntity> findFirstByOrderByEffectiveFromAsc();

    /**
     * 统计指定日期有效的股票数量。
     *
     * @param date 业务日期
     * @return 有效股票数量
     */
    @Query("SELECT COUNT(DISTINCT u.stockCode) FROM HistoricalStockUniverseEntity u "
            + "WHERE u.effectiveFrom <= :date "
            + "AND (u.effectiveTo IS NULL OR u.effectiveTo >= :date) "
            + "AND (u.listedDate IS NULL OR u.listedDate <= :date) "
            + "AND (u.delistedDate IS NULL OR u.delistedDate >= :date)")
    long countActiveOn(@Param("date") LocalDate date);

    /**
     * 查询与日期区间相交的历史股票版本。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 历史股票版本
     */
    @Query("SELECT u FROM HistoricalStockUniverseEntity u "
            + "WHERE u.effectiveFrom <= :endDate "
            + "AND (u.effectiveTo IS NULL OR u.effectiveTo >= :startDate) "
            + "ORDER BY u.stockCode ASC, u.effectiveFrom ASC")
    List<HistoricalStockUniverseEntity> findOverlapping(@Param("startDate") LocalDate startDate,
                                                         @Param("endDate") LocalDate endDate);

    /**
     * 查询指定来源版本在业务日期有效的股票池。
     *
     * @param source 股票池来源
     * @param sourceVersion 股票池来源版本
     * @param date 业务日期
     * @return 当日有效股票版本
     */
    @Query("SELECT u FROM HistoricalStockUniverseEntity u "
            + "WHERE u.source = :source AND u.sourceVersion = :sourceVersion "
            + "AND u.effectiveFrom <= :date "
            + "AND (u.effectiveTo IS NULL OR u.effectiveTo >= :date) "
            + "AND (u.listedDate IS NULL OR u.listedDate <= :date) "
            + "AND (u.delistedDate IS NULL OR u.delistedDate >= :date) "
            + "ORDER BY u.stockCode ASC, u.effectiveFrom DESC")
    List<HistoricalStockUniverseEntity> findActiveOn(
            @Param("source") String source,
            @Param("sourceVersion") String sourceVersion,
            @Param("date") LocalDate date);
}
// AI_GENERATE_END -----
