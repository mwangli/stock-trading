// AI_GENERATE_START --
package com.stock.tradingExecutor.service;

import com.alibaba.fastjson2.JSON;
import com.stock.tradingExecutor.domain.entity.OrderStatus;
import com.stock.tradingExecutor.domain.vo.OrderResult;
import com.stock.tradingExecutor.handler.NotificationWebSocketHandler;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 交易通知服务。
 * 同时发送 PC WebSocket 通知和默认关闭的微信小程序订阅消息。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationWebSocketHandler notificationHandler;
    private final WeChatNotificationService weChatNotificationService;

    /**
     * 发送订单状态通知。
     *
     * @param result 订单执行结果
     * @param type 买卖方向
     */
    public void notifyOrder(OrderResult result, String type) {
        if (result == null) {
            return;
        }
        String title = getTitle(type, result.getStatus());
        String content = result.getMessage() == null ? "订单状态已更新" : result.getMessage();
        NotificationMessage message = NotificationMessage.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .level(getLevelByStatus(result.getStatus()))
                .title(title)
                .content(content)
                .timestamp(LocalDateTime.now().toString())
                .data(result)
                .build();
        notificationHandler.broadcast(JSON.toJSONString(message));
        weChatNotificationService.send(title, content);
    }

    /**
     * 发送交易系统异常通知。
     *
     * @param title 异常标题
     * @param errorMsg 异常内容
     */
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
        weChatNotificationService.send(title, errorMsg);
    }

    private String getLevelByStatus(OrderStatus status) {
        if (status == OrderStatus.FILLED) {
            return "success";
        }
        if (status == OrderStatus.REJECTED || status == OrderStatus.CANCELLED || status == null) {
            return "error";
        }
        return "info";
    }

    private String getTitle(String type, OrderStatus status) {
        String action = "BUY".equalsIgnoreCase(type) ? "买入" : "SELL".equalsIgnoreCase(type) ? "卖出" : "订单";
        if (status == OrderStatus.FILLED) {
            return action + "成交";
        }
        if (status == OrderStatus.REJECTED) {
            return action + "被拒绝";
        }
        if (status == OrderStatus.CANCELLED) {
            return action + "已取消";
        }
        if (status == null) {
            return action + "失败";
        }
        return action + "通知";
    }

    /**
     * 前端实时通知消息。
     *
     * @author mwangli
     * @since 2026-09-25
     */
    @Data
    @Builder
    public static class NotificationMessage {
        private String id;
        private String type;
        private String level;
        private String title;
        private String content;
        private String timestamp;
        private Object data;
    }
}
// AI_GENERATE_END --
