// AI_GENERATE_START----------
package com.stock.market.service;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 新闻事件类型和信息优先级基线分类器。
 * 优先使用标题中的明确事件事实，避免长篇模板正文和普通治理词汇制造高优先级误判。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Component
public class NewsEventClassifier {

    /**
     * 根据新闻标题、正文和真实文档类型识别事件类型。
     *
     * @param title 规范化标题
     * @param content 规范化正文
     * @param category 真实文档类型或兼容分类
     * @return 事件类型
     */
    public String classify(String title, String content, String category) {
        String titleEventType = classifyTitle(title);
        if (titleEventType != null) {
            return titleEventType;
        }
        if (isRoutineDisclosureTitle(title)) {
            return "GENERAL_ANNOUNCEMENT";
        }
        String contentPrefix = content == null || content.length() <= 900
                ? content : content.substring(0, 900);
        String contentEventType = classifyStrongContentEvidence(contentPrefix);
        if (contentEventType != null) {
            return contentEventType;
        }
        return "公告".equals(category) || "OFFICIAL_DISCLOSURE".equals(category)
                ? "GENERAL_ANNOUNCEMENT" : "GENERAL_NEWS";
    }

    private String classifyTitle(String value) {
        String text = normalize(value);
        if (text.matches(".*(解除质押|解除冻结|解除司法冻结|解冻股份).*")) {
            return "SHAREHOLDER_OR_EXECUTIVE";
        }
        if (text.matches(".*(股权质押|股份质押|补充质押|司法冻结|轮候冻结|强制平仓).*")) {
            return "CAPITAL_RISK";
        }
        if (text.matches(".*(行政处罚|监管措施|纪律处分|问询函|警示函|立案调查|重大诉讼|重大仲裁|被执行人).*")) {
            return "REGULATORY_OR_LITIGATION";
        }
        if (text.matches(".*(股份回购|回购股份|股东减持|股东增持|高管减持|高管增持|控制权变更|实际控制人变更|股权激励|(?:董事长|总经理|高管).*辞职).*")) {
            return "SHAREHOLDER_OR_EXECUTIVE";
        }
        if (text.matches(".*(业绩预告|业绩快报|业绩预盈|业绩预亏|业绩预增|业绩预减|年度报告|半年度报告|季度报告|净利润.*(?:增长|下降|亏损|扭亏)|营收.*(?:增长|下降)).*")) {
            return "EARNINGS";
        }
        if (text.matches(".*(重大合同|签订.*合同|签署.*协议|中标通知|项目中标|重大投资|对外投资|资产收购|出售股权|股权转让|重大资产重组|并购重组|重大借款).*")) {
            return "MATERIAL_BUSINESS";
        }
        return null;
    }

    private String classifyStrongContentEvidence(String value) {
        String text = normalize(value);
        if (text.matches(".*(?:收到|被采取|因涉嫌).{0,80}(?:立案调查通知书|行政处罚决定书|监管措施决定书|纪律处分决定书|警示函).*"
                + "|.*(?:新增|发生|涉及).{0,40}(?:重大诉讼|重大仲裁).*")) {
            return "REGULATORY_OR_LITIGATION";
        }
        if (text.matches(".*(?:股东|实际控制人).{0,100}(?:办理|新增|被司法|被轮候).{0,30}(?:股份质押|股权质押|股份冻结|司法冻结|轮候冻结).*")) {
            return "CAPITAL_RISK";
        }
        if (text.matches(".*(?:预计|实现).{0,80}(?:净利润|营业收入).{0,60}(?:增长|下降|亏损|扭亏).*")) {
            return "EARNINGS";
        }
        if (text.matches(".*(?:公司|子公司).{0,80}(?:中标|签订重大合同|完成重大资产收购|实施重大投资).*")) {
            return "MATERIAL_BUSINESS";
        }
        return null;
    }

    private boolean isRoutineDisclosureTitle(String value) {
        String title = normalize(value);
        return title.matches(".*(董事会.*决议|监事会.*决议|股东(?:大)?会.*(?:决议|通知|文件)|工作报告|履职情况"
                + "|独立性评估|法律意见书|管理制度|公司章程|议事规则|授权委托书).*");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /**
     * 返回事件类型的信息优先级。
     *
     * @param eventType 事件类型
     * @return 优先级，范围为 1 到 5
     */
    public int priority(String eventType) {
        return switch (eventType) {
            case "REGULATORY_OR_LITIGATION", "EARNINGS" -> 5;
            case "SHAREHOLDER_OR_EXECUTIVE", "MATERIAL_BUSINESS" -> 4;
            case "CAPITAL_RISK" -> 3;
            default -> 1;
        };
    }
}
// AI_GENERATE_END----------
