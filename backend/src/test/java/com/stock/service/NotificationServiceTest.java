package com.stock.service;

import com.stock.handler.NotificationWebSocketHandler;
import com.stock.tradingExecutor.entity.OrderResult;
import com.stock.tradingExecutor.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationWebSocketHandler notificationHandler;

    @Test
    void notifyOrder_callsBroadcast() {
        NotificationService service = new NotificationService(notificationHandler);
        OrderResult result = OrderResult.builder()
                .success(true)
                .status(OrderStatus.FILLED)
                .message("成交")
                .build();

        service.notifyOrder(result, "BUY");

        verify(notificationHandler).broadcast(anyString());
    }
}
