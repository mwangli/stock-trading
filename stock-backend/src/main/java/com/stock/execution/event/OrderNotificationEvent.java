// AI_GENERATE_START-----
package com.stock.execution.event;

import com.stock.execution.vo.OrderResult;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 订单执行结果通知事件。
 * 用于在交易执行完成后向日志、消息或其他监听器发布统一结果。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Getter
public class OrderNotificationEvent extends ApplicationEvent {

    /** 订单执行结果。 */
    private final OrderResult result;

    /** 通知类型。 */
    private final String type;

    /**
     * 创建订单结果通知事件。
     *
     * @param source 事件来源
     * @param result 订单执行结果
     * @param type 通知类型
     */
    public OrderNotificationEvent(Object source, OrderResult result, String type) {
        super(source);
        this.result = result;
        this.type = type;
    }
}

// AI_GENERATE_END-----
