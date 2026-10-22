// AI_GENERATE_START ---
package com.stock.dataCollector.service;

import org.springframework.stereotype.Component;

/**
 * 新闻事件类型和信息优先级基线分类器。
 * 统一采集和模型评估使用的事件类型规则，避免维护两套不一致的事件分类逻辑。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Component
public class NewsEventClassifier {

    /**
     * 根据新闻标题、正文和分类识别事件类型。
     *
     * @param title 新闻标题
     * @param content 新闻正文
     * @param category 新闻来源分类
     * @return 事件类型
     */
    public String classify(String title, String content, String category) {
        String titleEventType = classifyText(title);
        if (titleEventType != null) {
            return titleEventType;
        }
        String contentEventType = classifyText(content);
        if (contentEventType != null) {
            return contentEventType;
        }
        return "公告".equals(category) ? "GENERAL_ANNOUNCEMENT" : "GENERAL_NEWS";
    }

    private String classifyText(String value) {
        String text = value == null ? "" : value.toLowerCase();
        if (text.matches(".*(解禁|限售|质押|融资|融券|龙虎榜|大宗交易|股东.*冻结|股份.*冻结).*")) {
            return "CAPITAL_RISK";
        }
        if (text.matches(".*(处罚|监管|问询|警示|立案|违规|诉讼|仲裁|被执行).*")) {
            return "REGULATORY_OR_LITIGATION";
        }
        if (text.matches(".*(回购|减持|增持|股东持股|高管持股|董事|监事|董秘|辞职|任命|换届).*")) {
            return "SHAREHOLDER_OR_EXECUTIVE";
        }
        if (text.matches(".*(业绩预告|业绩快报|年度报告|半年度报告|季度报告|净利润|营收).*")) {
            return "EARNINGS";
        }
        if (text.matches(".*(合同|订单|中标|项目|投资|收购|并购|合作|战略协议).*")) {
            return "MATERIAL_BUSINESS";
        }
        return null;
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
// AI_GENERATE_END ---
