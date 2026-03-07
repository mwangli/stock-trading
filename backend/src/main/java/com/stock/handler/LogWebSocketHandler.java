package com.stock.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LogWebSocketHandler extends TextWebSocketHandler {

    // Thread-safe set to store active sessions
    private static final Set<WebSocketSession> SESSIONS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        SESSIONS.add(session);
        log.info("New WebSocket connection established: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        SESSIONS.remove(session);
        log.info("WebSocket connection closed: {}", session.getId());
    }

    /**
     * Broadcasts a log message to all connected clients.
     * This method is static so it can be called from anywhere (e.g., Appender).
     */
    public static void broadcast(String message) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        TextMessage textMessage = new TextMessage(message);
        SESSIONS.forEach(session -> {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("Failed to send log message to session {}", session.getId(), e);
                }
            }
        });
    }
}
