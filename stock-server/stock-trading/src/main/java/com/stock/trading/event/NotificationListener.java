// AI_GENERATE_START------
package com.stock.trading.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 订单事件与可插拔通知接口之间的适配器。
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Slf4j
@Component
public class NotificationListener {

    /**
     * 异步转发订单结果，通知实现不得阻塞交易主流程。
     *
     * @param event 订单通知事件
     */
    @Async
    @EventListener
    public void handleOrderNotificationEvent(OrderNotificationEvent event) {
        log.debug("接收订单通知事件: type={}, orderId={}",
                event.getType(), event.getResult().getOrderId());
        log.info("订单执行结果通知: type={}, orderId={}, success={}",
                event.getType(), event.getResult().getOrderId(), event.getResult().isSuccess());
    }
}
// AI_GENERATE_END------
