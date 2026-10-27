// AI_GENERATE_START--------------
package com.stock.trading.service;

import com.stock.trading.adapter.ZXBrokerAdapter;
import com.stock.trading.config.PollerConfig;
import com.stock.trading.entity.OrderStatus;
import com.stock.trading.vo.PollState;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * 订单轮询器
 * 跟踪订单执行状态
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPoller {

    private final ZXBrokerAdapter brokerAdapter;
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
     * 同步等待订单完成。
     *
     * @param orderId 订单 ID
     * @return 完成、撤销、拒绝或超时状态
     */
    public OrderStatus waitForComplete(String orderId) {
        return waitForComplete(orderId, config.getMaxPollCount());
    }

    /**
     * 等待订单完成，并在外部任务关闭时提前结束等待以进入撤单流程。
     *
     * @param orderId 订单 ID
     * @param continuePolling 是否继续等待当前委托
     * @return 订单状态；任务关闭时返回 TIMEOUT 交由调用方撤单确认
     */
    public OrderStatus waitForComplete(String orderId, BooleanSupplier continuePolling) {
        return waitForComplete(orderId, config.getMaxPollCount(), continuePolling);
    }

    /**
     * 按指定最大轮询次数同步等待订单完成。
     *
     * @param orderId 订单 ID
     * @param maxPolls 最大轮询次数
     * @return 完成、撤销、拒绝或超时状态
     */
    public OrderStatus waitForComplete(String orderId, int maxPolls) {
        return waitForComplete(orderId, maxPolls, () -> true);
    }

    private OrderStatus waitForComplete(String orderId, int maxPolls, BooleanSupplier continuePolling) {
        PollState state = pollStates.computeIfAbsent(orderId, PollState::new);
        int pollCount = 0;

        while (pollCount < maxPolls) {
            if (!continuePolling.getAsBoolean()) {
                log.info("交易任务已关闭，订单停止等待并进入撤单: {}", orderId);
                return OrderStatus.TIMEOUT;
            }
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

                case PENDING:
                case SUBMITTED:
                case PARTIAL:
                case UNKNOWN:
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
     * 在订单已超时时发起撤单。
     *
     * @param orderId 订单 ID
     * @return 已发起撤单时返回 true
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
     * 发起指定订单撤单。
     *
     * @param orderId 订单 ID
     * @return 券商接受撤单请求时返回 true
     */
    public boolean cancelOrder(String orderId) {
        log.info("手动撤单: {}", orderId);
        return brokerAdapter.cancelOrder(orderId);
    }

    /**
     * 异步等待订单完成。
     *
     * @param orderId 订单 ID
     * @param callback 完成回调，可为空
     */
    public void waitForCompleteAsync(String orderId, OrderCallback callback) {
        AtomicReference<ScheduledFuture<?>> futureReference = new AtomicReference<>();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
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
                    ScheduledFuture<?> currentFuture = futureReference.get();
                    if (currentFuture != null) {
                        currentFuture.cancel(false);
                    }
                    if (callback != null) {
                        callback.onComplete(orderId, status, success);
                    }
                }
            } catch (Exception e) {
                log.error("订单轮询异常: {}", orderId, e);
            }
        }, 0, config.getPollIntervalSeconds(), TimeUnit.SECONDS);
        futureReference.set(future);
    }

    /**
     * 撤销订单并等待券商确认终态。
     *
     * @param orderId 券商订单标识
     * @return 已确认的 FILLED、CANCELLED 或 REJECTED
     */
    public OrderStatus cancelAndAwaitTerminal(String orderId) {
        OrderStatus current = brokerAdapter.queryOrderStatus(orderId);
        if (current == OrderStatus.FILLED || current == OrderStatus.CANCELLED || current == OrderStatus.REJECTED) {
            return current;
        }
        if (!brokerAdapter.cancelOrder(orderId)) {
            throw new IllegalStateException("券商撤单请求失败，不能释放订单执行权: " + orderId);
        }
        int maxPolls = Math.max(1, config.getMaxRetryCount());
        for (int index = 0; index < maxPolls; index++) {
            OrderStatus status = brokerAdapter.queryOrderStatus(orderId);
            if (status == OrderStatus.FILLED || status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED) {
                return status;
            }
            sleepPollInterval();
        }
        throw new IllegalStateException("撤单后未确认订单终态，继续保留执行权: " + orderId);
    }

    private void sleepPollInterval() {
        try {
            Thread.sleep(config.getPollIntervalSeconds() * 1000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("订单终态确认被中断", exception);
        }
    }

    /**
     * 查询订单轮询状态。
     *
     * @param orderId 券商订单标识
     * @return 当前轮询状态，不存在时返回 null
     */
    public PollState getPollState(String orderId) {
        return pollStates.get(orderId);
    }

    /**
     * 清除已结束订单的轮询状态。
     *
     * @param orderId 券商订单标识
     */
    public void clearPollState(String orderId) {
        pollStates.remove(orderId);
    }

    /**
     * 订单完成回调接口
     */
    public interface OrderCallback {
        /**
         * 接收订单轮询完成结果。
         *
         * @param orderId 订单 ID
         * @param status 最终轮询状态
         * @param success 是否全部成交
         */
        void onComplete(String orderId, OrderStatus status, boolean success);
    }
}

// AI_GENERATE_END--------------
