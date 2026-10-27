// AI_GENERATE_START----
package com.stock.data.service;

import com.stock.data.entity.StockInfo;
import com.stock.data.entity.StockNews;
import com.stock.data.enums.NewsSyncStatus;
import com.stock.data.repository.NewsRepository;
import com.stock.data.repository.StockInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

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

    StockInfo markStarted(String stockCode, String batchId) {
        StockInfo stockInfo = stockInfoRepository.findByCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("股票代码不存在: " + stockCode));
        stockInfo.setNewsLastAttemptAt(LocalDateTime.now());
        stockInfo.setNewsSyncBatchId(batchId);
        stockInfo.setNewsSyncStatus(NewsSyncStatus.RUNNING);
        stockInfo.setNewsSyncErrorCode(null);
        return stockInfoRepository.save(stockInfo);
    }

    void complete(StockInfo stockInfo, NewsSyncStatus status, int savedCount, String errorCode) {
        LocalDateTime completedAt = LocalDateTime.now();
        newsRepository.findFirstByStockCodeOrderByPublishTimeDesc(stockInfo.getCode())
                .map(StockNews::getPublishTime)
                .ifPresent(stockInfo::setNewsLastPublishTime);
        stockInfo.setNewsSyncStatus(status);
        stockInfo.setNewsSyncErrorCode(errorCode);

        if (status == NewsSyncStatus.SUCCESS_WITH_CHANGE || status == NewsSyncStatus.SUCCESS_NO_CHANGE) {
            stockInfo.setNewsLastSuccessAt(completedAt);
            int previousNoChangeCount = stockInfo.getNewsConsecutiveNoChangeCount() == null
                    ? 0 : stockInfo.getNewsConsecutiveNoChangeCount();
            stockInfo.setNewsConsecutiveNoChangeCount(savedCount > 0 ? 0 : previousNoChangeCount + 1);
            stockInfo.setNewsNextSyncAt(completedAt.plusDays(1));
        } else {
            stockInfo.setNewsNextSyncAt(completedAt.plusMinutes(15));
        }

        try {
            stockInfoRepository.save(stockInfo);
        } catch (RuntimeException exception) {
            log.error("股票 {} 新闻文档处理完成，但 MongoDB 水位更新失败，batchId={}",
                    stockInfo.getCode(), stockInfo.getNewsSyncBatchId(), exception);
            throw new IllegalStateException("新闻同步水位更新失败", exception);
        }
    }
}
// AI_GENERATE_END----
