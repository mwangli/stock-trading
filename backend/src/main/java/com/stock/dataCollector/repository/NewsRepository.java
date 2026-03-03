package com.stock.dataCollector.repository;

import com.stock.dataCollector.entity.StockNews;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 股票新闻数据访问接口
 * TODO: 待实现新闻采集功能
 */
@Repository
public interface NewsRepository extends MongoRepository<StockNews, String> {

    /**
     * 根据股票代码查询新闻
     */
    List<StockNews> findByStockCodeOrderByPublishTimeDesc(String stockCode);

    /**
     * 根据时间范围查询新闻
     */
    List<StockNews> findByPublishTimeBetweenOrderByPublishTimeDesc(
        LocalDateTime startTime, 
        LocalDateTime endTime
    );

    /**
     * 删除指定股票代码的新闻
     */
    void deleteByStockCode(String stockCode);
}