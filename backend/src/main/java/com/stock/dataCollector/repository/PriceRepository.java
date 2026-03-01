package com.stock.dataCollector.repository;

import com.stock.dataCollector.entity.StockPrice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 股票价格数据访问接口
 */
@Repository
public interface PriceRepository extends MongoRepository<StockPrice, String> {

    /**
     * 根据股票代码查询
     */
    List<StockPrice> findByCodeOrderByDateAsc(String code);

    /**
     * 根据股票代码和日期范围查询
     */
    List<StockPrice> findByCodeAndDateBetweenOrderByDateAsc(
        String code, 
        LocalDate startDate, 
        LocalDate endDate
    );

    /**
     * 根据股票代码和日期查询
     */
    Optional<StockPrice> findByCodeAndDate(String code, LocalDate date);

    /**
     * 删除指定股票代码的所有数据
     */
    void deleteByCode(String code);

    /**
     * 检查是否存在某日期的数据
     */
    boolean existsByCodeAndDate(String code, LocalDate date);
}