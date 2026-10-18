// AI_GENERATE_START -
package com.stock.dataCollector.persistence;

import com.stock.dataCollector.domain.entity.StockInfo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockInfoRepository extends JpaRepository<StockInfo, Long>, JpaSpecificationExecutor<StockInfo> {

    Optional<StockInfo> findByCode(String code);
    boolean existsByCode(String code);
    List<StockInfo> findByMarket(String market);
    List<StockInfo> findByNameContaining(String name);

    @Query("SELECT s.code FROM StockInfo s WHERE (s.name IS NULL OR "
            + "(UPPER(s.name) NOT LIKE '%ST%' AND s.name NOT LIKE '%退%')) "
            + "AND (s.securityCategory IS NULL OR (UPPER(s.securityCategory) NOT LIKE '%ST%' "
            + "AND s.securityCategory NOT LIKE '%退%' AND UPPER(s.securityCategory) NOT LIKE '%DELIST%' "
            + "AND UPPER(s.securityCategory) NOT LIKE '%TERMINATED%')) "
            + "AND (s.market IS NULL OR (UPPER(s.market) NOT LIKE '%ST%' "
            + "AND s.market NOT LIKE '%退%' AND UPPER(s.market) NOT LIKE '%DELIST%' "
            + "AND UPPER(s.market) NOT LIKE '%TERMINATED%'))")
    List<String> findAllCodes();

    List<StockInfo> findByCodeIn(List<String> codes);
    @Query("SELECT COUNT(s) FROM StockInfo s WHERE (s.name IS NULL OR "
            + "(UPPER(s.name) NOT LIKE '%ST%' AND s.name NOT LIKE '%退%')) "
            + "AND (s.securityCategory IS NULL OR (UPPER(s.securityCategory) NOT LIKE '%ST%' "
            + "AND s.securityCategory NOT LIKE '%退%' AND UPPER(s.securityCategory) NOT LIKE '%DELIST%' "
            + "AND UPPER(s.securityCategory) NOT LIKE '%TERMINATED%')) "
            + "AND (s.market IS NULL OR (UPPER(s.market) NOT LIKE '%ST%' "
            + "AND s.market NOT LIKE '%退%' AND UPPER(s.market) NOT LIKE '%DELIST%' "
            + "AND UPPER(s.market) NOT LIKE '%TERMINATED%'))")
    long countTradable();

    @Query("SELECT s FROM StockInfo s WHERE s.changePercent IS NOT NULL "
            + "AND (s.name IS NULL OR (UPPER(s.name) NOT LIKE '%ST%' AND s.name NOT LIKE '%退%')) "
            + "AND (s.securityCategory IS NULL OR (UPPER(s.securityCategory) NOT LIKE '%ST%' AND s.securityCategory NOT LIKE '%退%' AND UPPER(s.securityCategory) NOT LIKE '%DELIST%' AND UPPER(s.securityCategory) NOT LIKE '%TERMINATED%')) "
            + "AND (s.market IS NULL OR (UPPER(s.market) NOT LIKE '%ST%' AND s.market NOT LIKE '%退%' AND UPPER(s.market) NOT LIKE '%DELIST%' AND UPPER(s.market) NOT LIKE '%TERMINATED%')) "
            + "ORDER BY s.changePercent DESC")
    List<StockInfo> findTop10TradableByChangePercent(Pageable pageable);

    @Query("SELECT s FROM StockInfo s WHERE s.changePercent IS NOT NULL "
            + "AND (s.name IS NULL OR (UPPER(s.name) NOT LIKE '%ST%' AND s.name NOT LIKE '%退%')) "
            + "AND (s.securityCategory IS NULL OR (UPPER(s.securityCategory) NOT LIKE '%ST%' AND s.securityCategory NOT LIKE '%退%' AND UPPER(s.securityCategory) NOT LIKE '%DELIST%' AND UPPER(s.securityCategory) NOT LIKE '%TERMINATED%')) "
            + "AND (s.market IS NULL OR (UPPER(s.market) NOT LIKE '%ST%' AND s.market NOT LIKE '%退%' AND UPPER(s.market) NOT LIKE '%DELIST%' AND UPPER(s.market) NOT LIKE '%TERMINATED%')) "
            + "ORDER BY s.changePercent DESC")
    List<StockInfo> findTradableByChangePercentDesc(Pageable pageable);

    @Query("SELECT s FROM StockInfo s WHERE s.changePercent IS NOT NULL "
            + "AND (s.name IS NULL OR (UPPER(s.name) NOT LIKE '%ST%' AND s.name NOT LIKE '%退%')) "
            + "AND (s.securityCategory IS NULL OR (UPPER(s.securityCategory) NOT LIKE '%ST%' AND s.securityCategory NOT LIKE '%退%' AND UPPER(s.securityCategory) NOT LIKE '%DELIST%' AND UPPER(s.securityCategory) NOT LIKE '%TERMINATED%')) "
            + "AND (s.market IS NULL OR (UPPER(s.market) NOT LIKE '%ST%' AND s.market NOT LIKE '%退%' AND UPPER(s.market) NOT LIKE '%DELIST%' AND UPPER(s.market) NOT LIKE '%TERMINATED%')) "
            + "ORDER BY s.changePercent ASC")
    List<StockInfo> findTradableByChangePercentAsc(Pageable pageable);

    @Query("SELECT COUNT(s) FROM StockInfo s WHERE s.changePercent > :value "
            + "AND (s.name IS NULL OR (UPPER(s.name) NOT LIKE '%ST%' AND s.name NOT LIKE '%退%')) "
            + "AND (s.securityCategory IS NULL OR (UPPER(s.securityCategory) NOT LIKE '%ST%' AND s.securityCategory NOT LIKE '%退%' AND UPPER(s.securityCategory) NOT LIKE '%DELIST%' AND UPPER(s.securityCategory) NOT LIKE '%TERMINATED%')) "
            + "AND (s.market IS NULL OR (UPPER(s.market) NOT LIKE '%ST%' AND s.market NOT LIKE '%退%' AND UPPER(s.market) NOT LIKE '%DELIST%' AND UPPER(s.market) NOT LIKE '%TERMINATED%'))")
    long countTradableByChangePercentGreaterThan(@org.springframework.data.repository.query.Param("value") BigDecimal value);

    @Query("SELECT COUNT(s) FROM StockInfo s WHERE s.changePercent < :value "
            + "AND (s.name IS NULL OR (UPPER(s.name) NOT LIKE '%ST%' AND s.name NOT LIKE '%退%')) "
            + "AND (s.securityCategory IS NULL OR (UPPER(s.securityCategory) NOT LIKE '%ST%' AND s.securityCategory NOT LIKE '%退%' AND UPPER(s.securityCategory) NOT LIKE '%DELIST%' AND UPPER(s.securityCategory) NOT LIKE '%TERMINATED%')) "
            + "AND (s.market IS NULL OR (UPPER(s.market) NOT LIKE '%ST%' AND s.market NOT LIKE '%退%' AND UPPER(s.market) NOT LIKE '%DELIST%' AND UPPER(s.market) NOT LIKE '%TERMINATED%'))")
    long countTradableByChangePercentLessThan(@org.springframework.data.repository.query.Param("value") BigDecimal value);

    @Query("SELECT COUNT(s) FROM StockInfo s WHERE s.changePercent IS NULL "
            + "AND (s.name IS NULL OR (UPPER(s.name) NOT LIKE '%ST%' AND s.name NOT LIKE '%退%')) "
            + "AND (s.securityCategory IS NULL OR (UPPER(s.securityCategory) NOT LIKE '%ST%' AND s.securityCategory NOT LIKE '%退%' AND UPPER(s.securityCategory) NOT LIKE '%DELIST%' AND UPPER(s.securityCategory) NOT LIKE '%TERMINATED%')) "
            + "AND (s.market IS NULL OR (UPPER(s.market) NOT LIKE '%ST%' AND s.market NOT LIKE '%退%' AND UPPER(s.market) NOT LIKE '%DELIST%' AND UPPER(s.market) NOT LIKE '%TERMINATED%'))")
    long countTradableByChangePercentIsNull();

    @Query("SELECT COUNT(s) FROM StockInfo s WHERE s.changePercent = :value "
            + "AND (s.name IS NULL OR (UPPER(s.name) NOT LIKE '%ST%' AND s.name NOT LIKE '%退%')) "
            + "AND (s.securityCategory IS NULL OR (UPPER(s.securityCategory) NOT LIKE '%ST%' AND s.securityCategory NOT LIKE '%退%' AND UPPER(s.securityCategory) NOT LIKE '%DELIST%' AND UPPER(s.securityCategory) NOT LIKE '%TERMINATED%')) "
            + "AND (s.market IS NULL OR (UPPER(s.market) NOT LIKE '%ST%' AND s.market NOT LIKE '%退%' AND UPPER(s.market) NOT LIKE '%DELIST%' AND UPPER(s.market) NOT LIKE '%TERMINATED%'))")
    long countTradableByChangePercent(@org.springframework.data.repository.query.Param("value") BigDecimal value);

    @Query("SELECT COALESCE(SUM(s.totalMarketValue), 0) FROM StockInfo s WHERE (s.name IS NULL OR "
            + "(UPPER(s.name) NOT LIKE '%ST%' AND s.name NOT LIKE '%退%')) "
            + "AND (s.securityCategory IS NULL OR (UPPER(s.securityCategory) NOT LIKE '%ST%' AND s.securityCategory NOT LIKE '%退%' AND UPPER(s.securityCategory) NOT LIKE '%DELIST%' AND UPPER(s.securityCategory) NOT LIKE '%TERMINATED%')) "
            + "AND (s.market IS NULL OR (UPPER(s.market) NOT LIKE '%ST%' AND s.market NOT LIKE '%退%' AND UPPER(s.market) NOT LIKE '%DELIST%' AND UPPER(s.market) NOT LIKE '%TERMINATED%'))")
    BigDecimal sumTradableTotalMarketValue();

    @Query("SELECT COALESCE(SUM(s.turnoverRate), 0) FROM StockInfo s WHERE (s.name IS NULL OR "
            + "(UPPER(s.name) NOT LIKE '%ST%' AND s.name NOT LIKE '%退%')) "
            + "AND (s.securityCategory IS NULL OR (UPPER(s.securityCategory) NOT LIKE '%ST%' AND s.securityCategory NOT LIKE '%退%' AND UPPER(s.securityCategory) NOT LIKE '%DELIST%' AND UPPER(s.securityCategory) NOT LIKE '%TERMINATED%')) "
            + "AND (s.market IS NULL OR (UPPER(s.market) NOT LIKE '%ST%' AND s.market NOT LIKE '%退%' AND UPPER(s.market) NOT LIKE '%DELIST%' AND UPPER(s.market) NOT LIKE '%TERMINATED%'))")
    BigDecimal sumTradableTurnoverRate();

    @Query("SELECT COALESCE(AVG(s.changePercent), 0) FROM StockInfo s WHERE s.changePercent IS NOT NULL "
            + "AND (s.name IS NULL OR (UPPER(s.name) NOT LIKE '%ST%' AND s.name NOT LIKE '%退%')) "
            + "AND (s.securityCategory IS NULL OR (UPPER(s.securityCategory) NOT LIKE '%ST%' AND s.securityCategory NOT LIKE '%退%' AND UPPER(s.securityCategory) NOT LIKE '%DELIST%' AND UPPER(s.securityCategory) NOT LIKE '%TERMINATED%')) "
            + "AND (s.market IS NULL OR (UPPER(s.market) NOT LIKE '%ST%' AND s.market NOT LIKE '%退%' AND UPPER(s.market) NOT LIKE '%DELIST%' AND UPPER(s.market) NOT LIKE '%TERMINATED%'))")
    Double avgTradableChangePercent();

    void deleteByCode(String code);
}
// AI_GENERATE_END -
