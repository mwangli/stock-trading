package com.stock.event;

import com.stock.tradingExecutor.entity.OrderResult;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OrderNotificationEvent extends ApplicationEvent {

    private final OrderResult result;
    private final String type;

    public OrderNotificationEvent(Object source, OrderResult result, String type) {
        super(source);
        this.result = result;
        this.type = type;
    }
}
