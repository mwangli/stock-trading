package com.stock.databus.repository;

import com.stock.databus.entity.StockInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockRepository extends JpaRepository<StockInfo, Long> {

    @Query("SELECT s FROM StockInfo s WHERE s.isTradable = 1 AND s.deleted = '0'")
    List<StockInfo> findTradableStocks(int limit);

    StockInfo findByCode(String code);

    @Query("SELECT COUNT(s) FROM StockInfo s WHERE s.deleted = '0'")
    int countAll();

    @Query("SELECT s FROM StockInfo s WHERE s.market = :market AND s.isTradable = 1 AND s.deleted = '0'")
    List<StockInfo> findByMarket(@Param("market") String market);
}
