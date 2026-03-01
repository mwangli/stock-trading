package com.stock.dataCollector.repository.mysql;

import com.stock.dataCollector.entity.mysql.StockInfoMySql;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 股票信息MySQL数据访问接口
 */
@Repository
public interface StockInfoMySqlRepository extends JpaRepository<StockInfoMySql, Long>, JpaSpecificationExecutor<StockInfoMySql> {

    /**
     * 根据股票代码查询
     */
    Optional<StockInfoMySql> findByCode(String code);

    /**
     * 检查股票是否存在
     */
    boolean existsByCode(String code);

    /**
     * 根据市场查询
     */
    List<StockInfoMySql> findByMarket(String market);

    /**
     * 根据股票名称模糊查询
     */
    List<StockInfoMySql> findByNameContaining(String name);

    /**
     * 查询所有股票代码
     */
    @org.springframework.data.jpa.repository.Query("SELECT s.code FROM StockInfoMySql s")
    List<String> findAllCodes();

    /**
     * 统计总数
     */
    long countBy();
}