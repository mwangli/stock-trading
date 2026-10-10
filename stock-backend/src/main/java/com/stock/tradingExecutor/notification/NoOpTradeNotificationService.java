// AI_GENERATE_START -
package com.stock.tradingExecutor.notification;

import com.stock.tradingExecutor.domain.vo.OrderResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 未启用通知渠道时的空实现。
 * 订单和异常已由交易日志及持久化记录留痕，本实现不发送外部消息。
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Slf4j
@Service
public class NoOpTradeNotificationService implements TradeNotificationPort {

    /**
     * 记录通知已被预留接口接收，但不执行外部投递。
     *
     * @param result 订单执行结果
     * @param type 买卖方向
     */
    @Override
    public void publishOrderResult(OrderResult result, String type) {
        log.debug("通知渠道未启用，跳过订单通知: type={}, orderId={}",
                type, result == null ? null : result.getOrderId());
    }

    /**
     * 记录通知已被预留接口接收，但不执行外部投递。
     *
     * @param title 异常标题
     * @param errorMessage 异常内容
     */
    @Override
    public void publishError(String title, String errorMessage) {
        log.debug("通知渠道未启用，跳过异常通知: title={}", title);
    }
}
// AI_GENERATE_END -
