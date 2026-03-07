# WebSocket 实时日志 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在后端通过 WebSocket 实现实时日志推送，并使用异步 Appender 保障非阻塞。

**Architecture:** 通过 `WebSocketConfig` 注册 `/ws/logs`，`LogWebSocketHandler` 管理会话并广播；`WebSocketAppender` 接收日志事件并异步推送；`logback-spring.xml` 同时保留 Console/File 并加入 Async WebSocket Appender。

**Tech Stack:** Spring Boot WebSocket、Logback、Java 17。

---

### Task 1: 新增 WebSocket 依赖

**Files:**
- Modify: `backend/pom.xml`

**Step 1: Write the failing test**

无新增测试（配置变更）。

**Step 2: Run test to verify it fails**

无。

**Step 3: Write minimal implementation**

在 `backend/pom.xml` 的 dependencies 中加入：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

**Step 4: Run test to verify it passes**

无。

**Step 5: Commit**

```bash
git add backend/pom.xml
git commit -m "feat: add websocket starter for realtime logging"
```

---

### Task 2: 新增 WebSocket 配置与处理器

**Files:**
- Create: `backend/src/main/java/com/stock/config/WebSocketConfig.java`
- Create: `backend/src/main/java/com/stock/handler/LogWebSocketHandler.java`

**Step 1: Write the failing test**

无新增测试（基础设施类）。

**Step 2: Run test to verify it fails**

无。

**Step 3: Write minimal implementation**

`WebSocketConfig`：

```java
package com.stock.config;

import com.stock.handler.LogWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final LogWebSocketHandler logWebSocketHandler;

    public WebSocketConfig(LogWebSocketHandler logWebSocketHandler) {
        this.logWebSocketHandler = logWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(logWebSocketHandler, "/ws/logs");
    }
}
```

`LogWebSocketHandler`：

```java
package com.stock.handler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class LogWebSocketHandler extends TextWebSocketHandler {
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcast(String message) {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException ex) {
                    sessions.remove(session);
                }
            } else {
                sessions.remove(session);
            }
        }
    }
}
```

**Step 4: Run test to verify it passes**

无。

**Step 5: Commit**

```bash
git add backend/src/main/java/com/stock/config/WebSocketConfig.java \
    backend/src/main/java/com/stock/handler/LogWebSocketHandler.java
git commit -m "feat: add websocket handler for log streaming"
```

---

### Task 3: 新增 WebSocket Logback Appender

**Files:**
- Create: `backend/src/main/java/com/stock/logging/WebSocketAppender.java`

**Step 1: Write the failing test**

无新增测试（日志基础设施）。

**Step 2: Run test to verify it fails**

无。

**Step 3: Write minimal implementation**

```java
package com.stock.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.stock.handler.LogWebSocketHandler;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class WebSocketAppender extends AppenderBase<ILoggingEvent> {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault());

    private final LogWebSocketHandler logWebSocketHandler;

    public WebSocketAppender(LogWebSocketHandler logWebSocketHandler) {
        this.logWebSocketHandler = logWebSocketHandler;
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        String message = String.format("%s [%s] [%s] %s - %s",
            FORMATTER.format(Instant.ofEpochMilli(eventObject.getTimeStamp())),
            eventObject.getLevel(),
            eventObject.getThreadName(),
            eventObject.getLoggerName(),
            eventObject.getFormattedMessage());
        logWebSocketHandler.broadcast(message);
    }
}
```

**Step 4: Run test to verify it passes**

无。

**Step 5: Commit**

```bash
git add backend/src/main/java/com/stock/logging/WebSocketAppender.java
git commit -m "feat: add logback appender for websocket logs"
```

---

### Task 4: 新增 logback-spring.xml 并启用异步 Appender

**Files:**
- Create: `backend/src/main/resources/logback-spring.xml`

**Step 1: Write the failing test**

无新增测试（配置文件）。

**Step 2: Run test to verify it fails**

无。

**Step 3: Write minimal implementation**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml" />

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${CONSOLE_LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/app.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>${FILE_LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <appender name="WEBSOCKET" class="com.stock.logging.WebSocketAppender" />

    <appender name="ASYNC_WEBSOCKET" class="ch.qos.logback.classic.AsyncAppender">
        <queueSize>2048</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <appender-ref ref="WEBSOCKET" />
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="FILE" />
        <appender-ref ref="ASYNC_WEBSOCKET" />
    </root>
</configuration>
```

**Step 4: Run test to verify it passes**

无。

**Step 5: Commit**

```bash
git add backend/src/main/resources/logback-spring.xml
git commit -m "feat: configure logback websocket appender"
```

---

### Task 5: 编译验证

**Files:**
- Modify: 无

**Step 1: Run compile**

```bash
mvn compile
```

Expected: 构建成功，退出码 0。

**Step 2: Commit**

无（除非产生必要变更）。

---

### Task 6: 运行期手动验证

**Files:**
- Modify: 无

**Step 1: 启动应用并连接 WebSocket**

连接 `ws://localhost:8080/ws/logs` 并观察实时日志输出。

**Step 2: 确认日志流动正常**

Expected: 收到与控制台一致的日志消息。
