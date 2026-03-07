package com.stock.dataCollector.repository;

import com.stock.dataCollector.entity.StockInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

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
    @org.springframework.data.jpa.repository.Query("SELECT s.code FROM StockInfo s")
    List<String> findAllCodes();

    /**
     * 根据股票代码集合批量查询
     */
    List<StockInfo> findByCodeIn(List<String> codes);

    /**
     * 统计总数
     */
    long countBy();
}