## 目标

为后端提供一个轻量级的 WebSocket 通知入口：新增 `NotificationHandler`，并在 `WebSocketConfig` 中注册 `/ws/notifications`，允许所有来源连接（`*`），支持简单的文本广播。

## 范围

- 仅新增通知 WebSocket Handler 与注册路径。
- 不引入 STOMP、鉴权、消息队列或复杂协议。

## 方案概述（推荐）

采用单一 `NotificationHandler`（继承 `TextWebSocketHandler`）管理连接会话：

- 使用 `CopyOnWriteArraySet<WebSocketSession>` 保存连接。
- 连接建立时加入集合，断开时移除。
- 提供 `broadcast(String message)` 遍历集合发送 `TextMessage`。
- 发送异常时不影响其它连接，并清理异常 session。

## 组件与职责

- `NotificationHandler`（`com.stock.handler`）：
  - 负责会话管理与广播发送。
  - 作为 Spring Bean 以便业务层注入调用 `broadcast`。

- `WebSocketConfig`（`com.stock.config`）：
  - 注册 `/ws/notifications` 端点。
  - 允许所有来源（`setAllowedOrigins("*")`）。

## 数据流

1. 客户端建立连接 → `afterConnectionEstablished` 添加 session。
2. 客户端断开连接 → `afterConnectionClosed` 移除 session。
3. 业务调用 `broadcast` → 遍历会话并发送文本消息。

## 错误处理

- 单次发送失败不终止整体广播。
- 对异常 session 进行移除，避免失效连接长期占用。

## 验证方式

- 启动后端后通过 WebSocket 客户端连接 `ws://<host>/ws/notifications`。
- 触发 `broadcast` 后应能收到文本消息。

## 非目标

- 不实现鉴权、订阅主题、消息持久化或协议扩展。
