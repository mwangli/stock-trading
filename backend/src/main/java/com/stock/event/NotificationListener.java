package com.stock.event;

import com.stock.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 订单通知事件监听器
 * 监听 OrderNotificationEvent 并转发给 NotificationService 进行 WebSocket 推送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationService notificationService;

    /**
     * 处理订单通知事件
     *
     * @param event 订单通知事件
     */
    @EventListener
    public void handleOrderNotificationEvent(OrderNotificationEvent event) {
        log.info("Received order notification event: type={}, orderId={}", 
                event.getType(), event.getResult().getOrderId());
        notificationService.notifyOrder(event.getResult(), event.getType());
    }
}
