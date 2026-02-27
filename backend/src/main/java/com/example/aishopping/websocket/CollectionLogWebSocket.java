package com.example.aishopping.websocket;

import lombok.extern.slf4j.Slf4j;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket服务端点 - 推送采集日志
 */
@ServerEndpoint("/ws/collection-log/{taskId}")
@Slf4j
public class CollectionLogWebSocket {

    /**
     * 存储所有连接的任务ID -> 会话集合
     */
    private static final Map<Long, CopyOnWriteArraySet<Session>> TASK_SESSIONS = new ConcurrentHashMap<>();

    /**
     * 存储所有连接的会话
     */
    private static final CopyOnWriteArraySet<Session> ALL_SESSIONS = new CopyOnWriteArraySet<>();

    /**
     * 连接建立成功
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("taskId") Long taskId) {
        ALL_SESSIONS.add(session);
        
        if (taskId != null && taskId > 0) {
            TASK_SESSIONS.computeIfAbsent(taskId, k -> new CopyOnWriteArraySet<>()).add(session);
            log.info("WebSocket连接建立: taskId={}, sessionId={}", taskId, session.getId());
        } else {
            log.info("WebSocket连接建立: 监听所有日志, sessionId={}", session.getId());
        }
    }

    /**
     * 连接关闭
     */
    @OnClose
    public void onClose(Session session, @PathParam("taskId") Long taskId) {
        ALL_SESSIONS.remove(session);
        
        if (taskId != null && taskId > 0) {
            CopyOnWriteArraySet<Session> sessions = TASK_SESSIONS.get(taskId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    TASK_SESSIONS.remove(taskId);
                }
            }
        }
        log.info("WebSocket连接关闭: sessionId={}", session.getId());
    }

    /**
     * 收到客户端消息
     */
    @OnMessage
    public void onMessage(String message, Session session, @PathParam("taskId") Long taskId) {
        log.debug("收到WebSocket消息: {}", message);
    }

    /**
     * 发生错误
     */
    @OnError
    public void onError(Session session, Throwable error, @PathParam("taskId") Long taskId) {
        log.error("WebSocket错误: sessionId={}, taskId={}", session.getId(), taskId, error);
    }

    /**
     * 推送日志消息到指定任务
     */
    public static void sendLog(Long taskId, String logMessage) {
        if (taskId == null || logMessage == null) {
            return;
        }

        CopyOnWriteArraySet<Session> sessions = TASK_SESSIONS.get(taskId);
        if (sessions != null && !sessions.isEmpty()) {
            for (Session session : sessions) {
                sendMessage(session, logMessage);
            }
        }
    }

    /**
     * 推送日志消息到所有连接
     */
    public static void broadcastLog(String logMessage) {
        if (logMessage == null) {
            return;
        }

        for (Session session : ALL_SESSIONS) {
            sendMessage(session, logMessage);
        }
    }

    /**
     * 发送消息到会话
     */
    private static void sendMessage(Session session, String message) {
        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            synchronized (session) {
                session.getBasicRemote().sendText(message);
            }
        } catch (IOException e) {
            log.error("发送WebSocket消息失败: {}", e.getMessage());
        }
    }

    /**
     * 获取当前连接数
     */
    public static int getConnectionCount() {
        return ALL_SESSIONS.size();
    }

    /**
     * 获取指定任务的连接数
     */
    public static int getConnectionCount(Long taskId) {
        CopyOnWriteArraySet<Session> sessions = TASK_SESSIONS.get(taskId);
        return sessions != null ? sessions.size() : 0;
    }
}
