// AI_GENERATE_START-----
package com.stock.agent.service;

import com.stock.agent.dto.TradePlanCreateRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 自动交易计划结构校验器。
 * 只校验计划来源、有效期、证据和内部一致性，实时行情、资金、持仓与风控由交易执行链重新读取。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Component
public class TradePlanValidator {

    /**
     * 校验交易计划创建请求。
     *
     * @param request 创建请求
     */
    public void validate(TradePlanCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("交易计划不能为空");
        }
        requireText(request.getCandidateBatchId(), "候选批次不能为空");
        requireText(request.getStockCode(), "股票代码不能为空");
        if (!"BUY".equalsIgnoreCase(request.getDirection()) && !"SELL".equalsIgnoreCase(request.getDirection())) {
            throw new IllegalArgumentException("交易方向只允许 BUY 或 SELL");
        }
        BigDecimal amount = request.getPlannedAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("计划金额必须大于零");
        }
        if ("SELL".equalsIgnoreCase(request.getDirection())
                && (request.getPlannedQuantity() == null || request.getPlannedQuantity() <= 0
                || request.getPlannedQuantity() % 100 != 0)) {
            throw new IllegalArgumentException("卖出计划数量必须是正整数手数");
        }
        if (request.getEvidenceRefs() == null || request.getEvidenceRefs().isEmpty()) {
            throw new IllegalArgumentException("交易计划必须包含证据引用");
        }
        if (request.getEvidenceRefs().stream().anyMatch(item -> !StringUtils.hasText(item) || item.length() > 256)) {
            throw new IllegalArgumentException("证据引用不能为空且不能超过 256 个字符");
        }
        if (request.getExpiresAt() == null || !request.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("交易计划必须具有未来有效期");
        }
        requireText(request.getIdempotencyKey(), "幂等键不能为空");
        if (request.getIdempotencyKey().length() > 128) {
            throw new IllegalArgumentException("幂等键不能超过 128 个字符");
        }
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
// AI_GENERATE_END-----
