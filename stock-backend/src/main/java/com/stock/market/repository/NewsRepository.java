// AI_GENERATE_START---------
package com.stock.market.repository;

import com.stock.market.entity.StockNews;
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
     * 查询指定股票当前已知的最新新闻或公告。
     *
     * @param stockCode 股票代码
     * @return 最新新闻或公告，不存在时返回 empty
     */
    Optional<StockNews> findFirstByStockCodeOrderByPublishTimeDesc(String stockCode);

    /**
     * 查询指定时间之后最近的股票新闻和公告。
     *
     * @param stockCode 股票代码
     * @param publishTime 最早发布时间
     * @return 最近最多 20 条新闻和公告
     */
    List<StockNews> findTop20ByStockCodeAndPublishTimeAfterOrderByPublishTimeDesc(
            String stockCode, LocalDateTime publishTime);

    /**
     * 查询指定股票在决策窗口内最近的五条新闻和公告。
     *
     * @param stockCode 股票代码
     * @param startTime 窗口开始时间，包含
     * @param endTime 信息截止时间，包含
     * @return 按发布时间倒序排列的有限新闻快照来源
     */
    List<StockNews> findTop5ByStockCodeAndPublishTimeBetweenOrderByPublishTimeDesc(
            String stockCode, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 批量查询多个股票在指定时间之后的新闻和公告。
     *
     * @param stockCodes 股票代码集合
     * @param publishTime 最早发布时间
     * @return 按发布时间倒序排列的新闻和公告
     */
    List<StockNews> findByStockCodeInAndPublishTimeAfterOrderByPublishTimeDesc(
            List<String> stockCodes, LocalDateTime publishTime);

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
// AI_GENERATE_END---------
