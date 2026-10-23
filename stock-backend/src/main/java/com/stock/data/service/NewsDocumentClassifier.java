// AI_GENERATE_START -
package com.stock.data.service;

import com.stock.data.enums.NewsDocumentType;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 新闻真实文档类型分类器。
 * 通过来源和内容语义纠正供应商菜单栏目与实际文档类型的错位。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
public class NewsDocumentClassifier {

    /**
     * 推导新闻文档的真实类型。
     *
     * @param source 来源媒体
     * @param normalizedTitle 规范化标题
     * @param normalizedContent 规范化正文
     * @return 真实文档类型
     */
    public NewsDocumentType classify(String source, String normalizedTitle, String normalizedContent) {
        String sourceText = normalize(source);
        String title = normalize(normalizedTitle);
        String contentPrefix = normalize(prefix(normalizedContent, 800));
        String text = title + " " + contentPrefix;

        if (containsAny(text, "【评级】", "评级调整", "维持买入", "维持增持", "上调评级", "下调评级", "目标价")) {
            return NewsDocumentType.RATING_CHANGE;
        }
        if (containsAny(text, "【行研】", "行业研究", "行业研报", "投资策略报告", "研究报告")) {
            return NewsDocumentType.RESEARCH_REPORT;
        }
        if (containsAny(text, "投资者关系活动", "互动易", "投资者问答", "调研活动记录", "业绩说明会问答")) {
            return NewsDocumentType.INVESTOR_QA;
        }
        if (containsAny(sourceText, "上海证券交易所", "深圳证券交易所", "北京证券交易所", "巨潮资讯", "证监会")
                || containsAny(title, "公告", "年度报告", "半年度报告", "季度报告", "董事会决议", "监事会决议")) {
            return NewsDocumentType.OFFICIAL_DISCLOSURE;
        }
        if (!title.isBlank() || !contentPrefix.isBlank()) {
            return NewsDocumentType.MEDIA_NEWS;
        }
        return NewsDocumentType.UNKNOWN;
    }

    private String prefix(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
// AI_GENERATE_END -
