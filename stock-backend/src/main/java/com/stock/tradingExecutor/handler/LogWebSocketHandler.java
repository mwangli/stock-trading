// AI_GENERATE_START -
package com.stock.tradingExecutor.handler;

import com.stock.tradingExecutor.logging.LogBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 后台运行日志 WebSocket 连接处理器。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogWebSocketHandler extends TextWebSocketHandler {

    private final LogBroadcastService logBroadcastService;

    /**
     * 注册新建立的日志 WebSocket 会话并回放近期日志。
     *
     * @param session WebSocket 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logBroadcastService.registerSession(session);
        log.info("运行日志 WebSocket 连接已建立: sessionId={}", session.getId());
    }

    /**
     * 移除已关闭的日志 WebSocket 会话。
     *
     * @param session WebSocket 会话
     * @param status  关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logBroadcastService.removeSession(session);
        log.info("运行日志 WebSocket 连接已关闭: sessionId={}, status={}", session.getId(), status);
    }
}
// AI_GENERATE_END -
