// AI_GENERATE_START --
package com.stock.data.service;

import com.stock.data.dto.StockNewsLatestResult;
import com.stock.data.dto.StockNewsSummaryResponse;
import com.stock.data.entity.StockNews;
import com.stock.data.enums.NewsQualityStatus;
import com.stock.data.repository.NewsRepository;
import com.stock.data.validator.NewsContentQualityValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 股票新闻只读查询服务，为行情等展示场景提供已治理的新闻摘要。
 *
 * @author mwangli
 * @since 2026-10-27
 */
@Service
@RequiredArgsConstructor
public class StockNewsQueryService {

    /** 行情详情允许返回的摘要最大长度。 */
    private static final int SUMMARY_MAX_LENGTH = 500;

    private final NewsRepository newsRepository;
    private final NewsContentQualityValidator contentQualityValidator;

    /**
     * 查询指定股票最近三条可消费新闻。
     *
     * @param stockCode 股票代码，如 600519
     * @return 按发布时间倒序排列的有效新闻摘要
     */
    public StockNewsLatestResult findLatestValidNews(String stockCode) {
        String normalizedStockCode = stockCode == null ? "" : stockCode.trim();
        if (normalizedStockCode.isEmpty()) {
            throw new IllegalArgumentException("股票代码不能为空");
        }

        List<StockNewsSummaryResponse> items = newsRepository
                .findTop20ByStockCodeAndQualityStatusOrderByPublishTimeDesc(
                        normalizedStockCode, NewsQualityStatus.READY)
                .stream()
                .filter(this::hasSubstantiveContent)
                .limit(3)
                .map(this::toSummaryResponse)
                .toList();
        return StockNewsLatestResult.builder().items(items).build();
    }

    private StockNewsSummaryResponse toSummaryResponse(StockNews news) {
        String title = firstNonBlank(news.getNormalizedTitle(), news.getTitle(), news.getRawTitle(), "无标题");
        String content = firstNonBlank(news.getNormalizedContent(), news.getContent(), news.getRawContent(), title);
        return StockNewsSummaryResponse.builder()
                .id(news.getId())
                .title(title)
                .summary(toSummary(content))
                .source(news.getSource())
                .category(news.getCategory())
                .publishTime(news.getPublishTime())
                .build();
    }

    private boolean hasSubstantiveContent(StockNews news) {
        String title = firstNonBlank(news.getNormalizedTitle(), news.getTitle(), news.getRawTitle());
        String content = firstNonBlank(news.getNormalizedContent(), news.getContent(), news.getRawContent());
        return contentQualityValidator.hasSubstantiveContent(title, content);
    }

    private String toSummary(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= SUMMARY_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, SUMMARY_MAX_LENGTH) + "...";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
// AI_GENERATE_END --
