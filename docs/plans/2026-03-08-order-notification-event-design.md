# 订单通知事件化设计（TradeExecutor 解耦）

## 背景
当前 `TradeExecutor` 直接依赖 `NotificationService` 并同步调用 `notifyOrder`，导致交易执行与通知链路耦合。需要引入 Spring Event 以降低依赖并保持通知语义一致。

## 目标
- 通过 Spring Event 解耦 `TradeExecutor` 与 `NotificationService`。
- 保持通知链路与现有同步语义一致。
- 文档中明确通知架构（Event + WebSocket）。

## 非目标
- 不引入消息中间件（Kafka/Redis）。
- 不改变通知内容格式与 WebSocket 协议。
- 不引入异步通知（除非后续需求明确）。

## 现状概述
- `TradeExecutor` 在 `doExecuteBuy` / `doExecuteSell` 中调用 `NotificationService.notifyOrder`。
- `NotificationService` 负责封装 `NotificationMessage` 并调用 `NotificationWebSocketHandler.broadcast` 推送。

## 方案（推荐：同步 Spring Event）
### 组件变更
1. 新增事件类 `OrderNotificationEvent`：继承 `ApplicationEvent`，携带 `OrderResult result` 与 `String type`。
2. 新增监听器 `NotificationListener`：`@Component` + `@EventListener`，注入 `NotificationService`，在事件到达时调用 `notifyOrder`。
3. `TradeExecutor` 改为注入 `ApplicationEventPublisher`，发布 `OrderNotificationEvent`。

### 数据流
`TradeExecutor` → `ApplicationEventPublisher` → `NotificationListener` → `NotificationService` → `NotificationWebSocketHandler` → 前端

## 接口与类设计
- `OrderNotificationEvent(ApplicationEvent)`
  - 字段：`OrderResult result`、`String type`
  - 作用：承载订单通知数据并被 Spring 事件总线传播

- `NotificationListener`
  - 方法：`handleOrderNotificationEvent(OrderNotificationEvent event)`
  - 行为：`notificationService.notifyOrder(event.getResult(), event.getType())`

- `TradeExecutor`
  - 替换：`NotificationService` 依赖 → `ApplicationEventPublisher` 依赖
  - 发布：`publisher.publishEvent(new OrderNotificationEvent(this, result, type))`

## 异常处理
- 监听器内部由 `NotificationService` 处理异常与日志。
- 事件发布保持同步语义，异常策略与现有调用链一致。

## 测试与验证
- LSP 诊断：新增事件类、监听器、更新 `TradeExecutor` 无语法错误。
- 编译与测试：执行 `mvn test`（后端）验证通知行为未回归。

## 文档变更
- 在 `docs/design/04-交易执行设计.md` 中增加“系统通知设计”章节，描述 Spring Event + WebSocket 结构。

## 影响评估
- 仅调整依赖注入与调用链，不影响业务结果或通知格式。
- 后续若需要异步通知，可在 `NotificationListener` 上引入 `@Async` 并配置线程池。
