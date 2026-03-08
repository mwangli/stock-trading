package com.stock.dataCollector.persistence;

import com.stock.dataCollector.domain.entity.StockNews;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NewsRepository extends MongoRepository<StockNews, String> {

    List<StockNews> findByStockCodeOrderByPublishTimeDesc(String stockCode);
    List<StockNews> findByPublishTimeBetweenOrderByPublishTimeDesc(LocalDateTime startTime, LocalDateTime endTime);
    void deleteByStockCode(String stockCode);
}
