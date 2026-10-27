// AI_GENERATE_START-----------
package com.stock.data.service;

import com.mongodb.bulk.BulkWriteResult;
import com.stock.data.dto.NewsEventFactSyncResult;
import com.stock.data.entity.StockNews;
import com.stock.data.entity.StockNewsEventFact;
import com.stock.data.enums.NewsDocumentRole;
import com.stock.data.enums.NewsQualityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 新闻规范事件事实同步服务。
 * 仅聚合交易信号和硬风险事件，避免上下文正文重复占用交易特征存储。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class NewsEventFactService {

    private final MongoTemplate mongoTemplate;

    /**
     * 批量同步交易新闻事件事实，并在成功后将原始文档标记为可用。
     * 同批文档先按股票和规范事件标识聚合，避免唯一键竞争和写入顺序影响角色结果。
     *
     * @param newsItems 已完成采集治理并已持久化的原始文档
     * @return 新增、命中和重复文档统计
     * @throws IllegalStateException 事件事实或质量状态写入失败时抛出
     */
    public NewsEventFactSyncResult synchronizeBatch(List<StockNews> newsItems) {
        if (newsItems == null || newsItems.isEmpty()) {
            return NewsEventFactSyncResult.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        List<StockNews> eligibleItems = newsItems.stream()
                .filter(this::isEligibleForEventFact)
                .toList();
        List<StockNews> invalidItems = newsItems.stream()
                .filter(this::requiresEventFact)
                .filter(news -> !isEligibleForEventFact(news))
                .toList();
        if (!invalidItems.isEmpty()) {
            throw new IllegalStateException("存在无法生成事件事实的交易或风险文档，拒绝推进采集治理游标");
        }
        try {
            NewsEventFactSyncResult syncResult = synchronizeEligibleItems(eligibleItems, now);
            markQualityReady(newsItems);
            return syncResult;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("同步新闻事件事实失败，原始文档保持待同步状态", exception);
        }
    }

    private NewsEventFactSyncResult synchronizeEligibleItems(List<StockNews> eligibleItems, LocalDateTime now) {
        if (eligibleItems.isEmpty()) {
            return NewsEventFactSyncResult.empty();
        }
        Map<String, EventFactAggregate> aggregates = new LinkedHashMap<>();
        for (StockNews news : eligibleItems) {
            String key = news.getStockCode() + "|" + news.getCanonicalEventId();
            aggregates.computeIfAbsent(key, ignored -> new EventFactAggregate()).add(news);
        }

        BulkOperations factOperations = mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED, StockNewsEventFact.class);
        for (EventFactAggregate aggregate : aggregates.values()) {
            factOperations.upsert(eventFactQuery(aggregate.representative()), eventFactUpdate(aggregate, now));
        }
        BulkWriteResult writeResult = factOperations.execute();
        long insertedEvents = writeResult.getUpserts().size();
        long matchedEvents = writeResult.getMatchedCount();
        return new NewsEventFactSyncResult(
                eligibleItems.size(), insertedEvents, matchedEvents,
                Math.max(0, eligibleItems.size() - insertedEvents));
    }

    private boolean requiresEventFact(StockNews news) {
        return news != null && (news.getDocumentRole() == NewsDocumentRole.TRADE_SIGNAL
                || news.getDocumentRole() == NewsDocumentRole.RISK_ONLY);
    }

    private boolean isEligibleForEventFact(StockNews news) {
        return requiresEventFact(news)
                && news.getStockCode() != null
                && !news.getStockCode().isBlank()
                && news.getCanonicalEventId() != null
                && !news.getCanonicalEventId().isBlank();
    }

    private Query eventFactQuery(StockNews news) {
        return Query.query(Criteria.where("stockCode").is(news.getStockCode())
                .and("canonicalEventId").is(news.getCanonicalEventId()));
    }

    private Update eventFactUpdate(EventFactAggregate aggregate, LocalDateTime now) {
        StockNews news = aggregate.representative();
        LocalDateTime observedAt = news.getPublishTime() == null ? now : news.getPublishTime();
        Update update = new Update()
                .setOnInsert("canonicalEventId", news.getCanonicalEventId())
                .setOnInsert("stockCode", news.getStockCode())
                .setOnInsert("createTime", now)
                .min("publishTime", aggregate.firstPublishedAt(observedAt))
                .set("stockName", news.getStockName())
                .set("normalizedTitle", news.getNormalizedTitle())
                .set("normalizedContent", news.getNormalizedContent())
                .set("documentType", news.getDocumentType())
                .set("documentRole", news.getDocumentRole())
                .set("eventType", news.getEventType())
                .set("contentHash", news.getContentHash())
                .set("informationPriority", news.getInformationPriority())
                .set("noveltyScore", news.getNoveltyScore())
                .set("materialityScore", news.getMaterialityScore())
                .set("stockSpecificity", news.getStockSpecificity())
                .set("pipelineVersion", news.getPipelineVersion())
                .max("lastSeenAt", aggregate.lastSeenAt(observedAt))
                .set("updateTime", now);
        if (!aggregate.observedRoles().isEmpty()) {
            update.addToSet("observedDocumentRoles").each(aggregate.observedRoles().toArray());
        }
        if (!aggregate.sourceDocumentIds().isEmpty()) {
            update.addToSet("sourceExternalIds").each(aggregate.sourceDocumentIds().toArray());
        }
        if (!aggregate.sources().isEmpty()) {
            update.addToSet("sources").each(aggregate.sources().toArray());
        }
        return update;
    }

    private void markQualityReady(List<StockNews> newsItems) {
        BulkOperations operations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, StockNews.class);
        int queued = 0;
        for (StockNews news : newsItems) {
            if (!canMarkReady(news)) {
                continue;
            }
            Query query;
            if (news.getId() != null && !news.getId().isBlank()) {
                query = Query.query(Criteria.where("_id").is(news.getId()));
            } else if (news.getStockCode() != null && news.getExternalId() != null) {
                query = Query.query(Criteria.where("stockCode").is(news.getStockCode())
                        .and("externalId").is(news.getExternalId()));
            } else {
                continue;
            }
            Update update = new Update().set("qualityStatus", NewsQualityStatus.READY);
            if (requiresEventFact(news)) {
                update.set("qualityReason", "EVENT_FACT_READY");
            }
            operations.updateOne(query, update);
            queued++;
        }
        if (queued > 0) {
            operations.execute();
        }
    }

    private boolean canMarkReady(StockNews news) {
        return news != null && (news.getDocumentRole() == NewsDocumentRole.IGNORE
                || news.getDocumentRole() == NewsDocumentRole.CONTEXT_ONLY
                || isEligibleForEventFact(news));
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback == null || fallback.isBlank() ? null : fallback;
    }

    private int rolePriority(NewsDocumentRole role) {
        if (role == NewsDocumentRole.RISK_ONLY) {
            return 2;
        }
        if (role == NewsDocumentRole.TRADE_SIGNAL) {
            return 1;
        }
        return 0;
    }

    private final class EventFactAggregate {
        private StockNews representative;
        private LocalDateTime firstPublishedAt;
        private LocalDateTime lastSeenAt;
        private final Set<String> sourceDocumentIds = new LinkedHashSet<>();
        private final Set<String> sources = new LinkedHashSet<>();
        private final Set<NewsDocumentRole> observedRoles = new LinkedHashSet<>();

        private void add(StockNews news) {
            LocalDateTime observedAt = news.getPublishTime();
            if (representative == null
                    || rolePriority(news.getDocumentRole()) > rolePriority(representative.getDocumentRole())
                    || (rolePriority(news.getDocumentRole()) == rolePriority(representative.getDocumentRole())
                    && value(news.getInformationPriority()) > value(representative.getInformationPriority()))) {
                representative = news;
            }
            if (observedAt != null && (firstPublishedAt == null || observedAt.isBefore(firstPublishedAt))) {
                firstPublishedAt = observedAt;
            }
            if (observedAt != null && (lastSeenAt == null || observedAt.isAfter(lastSeenAt))) {
                lastSeenAt = observedAt;
            }
            String sourceDocumentId = firstNonBlank(news.getExternalId(), news.getId());
            if (sourceDocumentId != null) {
                sourceDocumentIds.add(sourceDocumentId);
            }
            if (news.getSource() != null && !news.getSource().isBlank()) {
                sources.add(news.getSource());
            }
            observedRoles.add(news.getDocumentRole());
        }

        private StockNews representative() {
            return representative;
        }

        private LocalDateTime firstPublishedAt(LocalDateTime fallback) {
            return firstPublishedAt == null ? fallback : firstPublishedAt;
        }

        private LocalDateTime lastSeenAt(LocalDateTime fallback) {
            return lastSeenAt == null ? fallback : lastSeenAt;
        }

        private Set<String> sourceDocumentIds() {
            return sourceDocumentIds;
        }

        private Set<String> sources() {
            return sources;
        }

        private Set<NewsDocumentRole> observedRoles() {
            return observedRoles;
        }

        private int value(Integer number) {
            return number == null ? 0 : number;
        }
    }
}
// AI_GENERATE_END-----------