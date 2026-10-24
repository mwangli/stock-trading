// AI_GENERATE_START------
package com.stock.market.service;

import com.stock.market.dto.NewsNormalizedDocument;
import com.stock.market.entity.StockNews;
import com.stock.market.enums.NewsDocumentRole;
import com.stock.market.enums.NewsDocumentType;
import com.stock.market.enums.NewsQualityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 新闻采集治理管线。
 * 在原始文档入库前统一完成规范化、分类、角色识别和事件标识生成。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class NewsCollectionPipelineService {

    /** 当前新闻采集治理规则版本。 */
    public static final String PIPELINE_VERSION = "NEWS_COLLECTION_V5";

    private final NewsDocumentNormalizer newsDocumentNormalizer;
    private final NewsDocumentClassifier newsDocumentClassifier;
    private final NewsEventClassifier newsEventClassifier;
    private final NewsDocumentRoleResolver newsDocumentRoleResolver;
    private final NewsCanonicalEventResolver newsCanonicalEventResolver;

    /**
     * 对原始新闻文档执行完整采集治理。
     *
     * @param news 原始新闻文档
     * @return 已补齐治理字段的同一文档实例
     * @throws IllegalArgumentException 文档为空时抛出
     */
    public StockNews process(StockNews news) {
        if (news == null) {
            throw new IllegalArgumentException("新闻文档不能为空");
        }
        String rawTitle = firstNonBlank(news.getRawTitle(), news.getTitle());
        String rawContent = firstNonBlank(news.getRawContent(), news.getContent());
        news.setRawTitle(rawTitle);
        news.setRawContent(rawContent);
        news.setTitle(firstNonBlank(news.getTitle(), rawTitle));
        news.setContent(firstNonBlank(news.getContent(), rawContent));
        news.setSourceChannel(firstNonBlank(news.getSourceChannel(), news.getCategory()));

        NewsNormalizedDocument normalized = newsDocumentNormalizer.normalize(rawTitle, rawContent);
        news.setNormalizedTitle(normalized.title());
        news.setNormalizedContent(normalized.content());

        NewsDocumentType documentType = newsDocumentClassifier.classify(
                news.getSource(), normalized.title(), normalized.content());
        String eventType = newsEventClassifier.classify(
                normalized.title(), normalized.content(), documentType.name());
        NewsDocumentRole documentRole = newsDocumentRoleResolver.resolve(
                documentType, eventType, normalized.title(), normalized.content());
        int priority = newsEventClassifier.priority(eventType);

        news.setDocumentType(documentType);
        news.setDocumentRole(documentRole);
        news.setEventType(eventType);
        news.setInformationPriority(priority);
        news.setContentHash(newsCanonicalEventResolver.contentHash(normalized.title(), normalized.content()));
        news.setCanonicalEventId(newsCanonicalEventResolver.canonicalEventId(
                eventType, normalized.title(), normalized.content(), news.getPublishTime()));
        news.setNoveltyScore(documentRole == NewsDocumentRole.IGNORE ? 0D : 1D);
        news.setMaterialityScore(resolveMateriality(documentRole, priority));
        news.setStockSpecificity(resolveStockSpecificity(news, normalized));
        news.setPipelineVersion(PIPELINE_VERSION);
        news.setQualityStatus(documentRole == NewsDocumentRole.IGNORE
                ? NewsQualityStatus.READY : NewsQualityStatus.FACT_PENDING);
        news.setQualityReason(resolveQualityReason(documentRole, normalized));
        return news;
    }

    private double resolveMateriality(NewsDocumentRole role, int priority) {
        if (role == NewsDocumentRole.IGNORE) {
            return 0D;
        }
        if (role == NewsDocumentRole.CONTEXT_ONLY) {
            return Math.min(0.3D, priority / 10D);
        }
        return Math.min(1D, priority / 5D);
    }

    private double resolveStockSpecificity(StockNews news, NewsNormalizedDocument normalized) {
        String code = firstNonBlank(news.getStockCode(), "");
        String name = firstNonBlank(news.getStockName(), "");
        if (containsStock(normalized.title(), code, name)) {
            return 1D;
        }
        if (containsStock(normalized.content(), code, name)) {
            return 0.75D;
        }
        return 0.4D;
    }

    private boolean containsStock(String value, String code, String name) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return (!code.isBlank() && value.contains(code)) || (!name.isBlank() && value.contains(name));
    }

    private String resolveQualityReason(NewsDocumentRole role, NewsNormalizedDocument normalized) {
        if (role == NewsDocumentRole.IGNORE) {
            return normalized.title().isBlank() && normalized.content().isBlank()
                    ? "EMPTY_NORMALIZED_DOCUMENT" : "NON_ACTIONABLE_NOISE";
        }
        if (role == NewsDocumentRole.CONTEXT_ONLY) {
            return "CONTEXT_NOT_FOR_RANKING";
        }
        return "EVENT_FACT_PENDING";
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
// AI_GENERATE_END------
