// AI_GENERATE_START-------------
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
     * 标题中的当前事实优先于正文历史描述，保证同一事件跨来源角色稳定。
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
        if (isNonActionableNoise(title, content)) {
            return NewsDocumentRole.IGNORE;
        }
        if (isAdministrativeDisclosure(title) || isResolvedRiskTitle(title)) {
            return NewsDocumentRole.CONTEXT_ONLY;
        }
        if (isHardRisk(title, eventType)) {
            return NewsDocumentRole.RISK_ONLY;
        }
        if (isCapitalRiskTitle(title) || isTradeSignalTitle(title)) {
            return NewsDocumentRole.TRADE_SIGNAL;
        }
        if (documentType == NewsDocumentType.INVESTOR_QA) {
            return NewsDocumentRole.CONTEXT_ONLY;
        }
        if ("REGULATORY_OR_LITIGATION".equals(eventType) || "CAPITAL_RISK".equals(eventType)) {
            if (isNegatedRisk(title, content)) {
                return NewsDocumentRole.CONTEXT_ONLY;
            }
            return NewsDocumentRole.TRADE_SIGNAL;
        }
        if (documentType == NewsDocumentType.RATING_CHANGE
                || "EARNINGS".equals(eventType)
                || "MATERIAL_BUSINESS".equals(eventType)
                || "SHAREHOLDER_OR_EXECUTIVE".equals(eventType)) {
            return NewsDocumentRole.TRADE_SIGNAL;
        }
        if (documentType == NewsDocumentType.RESEARCH_REPORT
                || documentType == NewsDocumentType.OFFICIAL_DISCLOSURE) {
            return NewsDocumentRole.CONTEXT_ONLY;
        }
        return containsAny(title + content, "涨停", "跌停", "上调", "下调", "超预期", "不及预期")
                ? NewsDocumentRole.TRADE_SIGNAL : NewsDocumentRole.CONTEXT_ONLY;
    }

    private boolean isNegatedRisk(String title, String content) {
        String evidence = title + " " + prefix(content, 500);
        boolean negated = evidence.matches(".*(不存在|未涉及|未发生|没有|无下列|无以下).{0,160}"
                + "(立案调查|行政处罚|重大诉讼|重大仲裁|股份冻结|股权质押).*");
        boolean reversed = evidence.matches(".*(但是|然而|但近日|现收到|实际发生).{0,80}"
                + "(立案调查|行政处罚|重大诉讼|重大仲裁|股份冻结|股权质押).*");
        boolean resolved = title.matches(".*(解除|解冻|撤销|已结案|已和解|已驳回|终止调查).{0,80}"
                + "(质押|冻结|行政处罚|重大诉讼|重大仲裁|立案调查).*"
                + "|.*(质押|冻结|行政处罚|重大诉讼|重大仲裁|立案调查).{0,80}"
                + "(已解除|已解冻|已撤销|已结案|已和解|已驳回|已终止).*");
        return (negated && !reversed) || resolved;
    }

    private boolean isAdministrativeDisclosure(String title) {
        boolean routine = containsAny(title,
                "董事会决议公告", "监事会决议公告", "独立董事提名人声明", "独立董事候选人声明",
                "股东会决议公告", "股东大会决议公告", "股东会文件", "股东大会文件",
                "独立董事述职报告", "独立性评估", "董事会工作报告", "监事会工作报告",
                "法律意见书", "履职情况评估报告", "管理制度", "公司章程", "议事规则", "授权委托书",
                "股票交易异常波动公告", "投资者关系管理信息", "投资者关系活动记录", "调研活动记录");
        boolean inquiryReply = title.matches(".*问询函.{0,40}(回复|回函).*");
        return (routine || inquiryReply)
                && !containsAny(title, "增持", "减持", "回购", "辞职", "控制权变更", "立案", "处罚",
                "业绩预告", "业绩预盈", "业绩预亏", "重大合同", "中标", "司法冻结", "轮候冻结");
    }

    private boolean isResolvedRiskTitle(String title) {
        boolean resolved = containsAny(title, "解除司法冻结", "解除冻结", "解冻股份", "解除质押",
                "撤销处罚", "诉讼已结案", "仲裁已结案", "终止调查");
        boolean activeRisk = containsAny(title, "新增质押", "补充质押", "新增冻结", "轮候冻结",
                "再次质押", "新增诉讼", "新增仲裁");
        return resolved && !activeRisk;
    }
    private boolean isHardRisk(String title, String eventType) {
        if ("REGULATORY_OR_LITIGATION".equals(eventType)) {
            return containsAny(title, "立案调查", "行政处罚", "纪律处分", "重大诉讼", "重大仲裁",
                    "被执行人", "退市风险警示", "终止上市");
        }
        return containsAny(title, "司法冻结", "轮候冻结", "强制平仓", "平仓风险");
    }

    private boolean isCapitalRiskTitle(String title) {
        return containsAny(title, "股份质押", "股权质押", "补充质押", "质押延期购回", "股票质押式回购", "股份冻结");
    }

    private boolean isTradeSignalTitle(String title) {
        return containsAny(title,
                "业绩预告", "业绩快报", "业绩预盈", "业绩预亏", "业绩预增", "业绩预减",
                "年度报告", "半年度报告", "季度报告", "股份回购", "回购股份", "股东减持", "股东增持",
                "高管减持", "高管增持", "控制权变更", "实际控制人变更", "股权激励", "重大合同",
                "中标通知", "项目中标", "中标候选人", "重大投资", "资产收购", "出售股权", "股权转让",
                "重大资产重组", "并购重组", "涉及诉讼", "诉讼进展", "仲裁进展");
    }

    private boolean isNonActionableNoise(String title, String content) {
        if (title.startsWith("【涨停】") || title.startsWith("【跌停】")) {
            return true;
        }
        if (containsAny(title, "投资日历", "财经日历", "知识星球", "付费目录", "内容目录",
                "每日大宗交易", "每日龙虎榜", "今日涨停一览", "今日跌停一览", "盘前必读",
                "大宗交易(深)", "大宗交易(沪)", "龙虎榜数据", "龙虎榜单", "资金流向排名",
                "主力资金流向")) {
            return true;
        }
        if (title.matches(".*\\d{4}年\\d{1,2}月\\d{1,2}日.*现\\d+笔.*大宗交易.*")) {
            return true;
        }
        return title.length() < 8 && containsAny(content, "点击查看", "扫码关注", "加入社群", "下载app")
                && content.length() < 300;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String prefix(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
// AI_GENERATE_END-------------