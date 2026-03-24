package com.stock.tradingExecutor.execution;

import com.stock.tradingExecutor.config.PollerConfig;
import com.stock.tradingExecutor.domain.entity.OrderStatus;
import com.stock.tradingExecutor.domain.vo.PollState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 订单轮询器
 * 跟踪订单执行状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPoller {

    private final BrokerAdapter brokerAdapter;
    private final PollerConfig config;

    /**
     * 轮询状态缓存
     */
    private final Map<String, PollState> pollStates = new ConcurrentHashMap<>();

    /**
     * 定时任务执行器
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    /**
     * 轮询订单状态
     *
     * @param orderId 订单ID
     * @return 订单状态
     */
    public OrderStatus pollOrderStatus(String orderId) {
        PollState state = pollStates.computeIfAbsent(orderId, PollState::new);

        OrderStatus status = brokerAdapter.queryOrderStatus(orderId);
        state.setLastStatus(status);
        state.incrementPollCount();

        log.info("订单轮询: {} 状态={} 轮询次数={}", orderId, status, state.getPollCount());

        return status;
    }

    /**
     * 等待订单完成 (同步阻塞)
     */
    public OrderStatus waitForComplete(String orderId) {
        return waitForComplete(orderId, config.getMaxPollCount());
    }

    /**
     * 等待订单完成 (同步阻塞)
     */
    public OrderStatus waitForComplete(String orderId, int maxPolls) {
        PollState state = pollStates.computeIfAbsent(orderId, PollState::new);
        int pollCount = 0;

        while (pollCount < maxPolls) {
            pollCount++;
            OrderStatus status = pollOrderStatus(orderId);

            switch (status) {
                case FILLED:
                    log.info("订单成交: {}", orderId);
                    return OrderStatus.FILLED;

                case CANCELLED:
                    log.info("订单已撤销: {}", orderId);
                    return OrderStatus.CANCELLED;

                case REJECTED:
                    log.info("订单废单: {}", orderId);
                    return OrderStatus.REJECTED;

                case SUBMITTED:
                case PARTIAL:
                    if (isTimeout(state)) {
                        log.warn("订单超时: {}", orderId);
                        return OrderStatus.TIMEOUT;
                    }

                    try {
                        Thread.sleep(config.getPollIntervalSeconds() * 1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return OrderStatus.TIMEOUT;
                    }
                    break;

                default:
                    break;
            }
        }

        log.warn("订单轮询次数超限: {} 次数={}", orderId, pollCount);
        return OrderStatus.TIMEOUT;
    }

    private boolean isTimeout(PollState state) {
        if (state.getStartTime() == null) {
            return false;
        }
        long elapsedSeconds = Duration.between(state.getStartTime(), LocalDateTime.now()).getSeconds();
        return elapsedSeconds >= config.getTimeoutSeconds();
    }

    /**
     * 超时自动撤单
     */
    public boolean cancelIfTimeout(String orderId) {
        PollState state = pollStates.get(orderId);
        if (state == null) {
            return false;
        }
        if (isTimeout(state)) {
            log.info("订单超时，执行撤单: {}", orderId);
            return brokerAdapter.cancelOrder(orderId);
        }
        return false;
    }

    /**
     * 手动撤单
     */
    public boolean cancelOrder(String orderId) {
        log.info("手动撤单: {}", orderId);
        return brokerAdapter.cancelOrder(orderId);
    }

    /**
     * 异步等待订单完成
     */
    public void waitForCompleteAsync(String orderId, OrderCallback callback) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                PollState state = pollStates.computeIfAbsent(orderId, PollState::new);
                OrderStatus status = pollOrderStatus(orderId);

                boolean completed = false;
                boolean success = false;

                switch (status) {
                    case FILLED:
                        completed = true;
                        success = true;
                        break;

                    case CANCELLED:
                    case REJECTED:
                        completed = true;
                        success = false;
                        break;

                    case TIMEOUT:
                        completed = true;
                        success = false;
                        break;

                    default:
                        if (isTimeout(state)) {
                            cancelOrder(orderId);
                            state.setLastStatus(OrderStatus.TIMEOUT);
                            completed = true;
                            success = false;
                        }
                        if (state.getPollCount() >= config.getMaxPollCount()) {
                            completed = true;
                            success = false;
                        }
                        break;
                }

                if (completed) {
                    scheduler.shutdown();
                    if (callback != null) {
                        callback.onComplete(orderId, status, success);
                    }
                }
            } catch (Exception e) {
                log.error("订单轮询异常: {}", orderId, e);
            }
        }, 0, config.getPollIntervalSeconds(), TimeUnit.SECONDS);
    }

    public PollState getPollState(String orderId) {
        return pollStates.get(orderId);
    }

    public void clearPollState(String orderId) {
        pollStates.remove(orderId);
    }

    /**
     * 订单完成回调接口
     */
    public interface OrderCallback {
        void onComplete(String orderId, OrderStatus status, boolean success);
    }
}
