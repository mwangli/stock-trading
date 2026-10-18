/* AI_GENERATE_START */
package com.stock.strategyAgent.runtime;

import com.stock.strategyAgent.config.AgentProperties;
import lombok.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent Provider 熔断器。
 *
 * <p>熔断仅保护 Agent 模型调用，不参与交易候选、风控和券商下单链路。</p>
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Component
public class AgentCircuitBreaker {

    private final AgentProperties properties;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    /**
     * 创建 Provider 级熔断器。
     *
     * @param properties Agent 运行配置
     */
    public AgentCircuitBreaker(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * 判断当前 Provider 是否允许一次调用。
     *
     * @param provider Provider 名称
     * @return 是否允许调用
     */
    public boolean allowRequest(String provider) {
        if (!properties.isCircuitBreakerEnabled()) {
            return true;
        }
        State state = states.computeIfAbsent(provider, ignored -> new State());
        long now = System.currentTimeMillis();
        if (state.openUntil.get() > now) {
            return false;
        }
        if (state.openUntil.get() > 0 && !state.halfOpenProbe.compareAndSet(false, true)) {
            return false;
        }
        return true;
    }

    /**
     * 记录成功调用并关闭熔断器。
     *
     * @param provider Provider 名称
     */
    public void recordSuccess(String provider) {
        State state = states.computeIfAbsent(provider, ignored -> new State());
        state.failures.set(0);
        state.openUntil.set(0);
        state.halfOpenProbe.set(false);
    }

    /**
     * 记录失败调用，达到阈值后打开熔断器。
     *
     * @param provider Provider 名称
     */
    public void recordFailure(String provider) {
        State state = states.computeIfAbsent(provider, ignored -> new State());
        state.halfOpenProbe.set(false);
        int failures = state.failures.incrementAndGet();
        if (failures >= Math.max(properties.getCircuitBreakerFailureThreshold(), 1)) {
            state.openUntil.set(System.currentTimeMillis()
                    + Math.max(properties.getCircuitBreakerOpenDurationMs(), 1L));
        }
    }

    /**
     * 查询 Provider 的熔断状态。
     *
     * @param provider Provider 名称
     * @return 熔断状态
     */
    public Snapshot snapshot(String provider) {
        State state = states.get(provider);
        if (!properties.isCircuitBreakerEnabled()) {
            return new Snapshot("DISABLED", 0, null);
        }
        if (state == null) {
            return new Snapshot("CLOSED", 0, null);
        }
        long openUntil = state.openUntil.get();
        if (openUntil > System.currentTimeMillis()) {
            return new Snapshot("OPEN", state.failures.get(), Duration.ofMillis(openUntil - System.currentTimeMillis()));
        }
        if (openUntil > 0) {
            return new Snapshot("HALF_OPEN", state.failures.get(), Duration.ZERO);
        }
        return new Snapshot("CLOSED", state.failures.get(), null);
    }

    /** Provider 熔断状态快照。 */
    @Value
    public static class Snapshot {
        /** 熔断状态。 */
        String state;
        /** 连续失败次数。 */
        int failureCount;
        /** 预计剩余打开时间。 */
        Duration remainingOpenDuration;
    }

    private static final class State {
        private final AtomicInteger failures = new AtomicInteger();
        private final AtomicLong openUntil = new AtomicLong();
        private final AtomicBoolean halfOpenProbe = new AtomicBoolean();
    }
}
/* AI_GENERATE_END */
