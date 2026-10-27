// AI_GENERATE_START------
package com.stock.data.service;

import com.mongodb.client.result.UpdateResult;
import com.stock.data.entity.StockInfo;
import com.stock.data.entity.StockNews;
import com.stock.data.enums.NewsSyncStatus;
import com.stock.data.repository.NewsRepository;
import com.stock.data.repository.StockInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 股票新闻同步状态服务。
 *
 * 负责维护股票维度的新闻同步批次、结果状态、最新发布时间和下次同步水位。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
class StockNewsSyncStateService {

    private final NewsRepository newsRepository;
    private final StockInfoRepository stockInfoRepository;
    private final MongoTemplate mongoTemplate;

    StockInfo markStarted(String stockCode, String batchId) {
        StockInfo stockInfo = stockInfoRepository.findByCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("股票代码不存在: " + stockCode));
        LocalDateTime startedAt = LocalDateTime.now();
        Update update = new Update()
                .set("newsLastAttemptAt", startedAt)
                .set("newsSyncBatchId", batchId)
                .set("newsSyncStatus", NewsSyncStatus.RUNNING)
                .unset("newsSyncErrorCode")
                .set("updateTime", startedAt);
        UpdateResult result = mongoTemplate.updateFirst(stockCodeQuery(stockCode), update, StockInfo.class);
        if (result.getMatchedCount() != 1) {
            throw new IllegalStateException("股票新闻同步状态更新失败: stockCode=" + stockCode);
        }
        stockInfo.setNewsLastAttemptAt(startedAt);
        stockInfo.setNewsSyncBatchId(batchId);
        stockInfo.setNewsSyncStatus(NewsSyncStatus.RUNNING);
        stockInfo.setNewsSyncErrorCode(null);
        return stockInfo;
    }

    void complete(StockInfo stockInfo, NewsSyncStatus status, int savedCount, String errorCode) {
        LocalDateTime completedAt = LocalDateTime.now();
        Optional<LocalDateTime> latestPublishTime = newsRepository
                .findFirstByStockCodeOrderByPublishTimeDesc(stockInfo.getCode())
                .map(StockNews::getPublishTime);
        Update update = new Update()
                .set("newsSyncStatus", status)
                .set("newsNextSyncAt", isSuccessful(status)
                        ? completedAt.plusDays(1) : completedAt.plusMinutes(15))
                .set("updateTime", completedAt);
        latestPublishTime.ifPresent(value -> update.set("newsLastPublishTime", value));
        if (errorCode == null) {
            update.unset("newsSyncErrorCode");
        } else {
            update.set("newsSyncErrorCode", errorCode);
        }
        if (isSuccessful(status)) {
            update.set("newsLastSuccessAt", completedAt);
            if (savedCount > 0) {
                update.set("newsConsecutiveNoChangeCount", 0);
            } else {
                int previousNoChangeCount = stockInfo.getNewsConsecutiveNoChangeCount() == null
                        ? 0 : stockInfo.getNewsConsecutiveNoChangeCount();
                update.set("newsConsecutiveNoChangeCount", previousNoChangeCount + 1);
            }
        }

        try {
            UpdateResult result = mongoTemplate.updateFirst(
                    stockCodeQuery(stockInfo.getCode()), update, StockInfo.class);
            if (result.getMatchedCount() != 1) {
                throw new IllegalStateException("未找到股票新闻同步水位: stockCode=" + stockInfo.getCode());
            }
        } catch (RuntimeException exception) {
            log.error("股票 {} 新闻文档处理完成，但 MongoDB 水位更新失败，batchId={}",
                    stockInfo.getCode(), stockInfo.getNewsSyncBatchId(), exception);
            throw new IllegalStateException("新闻同步水位更新失败", exception);
        }
    }

    private Query stockCodeQuery(String stockCode) {
        return Query.query(Criteria.where("code").is(stockCode));
    }

    private boolean isSuccessful(NewsSyncStatus status) {
        return status == NewsSyncStatus.SUCCESS_WITH_CHANGE || status == NewsSyncStatus.SUCCESS_NO_CHANGE;
    }
}
// AI_GENERATE_END------
