# WebSocket 实时日志设计方案

## 背景与目标
后端需要通过 WebSocket 实现实时日志推送，满足开发与运维对实时可视化日志的需求，同时保持日志输出对业务线程的最小影响。

## 范围
- 新增 WebSocket 端点 `/ws/logs`
- 新增自定义 Logback Appender，将日志广播到 WebSocket 会话
- 新增 `logback-spring.xml`，包含标准 Console/File 与异步 WebSocket Appender
- 保证日志推送为异步/非阻塞

## 架构概览
1. `WebSocketConfig` 注册 `LogWebSocketHandler` 至 `/ws/logs`。
2. `LogWebSocketHandler` 维护线程安全的 WebSocket 会话集合，并提供广播能力。
3. `WebSocketAppender` 接收日志事件，将格式化后的日志发送至 `LogWebSocketHandler`。
4. `logback-spring.xml` 中使用 `AsyncAppender` 包裹 `WebSocketAppender`，避免阻塞业务线程。

## 组件设计
### 1) com.stock.config.WebSocketConfig
- 实现 `WebSocketConfigurer`
- 在 `registerWebSocketHandlers` 中注册 `/ws/logs`
- 允许后续添加拦截器实现鉴权（预留扩展点）

### 2) com.stock.handler.LogWebSocketHandler
- 继承 `TextWebSocketHandler`
- 使用 `CopyOnWriteArraySet` 或 `ConcurrentHashMap` 保存活跃会话
- 在 `afterConnectionEstablished`/`afterConnectionClosed` 中维护会话集合
- 提供 `broadcast(String message)` 方法给 Appender 调用

### 3) com.stock.logging.WebSocketAppender
- 继承 `AppenderBase<ILoggingEvent>`
- 格式化日志事件（时间、级别、线程、logger、消息）
- 调用 `LogWebSocketHandler` 进行广播
- 避免抛出异常影响日志链路

## 数据流
日志事件 → Logback Appender → AsyncAppender 队列 → WebSocketHandler 广播 → 客户端接收

## 异常与性能
- WebSocket 连接异常时移除会话，防止内存泄漏
- `AsyncAppender` 配置 `queueSize`/`discardingThreshold`，保证高负载时不阻塞
- 广播前检查 `session.isOpen()`，避免无效写入

## 安全与扩展
默认不加鉴权（适用于内网/开发环境），预留拦截器扩展点以便后续升级为登录/管理员鉴权。

## 验证计划
- 运行 `mvn compile` 确保编译通过
- 手动连接 `/ws/logs`，验证实时日志输出

## 影响与回滚
- 只新增类与配置，不影响现有业务逻辑
- 如需回滚，移除新增类与 `logback-spring.xml` 配置即可
