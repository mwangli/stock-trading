package com.stock.tradingExecutor.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 日志广播服务
 * 负责维护 WebSocket 会话集合，并将后端运行日志广播给所有在线前端客户端。
 * 通过 Spring 容器进行统一管理，避免在 devtools 场景下因类加载器隔离导致静态字段不一致的问题。
 *
 * @author mwangli
 * @since 2026-03-11
 */
@Slf4j
@Component
public class LogBroadcastService {

    /**
     * 当前所有在线的 WebSocket 会话集合
     */
    private final Set<WebSocketSession> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * 注册新的 WebSocket 会话
     *
     * @param session 新建立的 WebSocket 会话
     */
    public void registerSession(WebSocketSession session) {
        sessions.add(session);
        log.info("Register WebSocket log session, id={}, total={}", session.getId(), sessions.size());
    }

    /**
     * 移除已关闭的 WebSocket 会话
     *
     * @param session 已关闭的 WebSocket 会话
     */
    public void removeSession(WebSocketSession session) {
        sessions.remove(session);
        log.info("Remove WebSocket log session, id={}, total={}", session.getId(), sessions.size());
    }

    /**
     * 向所有在线会话广播日志消息
     *
     * @param message 待广播的日志内容
     */
    public void broadcast(String message) {
        int sessionCount = sessions.size();
        if (sessionCount == 0) {
            return;
        }
        log.debug("Broadcast log message via WebSocket, sessions={}, message={}", sessionCount, message);
        TextMessage textMessage = new TextMessage(message != null ? message : "");
        sessions.forEach(session -> {
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

