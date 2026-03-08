package com.stock.dataCollector.persistence;

import com.stock.dataCollector.domain.entity.StockInfo;
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

    @Query("SELECT s.code FROM StockInfo s")
    List<String> findAllCodes();

    List<StockInfo> findByCodeIn(List<String> codes);
    long countBy();

    List<StockInfo> findTop10ByChangePercentIsNotNullOrderByChangePercentDesc();
    Optional<StockInfo> findTop1ByChangePercentIsNotNullOrderByChangePercentDesc();
    Optional<StockInfo> findTop1ByChangePercentIsNotNullOrderByChangePercentAsc();
    long countByChangePercentGreaterThan(BigDecimal value);
    long countByChangePercentLessThan(BigDecimal value);
    long countByChangePercentIsNull();
    long countByChangePercent(BigDecimal value);

    @Query("SELECT COALESCE(SUM(s.totalMarketValue), 0) FROM StockInfo s")
    BigDecimal sumTotalMarketValue();

    @Query("SELECT COALESCE(SUM(s.turnoverRate), 0) FROM StockInfo s")
    BigDecimal sumTurnoverRate();

    @Query("SELECT COALESCE(AVG(s.changePercent), 0) FROM StockInfo s WHERE s.changePercent IS NOT NULL")
    Double avgChangePercent();
}
