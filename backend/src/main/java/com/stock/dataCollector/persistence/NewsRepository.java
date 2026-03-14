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

    List<StockNews> findByPublishTimeBetweenOrderByPublishTimeDesc(LocalDateTime startTime, LocalDateTime endTime);

    void deleteByStockCode(String stockCode);

    /**
     * 根据证券平台外部 ID 查询，用于采集时去重
     *
     * @param externalId 证券平台新闻 ID
     * @return 已存在的新闻，若不存在则 empty
     */
    Optional<StockNews> findByExternalId(String externalId);

    /**
     * 判断指定外部 ID 的新闻是否已存在
     */
    boolean existsByExternalId(String externalId);

    /**
     * 批量查询已存在的外部 ID，用于方案 A 批量预查去重
     * 使用 projection 仅返回 externalId，避免传输 content 等大字段，提升查询性能
     *
     * @param externalIds 待检查的外部 ID 列表
     * @return 已存在的新闻（仅 externalId 有值），用于提取 Set
     */
    @Query(value = "{ 'externalId' : { $in : ?0 } }", fields = "{ 'externalId' : 1, '_id' : 0 }")
    List<StockNews> findExternalIdsByExternalIdIn(List<String> externalIds);
}
