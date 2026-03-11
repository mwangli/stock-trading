package com.stock.tradingExecutor.handler;

import com.stock.tradingExecutor.logging.LogBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogWebSocketHandler extends TextWebSocketHandler {

    private final LogBroadcastService logBroadcastService;
    private static final Set<WebSocketSession> SESSIONS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        SESSIONS.add(session);
        logBroadcastService.registerSession(session);
        log.info("New WebSocket connection established: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        SESSIONS.remove(session);
        logBroadcastService.removeSession(session);
        log.info("WebSocket connection closed: {}", session.getId());
    }

    /**
     * 向所有已连接客户端广播日志消息
     */
    public static void broadcast(String message) {
        int sessionCount = SESSIONS.size();
        log.debug("WebSocket broadcast invoked (legacy static), sessions={}, message={}", sessionCount, message);
    }
}
