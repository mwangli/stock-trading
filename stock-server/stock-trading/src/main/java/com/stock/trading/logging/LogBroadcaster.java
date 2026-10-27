// AI_GENERATE_START-------
package com.stock.trading.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
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
public class LogBroadcaster {

    private static final int MAX_RECENT_LOGS = 500;

    /**
     * 当前所有在线的 WebSocket 会话集合
     */
    private final Set<WebSocketSession> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * 最近后台日志缓存，新连接建立后立即回放。
     */
    private final Deque<String> recentLogs = new ArrayDeque<>(MAX_RECENT_LOGS);

    /**
     * 注册新的 WebSocket 会话
     *
     * @param session 新建立的 WebSocket 会话
     */
    public void registerSession(WebSocketSession session) {
        sessions.add(session);
        replayRecentLogs(session);
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
        String safeMessage = message != null ? message : "";
        cache(safeMessage);
        int sessionCount = sessions.size();
        log.debug("Broadcast log message via WebSocket, sessions={}, message={}", sessionCount, message);
        TextMessage textMessage = new TextMessage(safeMessage);
        List<WebSocketSession> failedSessions = new ArrayList<>();
        sessions.forEach(session -> {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("Failed to send log message to session {}", session.getId(), e);
                    failedSessions.add(session);
                }
            } else {
                failedSessions.add(session);
            }
        });
        sessions.removeAll(failedSessions);
    }

    /**
     * 将日志写入有界内存缓存。
     *
     * @param message 已格式化日志
     */
    private synchronized void cache(String message) {
        while (recentLogs.size() >= MAX_RECENT_LOGS) {
            recentLogs.removeFirst();
        }
        recentLogs.addLast(message);
    }

    /**
     * 向新连接回放最近后台日志。
     *
     * @param session 新建立的 WebSocket 会话
     */
    private void replayRecentLogs(WebSocketSession session) {
        List<String> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(recentLogs);
        }
        try {
            synchronized (session) {
                for (String recentLog : snapshot) {
                    session.sendMessage(new TextMessage(recentLog));
                }
            }
        } catch (IOException exception) {
            sessions.remove(session);
            log.warn("Failed to replay recent logs to session {}", session.getId(), exception);
        }
    }
}
// AI_GENERATE_END-------
