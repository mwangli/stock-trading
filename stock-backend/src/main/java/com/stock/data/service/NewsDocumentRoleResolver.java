// AI_GENERATE_START -
package com.stock.data.service;

import com.stock.data.enums.NewsDocumentRole;
import com.stock.data.enums.NewsDocumentType;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 新闻文档业务角色识别器。
 * 将风险门禁、收益信号、上下文和无效文档分离，避免中性公告稀释选股特征。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
public class NewsDocumentRoleResolver {

    /**
     * 根据文档类型和事件语义解析下游业务角色。
     *
     * @param documentType 真实文档类型
     * @param eventType 结构化事件类型
     * @param normalizedTitle 规范化标题
     * @param normalizedContent 规范化正文
     * @return 下游业务角色
     */
    public NewsDocumentRole resolve(NewsDocumentType documentType, String eventType,
                                    String normalizedTitle, String normalizedContent) {
        String title = normalize(normalizedTitle);
        String content = normalize(normalizedContent);
        if (title.isBlank() && content.length() < 20) {
            return NewsDocumentRole.IGNORE;
        }
        if (isAdministrativeDisclosure(title)) {
            return NewsDocumentRole.CONTEXT_ONLY;
        }
        if ("REGULATORY_OR_LITIGATION".equals(eventType) || "CAPITAL_RISK".equals(eventType)) {
            return NewsDocumentRole.RISK_ONLY;
        }
        if (documentType == NewsDocumentType.RATING_CHANGE
                || "EARNINGS".equals(eventType)
                || "MATERIAL_BUSINESS".equals(eventType)
                || "SHAREHOLDER_OR_EXECUTIVE".equals(eventType)) {
            return NewsDocumentRole.TRADE_SIGNAL;
        }
        if (documentType == NewsDocumentType.RESEARCH_REPORT
                || documentType == NewsDocumentType.INVESTOR_QA
                || documentType == NewsDocumentType.OFFICIAL_DISCLOSURE) {
            return NewsDocumentRole.CONTEXT_ONLY;
        }
        return containsAny(title + content, "涨停", "跌停", "上调", "下调", "超预期", "不及预期")
                ? NewsDocumentRole.TRADE_SIGNAL : NewsDocumentRole.CONTEXT_ONLY;
    }

    private boolean isAdministrativeDisclosure(String title) {
        return containsAny(title,
                "董事会决议公告", "监事会决议公告", "独立董事提名人声明", "独立董事候选人声明",
                "独立董事述职报告", "管理制度", "公司章程", "股东大会议事规则", "授权委托书")
                && !containsAny(title, "增持", "减持", "回购", "辞职", "控制权变更", "立案", "处罚");
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
