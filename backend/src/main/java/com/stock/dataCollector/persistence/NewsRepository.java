package com.stock.dataCollector.persistence;

import com.stock.dataCollector.domain.entity.StockNews;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 股票新闻仓储
 *
 * @author mwangli
 * @since 2026-03-14
 */
@Repository
public interface NewsRepository extends MongoRepository<StockNews, String> {

    List<StockNews> findByStockCodeOrderByPublishTimeDesc(String stockCode);

    /**
     * 统计指定股票的新闻+公告总数，用于采集前判断是否可跳过
     */
    long countByStockCode(String stockCode);

    List<StockNews> findByPublishTimeBetweenOrderByPublishTimeDesc(LocalDateTime startTime, LocalDateTime endTime);

    void deleteByStockCode(String stockCode);

    /**
     * 根据股票代码与证券平台外部 ID 查询，用于采集时去重
     *
     * @param stockCode  股票代码
     * @param externalId 证券平台新闻 ID
     * @return 已存在的新闻，若不存在则 empty
     */
    Optional<StockNews> findByStockCodeAndExternalId(String stockCode, String externalId);

    /**
     * 批量查询指定股票下已存在的外部 ID，用于方案 A 批量预查去重
     * 同一条新闻可关联多只股票，需按 (stockCode, externalId) 联合判重
     * 使用 projection 仅返回 externalId，避免传输 content 等大字段
     *
     * @param stockCode  股票代码
     * @param externalIds 待检查的外部 ID 列表
     * @return 已存在的新闻（仅 externalId 有值），用于提取 Set
     */
    @Query(value = "{ 'stockCode' : ?0, 'externalId' : { $in : ?1 } }", fields = "{ 'externalId' : 1, '_id' : 0 }")
    List<StockNews> findExternalIdsByStockCodeAndExternalIdIn(String stockCode, List<String> externalIds);
}
