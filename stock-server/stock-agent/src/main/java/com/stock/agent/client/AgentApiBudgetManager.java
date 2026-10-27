// AI_GENERATE_START----------
package com.stock.agent.client;

import com.stock.agent.config.AgentProperties;
import com.stock.agent.entity.AgentApiDailyBudgetDocument;
import com.mongodb.client.result.UpdateResult;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Agent API 每日费用预算服务。
 *
 * <p>调用前按提示词UTF-8字节数和最大输出Token保守预留预算，调用后按Provider返回的
 * TokenUsage结算并释放差额。Token统计缺失或调用失败时保留全部预留，确保预算控制失败关闭。</p>
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Slf4j
@Component
public class AgentApiBudgetManager {

    private static final String DEEPSEEK_PROVIDER = "deepseek";
    private static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal MICROS_PER_CNY = BigDecimal.valueOf(1_000_000L);

    private final AgentProperties properties;
    private final MongoTemplate mongoTemplate;

    /**
     * 创建Agent API费用预算服务。
     *
     * @param properties Agent运行配置
     * @param mongoTemplate MongoDB访问入口
     */
    public AgentApiBudgetManager(AgentProperties properties, MongoTemplate mongoTemplate) {
        this.properties = properties;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * 根据请求和会话历史估算输入Token上界。
     *
     * <p>UTF-8字节数作为保守Token上界，并额外加入协议开销，避免实际账单高于预留费用。</p>
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param memoryMessages 会话历史消息
     * @return 输入Token保守上界
     */
    public long estimateInputTokens(String systemPrompt, String userPrompt, Iterable<?> memoryMessages) {
        long estimated = utf8Length(systemPrompt);
        estimated = saturatedAdd(estimated, utf8Length(userPrompt));
        if (memoryMessages != null) {
            for (Object message : memoryMessages) {
                estimated = saturatedAdd(estimated, utf8Length(String.valueOf(message)));
            }
        }
        int overhead = Math.max(dailyBudget().getReservationOverheadTokens(), 0);
        return saturatedAdd(estimated, overhead);
    }

    /**
     * 在DeepSeek调用前原子预留当日费用预算。
     *
     * @param estimatedInputTokens 输入Token保守上界
     * @param maxOutputTokens 单次最大输出Token数
     * @return 本次调用预算预留
     * @throws BudgetControlException 当预算耗尽、配置非法或预算存储不可用时抛出
     */
    public BudgetReservation reserveDeepSeek(long estimatedInputTokens, int maxOutputTokens) {
        AgentProperties.DailyBudgetProperties config = dailyBudget();
        if (!config.isEnabled()) {
            return BudgetReservation.unlimited();
        }
        validateConfig(config);
        LocalDate usageDate = LocalDate.now(BILLING_ZONE);
        String usageId = usageId(DEEPSEEK_PROVIDER, usageDate);
        long dailyLimitMicros = toMicros(config.getDailyLimitCny());
        long reservedCostMicros = calculateCostMicros(
                Math.max(estimatedInputTokens, 0L), Math.max(maxOutputTokens, 0), config);
        if (reservedCostMicros > dailyLimitMicros) {
            throw budgetExceeded(0L, reservedCostMicros);
        }
        try {
            ensureDailyDocument(usageId, usageDate);
            long maximumAllocatedBeforeReserve = dailyLimitMicros - reservedCostMicros;
            Query query = Query.query(Criteria.where("id").is(usageId)
                    .and("allocatedCostMicros").lte(maximumAllocatedBeforeReserve));
            Update update = new Update()
                    .inc("allocatedCostMicros", reservedCostMicros)
                    .set("updatedAt", LocalDateTime.now(BILLING_ZONE));
            AgentApiDailyBudgetDocument reserved = mongoTemplate.findAndModify(query, update,
                    FindAndModifyOptions.options().returnNew(true), AgentApiDailyBudgetDocument.class);
            if (reserved == null) {
                BudgetSnapshot snapshot = currentSnapshot();
                throw budgetExceeded(snapshot.remainingCostMicros(), reservedCostMicros);
            }
            log.info("[Agent][Budget] provider={} usageDate={} status=RESERVED reservedCny={} allocatedCny={} dailyLimitCny={}",
                    DEEPSEEK_PROVIDER, usageDate, toCny(reservedCostMicros),
                    toCny(reserved.getAllocatedCostMicros()), config.getDailyLimitCny());
            return new BudgetReservation(true, usageId, usageDate, reservedCostMicros, dailyLimitMicros);
        } catch (BudgetControlException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BudgetControlException("AGENT_DAILY_BUDGET_UNAVAILABLE",
                    "每日API费用预算状态不可用，已阻止本次模型调用", exception);
        }
    }

    /**
     * 使用Provider返回的Token统计结算成功调用。
     *
     * @param reservation 调用前预算预留
     * @param tokenUsage Provider返回的Token统计
     */
    public void settleSuccess(BudgetReservation reservation, TokenUsage tokenUsage) {
        if (reservation == null || !reservation.limited()) {
            return;
        }
        if (tokenUsage == null) {
            settleConservatively(reservation, "TOKEN_USAGE_UNAVAILABLE");
            return;
        }
        long inputTokens = safeTokenCount(tokenUsage.inputTokenCount());
        long outputTokens = safeTokenCount(tokenUsage.outputTokenCount());
        if (inputTokens == 0L && outputTokens == 0L) {
            settleConservatively(reservation, "TOKEN_USAGE_EMPTY");
            return;
        }
        long actualCostMicros = calculateCostMicros(inputTokens, outputTokens, dailyBudget());
        settle(reservation, actualCostMicros, inputTokens, outputTokens, false);
    }

    /**
     * 对已发起但失败或超时的调用执行保守结算。
     *
     * @param reservation 调用前预算预留
     * @param reason 保守结算原因
     */
    public void settleConservatively(BudgetReservation reservation, String reason) {
        if (reservation == null || !reservation.limited()) {
            return;
        }
        settle(reservation, reservation.reservedCostMicros(), 0L, 0L, true);
        log.warn("[Agent][Budget] provider={} usageDate={} status=CONSERVATIVE_SETTLEMENT reason={} reservedCny={}",
                DEEPSEEK_PROVIDER, reservation.usageDate(), reason, toCny(reservation.reservedCostMicros()));
    }

    /**
     * 查询北京时间当天的DeepSeek费用预算快照。
     *
     * @return 当日预算快照
     */
    public BudgetSnapshot currentSnapshot() {
        AgentProperties.DailyBudgetProperties config = dailyBudget();
        LocalDate usageDate = LocalDate.now(BILLING_ZONE);
        long limitMicros = config.isEnabled() && config.getDailyLimitCny() != null
                ? toMicros(config.getDailyLimitCny()) : 0L;
        AgentApiDailyBudgetDocument document = mongoTemplate.findById(
                usageId(DEEPSEEK_PROVIDER, usageDate), AgentApiDailyBudgetDocument.class);
        long allocated = document == null ? 0L : document.getAllocatedCostMicros();
        return new BudgetSnapshot(config.isEnabled(), usageDate, limitMicros, allocated,
                document == null ? 0L : document.getSettledCostMicros(),
                Math.max(limitMicros - allocated, 0L),
                document == null ? 0L : document.getInputTokens(),
                document == null ? 0L : document.getOutputTokens(),
                document == null ? 0L : document.getRequestCount(),
                document == null ? 0L : document.getConservativeSettlementCount());
    }

    private void settle(BudgetReservation reservation, long actualCostMicros, long inputTokens,
                        long outputTokens, boolean conservative) {
        long normalizedActualCost = Math.max(actualCostMicros, 0L);
        long allocatedDelta = normalizedActualCost - reservation.reservedCostMicros();
        Update update = new Update()
                .inc("allocatedCostMicros", allocatedDelta)
                .inc("settledCostMicros", normalizedActualCost)
                .inc("inputTokens", Math.max(inputTokens, 0L))
                .inc("outputTokens", Math.max(outputTokens, 0L))
                .inc("requestCount", 1L)
                .set("updatedAt", LocalDateTime.now(BILLING_ZONE));
        if (conservative) {
            update.inc("conservativeSettlementCount", 1L);
        }
        try {
            UpdateResult result = mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(reservation.usageId())),
                    update, AgentApiDailyBudgetDocument.class);
            if (result.getMatchedCount() == 0L) {
                log.error("[Agent][Budget] provider={} usageDate={} status=SETTLEMENT_RECORD_MISSING",
                        DEEPSEEK_PROVIDER, reservation.usageDate());
                return;
            }
            if (normalizedActualCost > reservation.reservedCostMicros()) {
                log.error("[Agent][Budget] provider={} usageDate={} status=RESERVATION_UNDERESTIMATED reservedCny={} actualCny={}",
                        DEEPSEEK_PROVIDER, reservation.usageDate(), toCny(reservation.reservedCostMicros()),
                        toCny(normalizedActualCost));
            }
            log.info("[Agent][Budget] provider={} usageDate={} status=SETTLED costCny={} inputTokens={} outputTokens={} conservative={}",
                    DEEPSEEK_PROVIDER, reservation.usageDate(), toCny(normalizedActualCost),
                    inputTokens, outputTokens, conservative);
        } catch (RuntimeException exception) {
            log.error("[Agent][Budget] provider={} usageDate={} status=SETTLEMENT_FAILED reservedBudgetRetained=true causeType={}",
                    DEEPSEEK_PROVIDER, reservation.usageDate(), exception.getClass().getSimpleName());
        }
    }

    private void ensureDailyDocument(String usageId, LocalDate usageDate) {
        LocalDateTime now = LocalDateTime.now(BILLING_ZONE);
        Update update = new Update()
                .setOnInsert("provider", DEEPSEEK_PROVIDER)
                .setOnInsert("usageDate", usageDate)
                .setOnInsert("allocatedCostMicros", 0L)
                .setOnInsert("settledCostMicros", 0L)
                .setOnInsert("inputTokens", 0L)
                .setOnInsert("outputTokens", 0L)
                .setOnInsert("requestCount", 0L)
                .setOnInsert("conservativeSettlementCount", 0L)
                .setOnInsert("createdAt", now)
                .setOnInsert("updatedAt", now);
        mongoTemplate.upsert(Query.query(Criteria.where("id").is(usageId)), update,
                AgentApiDailyBudgetDocument.class);
    }

    private BudgetControlException budgetExceeded(long remainingMicros, long requiredMicros) {
        return new BudgetControlException("AGENT_DAILY_BUDGET_EXCEEDED",
                "DeepSeek当日API费用预算不足，剩余额度" + toCny(Math.max(remainingMicros, 0L))
                        + "元，本次至少需要预留" + toCny(requiredMicros) + "元");
    }

    private AgentProperties.DailyBudgetProperties dailyBudget() {
        AgentProperties.DeepSeekProperties deepSeek = properties.getDeepseek();
        if (deepSeek == null || deepSeek.getDailyBudget() == null) {
            throw new BudgetControlException("AGENT_DAILY_BUDGET_INVALID",
                    "DeepSeek每日费用预算配置缺失");
        }
        return deepSeek.getDailyBudget();
    }

    private void validateConfig(AgentProperties.DailyBudgetProperties config) {
        if (!isPositive(config.getDailyLimitCny()) || !isPositive(config.getInputPriceCnyPerMillion())
                || !isPositive(config.getOutputPriceCnyPerMillion()) || !isPositive(config.getSafetyFactor())) {
            throw new BudgetControlException("AGENT_DAILY_BUDGET_INVALID",
                    "DeepSeek每日费用预算或Token单价配置无效");
        }
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private long calculateCostMicros(long inputTokens, long outputTokens,
                                     AgentProperties.DailyBudgetProperties config) {
        BigDecimal inputCost = config.getInputPriceCnyPerMillion()
                .multiply(BigDecimal.valueOf(inputTokens));
        BigDecimal outputCost = config.getOutputPriceCnyPerMillion()
                .multiply(BigDecimal.valueOf(outputTokens));
        BigDecimal estimatedCny = inputCost.add(outputCost)
                .divide(ONE_MILLION, 12, RoundingMode.CEILING)
                .multiply(config.getSafetyFactor());
        return toMicros(estimatedCny);
    }

    private long toMicros(BigDecimal cny) {
        if (cny == null || cny.signum() <= 0) {
            return 0L;
        }
        return cny.multiply(MICROS_PER_CNY).setScale(0, RoundingMode.CEILING).longValueExact();
    }

    private BigDecimal toCny(long micros) {
        return BigDecimal.valueOf(micros).divide(MICROS_PER_CNY, 6, RoundingMode.HALF_UP);
    }

    private long utf8Length(String value) {
        return value == null ? 0L : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private long safeTokenCount(Integer value) {
        return value == null ? 0L : Math.max(value.longValue(), 0L);
    }

    private String usageId(String provider, LocalDate usageDate) {
        return provider + ":" + usageDate;
    }

    /** 单次模型调用的预算预留。 */
    public record BudgetReservation(boolean limited, String usageId, LocalDate usageDate,
                                    long reservedCostMicros, long dailyLimitMicros) {

        private static BudgetReservation unlimited() {
            return new BudgetReservation(false, null, null, 0L, 0L);
        }
    }

    /** 当日费用预算只读快照。 */
    public record BudgetSnapshot(boolean enabled, LocalDate usageDate, long dailyLimitCostMicros,
                                 long allocatedCostMicros, long settledCostMicros, long remainingCostMicros,
                                 long inputTokens, long outputTokens, long requestCount,
                                 long conservativeSettlementCount) {
    }

    /** 每日费用预算拒绝或不可用异常。 */
    public static class BudgetControlException extends RuntimeException {

        private final String errorCode;

        private BudgetControlException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        private BudgetControlException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        /**
         * 返回稳定错误码。
         *
         * @return 预算控制错误码
         */
        public String getErrorCode() {
            return errorCode;
        }
    }
}
// AI_GENERATE_END---------