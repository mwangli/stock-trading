// AI_GENERATE_START ------
package com.stock.tradingExecutor.service;

import com.stock.modelService.service.PredictionCandidateQueryService;
import com.stock.strategyAgent.config.AgentProperties;
import com.stock.strategyAgent.domain.AgentEnums;
import com.stock.strategyAgent.domain.entity.AgentTradePlanEntity;
import com.stock.strategyAgent.persistence.AgentTradePlanRepository;
import com.stock.dataCollector.service.TradingCalendarService;
import com.stock.tradingExecutor.domain.vo.OrderResult;
import com.stock.tradingExecutor.execution.TradeExecutor;
import com.stock.tradingExecutor.execution.TradingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TradePlan 自动执行服务。
 * 重新读取交易日和券商当前事实后，委托既有 TradeExecutor 完成风险检查与券商适配器调用。
 * 交易计划由 strategyAgent 生成，本服务属于 tradingExecutor，负责最终复核和执行。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradePlanExecutionService {

    private final AgentTradePlanRepository repository;
    private final AgentProperties agentProperties;
    private final TradingProperties tradingProperties;
    private final TradingCalendarService tradingCalendarService;
    private final TradeExecutor tradeExecutor;
    private final PredictionCandidateQueryService predictionCandidateQueryService;
    private final com.stock.strategyAgent.service.TradePlanStateMachine stateMachine;

    /**
     * 执行当前所有待复核交易计划。
     *
     * @return 实际处理的计划数量
     */
    public int executePendingPlans() {
        if (!agentProperties.isTradePlanEnabled()) {
            return 0;
        }
        int processed = 0;
        for (AgentTradePlanEntity plan : repository.findByStatusOrderByCreatedAtAsc(
                AgentEnums.TradePlanStatus.PENDING_REVALIDATION)) {
            if (executeOne(plan.getPlanId())) {
                processed++;
            }
        }
        return processed;
    }

    /**
     * 重新取当前事实并执行单个交易计划。
     *
     * @param planId 计划标识
     * @return 是否完成一次终态处理
     */
    @Transactional
    public boolean executeOne(String planId) {
        AgentTradePlanEntity plan = repository.findById(planId).orElse(null);
        if (plan == null || plan.getStatus() != AgentEnums.TradePlanStatus.PENDING_REVALIDATION) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (plan.getExpiresAt() == null || !plan.getExpiresAt().isAfter(now)) {
            return saveDecision(plan, AgentEnums.TradePlanStatus.EXPIRED, "PLAN_EXPIRED", "交易计划已过期", null);
        }
        String traceId = UUID.randomUUID().toString();
        if (repository.claimForExecution(planId, AgentEnums.TradePlanStatus.PENDING_REVALIDATION,
                AgentEnums.TradePlanStatus.EXECUTING, now, traceId) != 1) {
            log.info("[交易执行] 计划未抢占成功，跳过重复执行: planId={}", planId);
            return false;
        }
        plan = repository.findById(planId).orElse(null);
        if (plan == null) {
            return false;
        }
        if (!tradingProperties.isLiveWriteAllowed()) {
            return saveDecision(plan, AgentEnums.TradePlanStatus.RISK_REJECTED, "REAL_WRITE_DISABLED",
                    "TRADING_REAL_WRITE_ENABLED 未开启，禁止调用券商写接口", null);
        }
        try {
            tradingCalendarService.requireTradingDay(LocalDate.now());
            if (plan.getTargetTradeDate() != null && !LocalDate.now().equals(plan.getTargetTradeDate())) {
                return saveDecision(plan, AgentEnums.TradePlanStatus.RISK_REJECTED,
                        "TARGET_TRADE_DATE_MISMATCH", "目标交易日与当前执行日期不一致", null);
            }
            validatePlanFacts(plan);
            if ("BUY".equalsIgnoreCase(plan.getDirection())) {
                predictionCandidateQueryService.requireTradingEligible(plan.getPredictionBatchId());
            }

            OrderResult result;
            if ("BUY".equalsIgnoreCase(plan.getDirection())) {
                result = tradeExecutor.executeBuy(plan.getStockCode(), plan.getPlannedAmount());
            } else {
                Integer quantity = plan.getPlannedQuantity();
                if (quantity == null || quantity <= 0) {
                    throw new IllegalStateException("卖出计划缺少有效数量");
                }
                result = tradeExecutor.executeSell(plan.getStockCode(), BigDecimal.valueOf(quantity));
            }
            AgentEnums.TradePlanStatus status = result != null && result.isSuccess()
                    ? AgentEnums.TradePlanStatus.EXECUTED : AgentEnums.TradePlanStatus.FAILED;
            String message = result == null ? "交易执行未返回结果" : result.getMessage();
            String code = status == AgentEnums.TradePlanStatus.EXECUTED ? null : "ORDER_EXECUTION_FAILED";
            return saveDecision(plan, status, code, message == null ? "交易执行完成" : message, result);
        } catch (RuntimeException exception) {
            log.warn("[交易执行] TradePlan 执行失败: planId={}", planId, exception);
            String code = exception.getMessage() == null ? "EXECUTION_EXCEPTION" : exception.getMessage();
            AgentEnums.TradePlanStatus status = isRiskCode(code)
                    ? AgentEnums.TradePlanStatus.RISK_REJECTED : AgentEnums.TradePlanStatus.FAILED;
            return saveDecision(plan, status, code, code, null);
        }
    }

    private void validatePlanFacts(AgentTradePlanEntity plan) {
        if ("BUY".equalsIgnoreCase(plan.getDirection())) {
            if (plan.getPlannedAmount() == null || plan.getPlannedAmount().signum() <= 0) {
                throw new IllegalStateException("INVALID_BUY_AMOUNT");
            }
            var account = tradeExecutor.getAccountStatus();
            if (account == null) {
                throw new IllegalStateException("ACCOUNT_FACT_UNAVAILABLE");
            }
            if (account.getAvailableCash() != null
                    && account.getAvailableCash().compareTo(plan.getPlannedAmount()) < 0) {
                throw new IllegalStateException("INSUFFICIENT_FUNDS");
            }
        } else if (plan.getPlannedQuantity() == null || plan.getPlannedQuantity() <= 0
                || plan.getPlannedQuantity() % 100 != 0) {
            throw new IllegalStateException("INVALID_SELL_QUANTITY");
        }
    }

    private boolean isRiskCode(String code) {
        return code.startsWith("INVALID_") || code.endsWith("_UNAVAILABLE")
                || "INSUFFICIENT_FUNDS".equals(code) || "ACCOUNT_FACT_UNAVAILABLE".equals(code);
    }

    private boolean saveDecision(AgentTradePlanEntity plan, AgentEnums.TradePlanStatus status,
                                 String riskCode, String message, OrderResult result) {
        stateMachine.requireTransition(plan.getStatus(), status);
        plan.setStatus(status)
                .setRiskCode(riskCode)
                .setOrderId(result == null ? null : result.getOrderId())
                .setExecutedAt(status == AgentEnums.TradePlanStatus.EXECUTED ? LocalDateTime.now() : null)
                .setDecisionMessage(message)
                .setUpdatedAt(LocalDateTime.now());
        repository.save(plan);
        return true;
    }
}
// AI_GENERATE_END ------
