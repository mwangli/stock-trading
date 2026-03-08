package com.stock.dataCollector.repository;

import com.stock.dataCollector.entity.StockInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 股票信息数据访问接口
 */
@Repository
public interface StockInfoRepository extends JpaRepository<StockInfo, Long>, JpaSpecificationExecutor<StockInfo> {

    /**
     * 根据股票代码查询
     */
    Optional<StockInfo> findByCode(String code);

    /**
     * 检查股票是否存在
     */
    boolean existsByCode(String code);

    /**
     * 根据市场查询
     */
    List<StockInfo> findByMarket(String market);

    /**
     * 根据股票名称模糊查询
     */
    List<StockInfo> findByNameContaining(String name);

    /**
     * 查询所有股票代码
     */
    @Query("SELECT s.code FROM StockInfo s")
    List<String> findAllCodes();

    /**
     * 根据股票代码集合批量查询
     */
    List<StockInfo> findByCodeIn(List<String> codes);

    /**
     * 统计总数
     */
    long countBy();

    // ---------- 涨幅榜 / 市场统计用，避免全表加载 ----------

    /** 涨幅榜 TOP10：按涨跌幅降序，仅查 10 条 */
    List<StockInfo> findTop10ByChangePercentIsNotNullOrderByChangePercentDesc();

    /** 领涨 1 条：涨跌幅最大 */
    Optional<StockInfo> findTop1ByChangePercentIsNotNullOrderByChangePercentDesc();

    /** 领跌 1 条：涨跌幅最小 */
    Optional<StockInfo> findTop1ByChangePercentIsNotNullOrderByChangePercentAsc();

    /** 涨跌幅 &gt; 0 的数量 */
    long countByChangePercentGreaterThan(BigDecimal value);

    /** 涨跌幅 &lt; 0 的数量 */
    long countByChangePercentLessThan(BigDecimal value);

    /** 涨跌幅为 null 的数量（算平盘） */
    long countByChangePercentIsNull();

    /** 涨跌幅等于某值的数量（如 0 为平盘） */
    long countByChangePercent(BigDecimal value);

    /** 总市值之和（聚合，不加载实体） */
    @Query("SELECT COALESCE(SUM(s.totalMarketValue), 0) FROM StockInfo s")
    BigDecimal sumTotalMarketValue();

    /** 换手率之和（用于算平均换手率） */
    @Query("SELECT COALESCE(SUM(s.turnoverRate), 0) FROM StockInfo s")
    BigDecimal sumTurnoverRate();

    /** 有效涨跌幅的平均值（仅非 null） */
    @Query("SELECT COALESCE(AVG(s.changePercent), 0) FROM StockInfo s WHERE s.changePercent IS NOT NULL")
    Double avgChangePercent();
}