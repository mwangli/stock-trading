package com.stock.databus.repository;

import com.stock.databus.entity.StockNews;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NewsRepository extends MongoRepository<StockNews, String> {

    List<StockNews> findByStockCode(String stockCode);

    List<StockNews> findByPubTimeAfterOrderByPubTimeDesc(LocalDateTime pubTime);

    List<StockNews> findByStockCodeAndPubTimeAfter(String stockCode, LocalDateTime pubTime);
}
