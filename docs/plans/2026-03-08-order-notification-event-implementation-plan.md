# 订单通知事件化 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 使用 Spring Event 解耦 `TradeExecutor` 与 `NotificationService`，并更新交易执行设计文档。

**Architecture:** `TradeExecutor` 发布 `OrderNotificationEvent`，`NotificationListener` 监听并调用 `NotificationService.notifyOrder`，后续由 `NotificationWebSocketHandler` 推送前端。

**Tech Stack:** Java 17, Spring Boot 3.2.x, Spring Events, JUnit 5

---

### Task 1: 新增订单通知事件类

**Files:**
- Create: `backend/src/main/java/com/stock/event/OrderNotificationEvent.java`

**Step 1: 写一个最小的事件类（无测试）**

```java
package com.stock.event;

import com.stock.tradingExecutor.entity.OrderResult;
import org.springframework.context.ApplicationEvent;

public class OrderNotificationEvent extends ApplicationEvent {
    private final OrderResult result;
    private final String type;

    public OrderNotificationEvent(Object source, OrderResult result, String type) {
        super(source);
        this.result = result;
        this.type = type;
    }

    public OrderResult getResult() {
        return result;
    }

    public String getType() {
        return type;
    }
}
```

**Step 2: LSP 诊断确认无语法问题**

Run: `lsp_diagnostics` on `backend/src/main/java/com/stock/event/OrderNotificationEvent.java`
Expected: 0 errors

**Step 3: Commit**

```bash
git add backend/src/main/java/com/stock/event/OrderNotificationEvent.java
git commit -m "feat: add order notification event"
```

---

### Task 2: 新增通知监听器并补充单元测试

**Files:**
- Create: `backend/src/main/java/com/stock/listener/NotificationListener.java`
- Create: `backend/src/test/java/com/stock/listener/NotificationListenerTest.java`

**Step 1: 编写失败测试（验证监听器会调用 NotificationService）**

```java
package com.stock.listener;

import com.stock.event.OrderNotificationEvent;
import com.stock.service.NotificationService;
import com.stock.tradingExecutor.entity.OrderResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NotificationListenerTest {
    @Test
    void shouldDelegateToNotificationService() {
        NotificationService service = Mockito.mock(NotificationService.class);
        NotificationListener listener = new NotificationListener(service);
        OrderResult result = OrderResult.fail("test");

        listener.handleOrderNotificationEvent(
                new OrderNotificationEvent(this, result, "BUY")
        );

        Mockito.verify(service).notifyOrder(result, "BUY");
    }
}
```

**Step 2: 运行测试确认失败（类未实现）**

Run: `mvn test -Dtest=NotificationListenerTest`
Expected: FAIL (NotificationListener not found)

**Step 3: 实现监听器**

```java
package com.stock.listener;

import com.stock.event.OrderNotificationEvent;
import com.stock.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationListener {
    private final NotificationService notificationService;

    @EventListener
    public void handleOrderNotificationEvent(OrderNotificationEvent event) {
        notificationService.notifyOrder(event.getResult(), event.getType());
    }
}
```

**Step 4: 运行测试确认通过**

Run: `mvn test -Dtest=NotificationListenerTest`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/java/com/stock/listener/NotificationListener.java backend/src/test/java/com/stock/listener/NotificationListenerTest.java
git commit -m "test: add notification listener test and implementation"
```

---

### Task 3: 更新 TradeExecutor 发布事件

**Files:**
- Modify: `backend/src/main/java/com/stock/tradingExecutor/execution/TradeExecutor.java`

**Step 1: 替换依赖并发布事件**

```java
import com.stock.event.OrderNotificationEvent;
import org.springframework.context.ApplicationEventPublisher;

private final ApplicationEventPublisher publisher;

// 替换 notifyOrder 调用
publisher.publishEvent(new OrderNotificationEvent(this, result, "BUY"));
publisher.publishEvent(new OrderNotificationEvent(this, result, "SELL"));
```

**Step 2: LSP 诊断确认无语法问题**

Run: `lsp_diagnostics` on `backend/src/main/java/com/stock/tradingExecutor/execution/TradeExecutor.java`
Expected: 0 errors

**Step 3: Commit**

```bash
git add backend/src/main/java/com/stock/tradingExecutor/execution/TradeExecutor.java
git commit -m "refactor: publish order notification events"
```

---

### Task 4: 更新交易执行设计文档

**Files:**
- Modify: `docs/design/04-交易执行设计.md`

**Step 1: 添加“系统通知设计”章节**

示例内容（插入到“组件交互关系”或“关键设计决策”附近）：

```markdown
## 系统通知设计

交易执行完成后通过 Spring Event 解耦通知链路：

TradeExecutor -> ApplicationEventPublisher -> OrderNotificationEvent
-> NotificationListener -> NotificationService -> NotificationWebSocketHandler -> 前端

该设计保证交易执行与通知服务解耦，同时保持通知时序一致。
```

**Step 2: LSP/Markdown 校验（可选）**

如有 Markdown 校验工具可运行；否则略过。

**Step 3: Commit**

```bash
git add docs/design/04-交易执行设计.md
git commit -m "docs: add system notification design"
```

---

### Task 5: 全量验证

**Step 1: 后端 LSP 诊断**

Run: `lsp_diagnostics` on all modified Java files
Expected: 0 errors

**Step 2: 运行后端测试**

Run: `mvn test`
Expected: PASS

**Step 3: Commit（如有零散修改）**

```bash
git add .
git commit -m "test: verify notification event wiring"
```

---

## Notes
- 如果项目测试运行较慢，可先运行单测 `mvn test -Dtest=NotificationListenerTest` 再全量测试。
- 不需要更新前端代码，通知格式保持不变。
