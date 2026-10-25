// AI_GENERATE_START------------
package com.stock.agent.tooling.capability;

import com.stock.agent.interaction.dto.AgentTaskStrategy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Agent 首期能力路由器。
 * 根据问题语义选择任务策略和最小只读工具集合，权限校验仍由 Java 工具执行边界负责。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Component
public class AgentCapabilityRouter {

    /**
     * 为当前问题选择任务策略和首期只读工具。
     *
     * @param question 用户问题
     * @return 能力路由结果
     */
    public AgentCapabilitySelection route(String question) {
        if (!StringUtils.hasText(question)) {
            return new AgentCapabilitySelection(AgentTaskStrategy.CONVERSATION, Set.of());
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        Set<AgentToolName> tools = new LinkedHashSet<>();
        AgentTaskStrategy strategy = AgentTaskStrategy.CONVERSATION;

        if (containsAny(normalized, "你好", "您好", "hello", "hi", "你是谁", "能做什么")) {
            return new AgentCapabilitySelection(AgentTaskStrategy.CONVERSATION, Set.of());
        }
        if (containsAny(normalized, "天气", "气温", "下雨", "降雨", "空气质量", "新闻", "最新消息",
                "实时信息", "联网", "搜索网页")) {
            tools.add(AgentToolName.WEB_SEARCH);
            strategy = AgentTaskStrategy.CONVERSATION;
        }
        if (containsAny(normalized, "模块", "架构", "设计", "项目文档", "怎么实现", "如何实现",
                "model", "backend", "frontend", "agent")) {
            tools.add(AgentToolName.PROJECT_DOCUMENT_SEARCH);
            strategy = AgentTaskStrategy.CONVERSATION;
        }
        if (containsAny(normalized, "上涨股票", "哪些股票上涨", "今天上涨", "今日上涨", "涨幅榜",
                "市场涨跌", "下跌股票")) {
            tools.add(AgentToolName.MARKET_ADVANCERS);
            strategy = AgentTaskStrategy.FACT_QUERY;
        }
        if (containsAny(normalized, "上个月收益", "上月收益", "月度收益", "收益为负", "亏损原因",
                "为什么亏损", "亏损最多", "主要亏损", "主要盈利")) {
            return new AgentCapabilitySelection(
                    AgentTaskStrategy.ANALYSIS, Set.of(AgentToolName.PERFORMANCE_ANALYSIS));
        }
        if (containsAny(normalized, "累计收益", "当日收益", "今日收益", "本月收益", "胜率", "收益率", "收益情况", "账户收益")) {
            return new AgentCapabilitySelection(
                    AgentTaskStrategy.FACT_QUERY, Set.of(AgentToolName.PERFORMANCE_OVERVIEW));
        }
        if (containsAny(normalized, "模型连接", "模型状态", "provider", "ollama", "deepseek", "响应超时", "对话超时")) {
            return new AgentCapabilitySelection(AgentTaskStrategy.DIAGNOSTIC, Set.of());
        }
        if (containsAny(normalized, "没有买入", "执行门禁", "下单", "交易执行", "资金", "持仓", "账户")) {
            return new AgentCapabilitySelection(AgentTaskStrategy.DIAGNOSTIC,
                    Set.of(AgentToolName.TRADING_CALENDAR, AgentToolName.PREDICTION_CANDIDATES,
                            AgentToolName.BROKER_STATUS, AgentToolName.JOB_DIAGNOSTIC));
        }
        if (containsAny(normalized, "任务失败", "任务状态", "调度", "job")) {
            return new AgentCapabilitySelection(
                    AgentTaskStrategy.DIAGNOSTIC, Set.of(AgentToolName.JOB_DIAGNOSTIC));
        }
        if (containsAny(normalized, "最新价格", "当前价格", "收盘价", "行情")) {
            tools.add(AgentToolName.LATEST_PRICE);
            strategy = AgentTaskStrategy.FACT_QUERY;
        }
        if (containsAny(normalized, "模型表现", "模型概览", "激活模型", "训练状态", "模型版本")) {
            tools.add(AgentToolName.MODEL_OVERVIEW);
            strategy = AgentTaskStrategy.ANALYSIS;
        }
        if (containsAny(normalized, "策略", "候选", "排名", "选股")) {
            tools.add(AgentToolName.STRATEGY_STATE);
            tools.add(AgentToolName.PREDICTION_CANDIDATES);
            strategy = AgentTaskStrategy.ANALYSIS;
        }
        if (containsAny(normalized, "交易日", "开市", "休市")) {
            tools.add(AgentToolName.TRADING_CALENDAR);
        }
        return new AgentCapabilitySelection(strategy, Set.copyOf(tools));
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
// AI_GENERATE_END-----------
