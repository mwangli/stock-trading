// AI_GENERATE_START --
package com.stock.agent.service;

import com.stock.agent.entity.AgentTradePlanEntity;
import com.stock.agent.model.BusinessRagSourceDocument;
import com.stock.agent.repository.AgentTradePlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 从交易计划复核记录中提取按用户隔离的复盘结论。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
@RequiredArgsConstructor
public class TradePlanReviewBusinessRagSource implements BusinessRagSource {

    private final AgentTradePlanRepository repository;

    /**
     * 返回交易计划复核来源名称。
     *
     * @return 来源名称
     */
    @Override
    public String sourceName() {
        return "tradePlanReviews";
    }

    /**
     * 查询最近用户复核记录并转换为私有 RAG 文档。
     *
     * @return 按用户范围隔离的复盘文档
     */
    @Override
    public List<BusinessRagSourceDocument> collect() {
        return repository.findTop100ByReviewCommentIsNotNullOrderByUpdatedAtDesc().stream()
                .filter(item -> StringUtils.hasText(item.getPlanId()))
                .filter(item -> StringUtils.hasText(item.getUserScope()))
                .filter(item -> StringUtils.hasText(item.getReviewComment()))
                .map(this::toDocument)
                .toList();
    }

    private BusinessRagSourceDocument toDocument(AgentTradePlanEntity plan) {
        String content = "# 交易计划复盘结论\n\n"
                + "计划标识：" + value(plan.getPlanId()) + "\n"
                + "股票代码：" + value(plan.getStockCode()) + "\n"
                + "目标交易日：" + value(plan.getTargetTradeDate()) + "\n"
                + "策略版本：" + value(plan.getStrategyVersion()) + "\n"
                + "复核结果：" + value(plan.getStatus()) + "\n"
                + "原计划理由：" + value(plan.getReason()) + "\n"
                + "用户复核意见：" + plan.getReviewComment().trim() + "\n"
                + "处理摘要：" + value(plan.getDecisionMessage()) + "\n";
        List<String> stockCodes = StringUtils.hasText(plan.getStockCode())
                ? List.of(plan.getStockCode().trim()) : List.of();
        List<String> tags = new ArrayList<>(List.of("复盘结论", "交易计划复核"));
        if (plan.getStatus() != null) {
            tags.add("复核结果:" + plan.getStatus().name());
        }
        return new BusinessRagSourceDocument(
                "REVIEW_CONCLUSION",
                "trade-plan-review:" + plan.getPlanId(),
                content,
                stockCodes,
                List.copyOf(tags),
                "PRIVATE",
                plan.getUserScope(),
                plan.getUpdatedAt());
    }

    private String value(Object value) {
        return value == null ? "未知" : String.valueOf(value);
    }
}
// AI_GENERATE_END --