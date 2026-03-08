package com.stock.service;

import com.alibaba.fastjson2.JSON;
import com.stock.handler.NotificationWebSocketHandler;
import com.stock.tradingExecutor.entity.OrderResult;
import com.stock.tradingExecutor.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationWebSocketHandler notificationHandler;

    public void notifyOrder(OrderResult result, String type) {
        if (result == null) {
            return;
        }

        NotificationMessage message = NotificationMessage.builder()
                .id(UUID.randomUUID().toString())
                .type(type) // BUY or SELL
                .level(getLevelByStatus(result.getStatus()))
                .title(getTitle(type, result.getStatus()))
                .content(result.getMessage())
                .timestamp(LocalDateTime.now().toString())
                .data(result)
                .build();

        String json = JSON.toJSONString(message);
        log.info("Sending notification: {}", json);
        notificationHandler.broadcast(json);
    }
    
    public void notifyError(String title, String errorMsg) {
         NotificationMessage message = NotificationMessage.builder()
                .id(UUID.randomUUID().toString())
                .type("ERROR") 
                .level("error")
                .title(title)
                .content(errorMsg)
                .timestamp(LocalDateTime.now().toString())
                .build();
        
        notificationHandler.broadcast(JSON.toJSONString(message));
    }

    private String getLevelByStatus(OrderStatus status) {
        if (status == OrderStatus.FILLED) {
            return "success";
        } else if (status == OrderStatus.REJECTED || status == OrderStatus.CANCELLED) {
            return "error";
        } else {
            return "info";
        }
    }

    private String getTitle(String type, OrderStatus status) {
        String action = "BUY".equals(type) ? "买入" : "卖出";
        if (status == OrderStatus.FILLED) {
            return action + "成交";
        } else if (status == OrderStatus.REJECTED) {
            return action + "被拒绝";
        } else if (status == OrderStatus.CANCELLED) {
            return action + "已取消";
        } else {
            return action + "通知";
        }
    }

    @Data
    @Builder
    public static class NotificationMessage {
        private String id;
        private String type;
        private String level; // success, info, warning, error
        private String title;
        private String content;
        private String timestamp;
        private Object data;
    }
}
