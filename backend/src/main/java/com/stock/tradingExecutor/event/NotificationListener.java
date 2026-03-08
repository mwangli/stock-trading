package com.stock.tradingExecutor.event;

import com.stock.tradingExecutor.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 订单通知事件监听器，将 OrderNotificationEvent 转发给 NotificationService 做 WebSocket 推送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationService notificationService;

    @Async
    @EventListener
    public void handleOrderNotificationEvent(OrderNotificationEvent event) {
        log.info("Received order notification event: type={}, orderId={}",
                event.getType(), event.getResult().getOrderId());
        notificationService.notifyOrder(event.getResult(), event.getType());
    }
}
