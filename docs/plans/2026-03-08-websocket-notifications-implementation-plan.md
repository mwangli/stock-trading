# WebSocket Notifications Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 新增轻量通知 WebSocket 端点 `/ws/notifications`，支持连接管理与文本广播。

**Architecture:** 通过 `NotificationHandler`（继承 `TextWebSocketHandler`）维护 `CopyOnWriteArraySet` 会话集合，提供 `broadcast` 发送文本消息；`WebSocketConfig` 注册该 handler 并允许所有来源。

**Tech Stack:** Java 17, Spring Boot 3.2, Spring WebSocket

---

### Task 1: 新增 NotificationHandler

**Files:**
- Create: `backend/src/main/java/com/stock/handler/NotificationHandler.java`
- Test: （无新增测试）

**Step 1: 写一个失败测试（可选）**

> 本需求为轻量配置与 handler，当前项目未提供 WebSocket 测试基建，跳过自动化测试。

**Step 2: 实现 NotificationHandler**

```java
package com.stock.handler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
public class NotificationHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                session.sendMessage(textMessage);
            } catch (IOException ex) {
                sessions.remove(session);
                log.warn("Failed to send notification message", ex);
            }
        }
    }
}
```

**Step 3: 运行静态诊断**

Run: `lsp_diagnostics` on `NotificationHandler.java`
Expected: 0 errors

**Step 4: （如需）运行编译**

Run: `mvn -q -DskipTests compile` in `backend`
Expected: BUILD SUCCESS

**Step 5: Commit**

> 仅在用户明确要求提交时执行。

---

### Task 2: 注册 WebSocket 端点

**Files:**
- Modify: `backend/src/main/java/com/stock/config/WebSocketConfig.java`

**Step 1: 更新配置**

```java
import com.stock.handler.NotificationHandler;
```

新增字段：
```java
private final NotificationHandler notificationHandler;
```

在 `registerWebSocketHandlers` 中注册：
```java
registry.addHandler(notificationHandler, "/ws/notifications")
        .setAllowedOrigins("*");
```

**Step 2: 运行静态诊断**

Run: `lsp_diagnostics` on `WebSocketConfig.java`
Expected: 0 errors

**Step 3: （如需）运行编译**

Run: `mvn -q -DskipTests compile` in `backend`
Expected: BUILD SUCCESS

**Step 4: Commit**

> 仅在用户明确要求提交时执行。
