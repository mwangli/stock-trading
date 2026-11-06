<!-- AI_GENERATE_START -- -->
# 消息推送通知功能变更计划书

## 1. 目标与范围

本次改造在不改变交易和任务执行结果的前提下，为系统增加 Bark 与 PC 通知中心双通道通知能力。

- Trading 仓库：D:\stock-trading4，目标分支 stock-trading。
- Web 仓库：D:\stock-trading-web，目标分支 stock-web。
- PC 范围：仅改造 DesktopLayout，移动端不增加通知入口。
- 数据权责：Python 写入和清理 trading.db；Java 通过 DuckDB 只读查询。
- 交付物：本计划书和 output/notification-demo.html。

## 2. 总体方案

通知先转换为统一的结构化消息，再分别投递到两个互不依赖的通道：

1. Bark 通道维持现有移动推送能力。
2. SQLite 通道写入 notify_msg，由 PC 端轮询查询。
3. 任一通道失败只记录警告，不回滚另一通道，也不改变原交易或任务状态。
4. 浏览器只保存已读游标，不向数据库写入用户状态。

## 3. 数据库设计

SQLite 目标结构版本由 5 提升至 6，保留版本 5 已有迁移，不覆盖多模型改造。

    CREATE TABLE notify_msg (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        category TEXT NOT NULL CHECK(category IN ('SYSTEM', 'TRADE')),
        level TEXT NOT NULL CHECK(level IN ('INFO', 'SUCCESS', 'WARNING', 'ERROR')),
        title TEXT NOT NULL,
        content TEXT NOT NULL,
        source TEXT NOT NULL,
        event_key TEXT UNIQUE,
        created_at TEXT NOT NULL,
        expires_at TEXT NOT NULL
    );

索引：

- created_at DESC, id DESC：首次加载最新通知。
- category, id DESC：分类浏览和后续扩展。
- expires_at：清理到期数据。

保留策略：

| 分类 | 场景 | 保留时间 | 幂等策略 |
| --- | --- | ---: | --- |
| SYSTEM | CLI 运行异常 | 30 天 | 允许重复记录 |
| TRADE | 成交成功、拒绝、未知、停止 | 183 天 | 业务日期 + clientOrderId + 终态 |

## 4. Trading 侧改造

### 4.1 结构化通知

统一消息包含 category、level、title、content、source、retention_days、可选 event_key 和 Bark 参数。

- FILLED：TRADE/SUCCESS。
- REJECTED、UNKNOWN：TRADE/ERROR。
- STOPPED：TRADE/WARNING。
- CLI 异常：SYSTEM/ERROR。
- 没有委托尝试时不生成交易通知。

### 4.2 双通道隔离

数据库写入和 Bark 请求分别捕获异常。数据库失败时继续 Bark；Bark 失败时已写入的 PC 通知保留。两类失败只产生警告日志。

### 4.3 清理任务

沿用现有 stock-clean / cleanup_workflow，按 expires_at 小于等于清理执行时的 UTC 时间删除通知，并在清理结果中增加 notify_msg 删除数量和通知清理截止时间。现有空间回收逻辑保持不变。

## 5. Web 查询接口

### 5.1 请求

    GET /api/notifications?afterId={id}&seenId={id}&limit={1..100}

- afterId：可选，后续轮询游标。
- seenId：浏览器本地已读位置，默认 0。
- limit：默认 50，服务端限制为 1 至 100。

### 5.2 响应

    {
      "available": true,
      "items": [],
      "latestId": 128,
      "cursorId": 128,
      "unreadCount": 3
    }

- latestId：当前全部有效通知的最大 ID，用于“全部已读”。
- cursorId：本批实际读取到的最大 ID，用于下一次轮询；即使新增量超过单批上限，也可继续拉取而不跳过数据。
- 首次查询按 ID 倒序返回最新数据；增量查询按 ID 正序返回。
- 只返回 expires_at 尚未到期的通知。
- 数据库不存在、表未迁移或 DuckDB 查询失败时，返回 available=false 和空列表。

## 6. PC 通知中心

- 登录后立即查询，之后每 5 秒轮询。
- 请求未完成时跳过本轮，避免重叠请求。
- 未读为 0 时隐藏角标，1 至 99 显示数字，超过 99 显示 99+。
- 面板支持“全部、交易、系统”筛选，展示级别图标、时间、内容和来源。
- “全部已读”将 latestId 写入 stockTrading.notifications.lastSeen.{userId}。
- 查询失败保留已有通知，仅在面板内显示“通知服务暂不可用”。
- PC 顶栏沿用 Blue Ledger 的紧凑工具栏样式；移动端入口保持隐藏。

## 7. 实施顺序

1. 提升 SQLite 结构版本并创建 notify_msg 与索引。
2. 增加通知写入方法和到期清理规则。
3. 将现有 Bark 调用改为统一消息双通道发布。
4. 增加 Java 只读 Repository、Controller 和接口模型。
5. 增加 PC 通知服务、轮询组件和顶栏入口。
6. 生成静态 Demo，完成源码差异、UTF-8 和标记检查。

## 8. 验收标准

- 新库和版本 5 数据库可无损升级到版本 6。
- 相同交易终态重试不重复写入，系统异常可重复记录。
- Bark 与数据库通知任一失败不影响另一通道及原业务结果。
- 清理仅删除到期通知，系统保留 30 天，交易保留 183 天。
- 首次加载、5 秒增量轮询、批量游标续传、99+、全部已读和新通知再出现符合预期。
- 过期通知、数据库缺失和通知表缺失不导致接口或 PC 顶栏异常。
- 本次仅执行源码、差异、UTF-8 和标记检查；不执行编译、测试、启动、浏览器或真实交易验证。

## 9. 变更清单

### Trading

- src/stock_trading/state/store.py
- src/stock_trading/utils/notifications.py

### Web 后端

- stock-server/src/main/java/com/stock/console/notification/NotificationController.java
- stock-server/src/main/java/com/stock/console/notification/NotificationFeed.java
- stock-server/src/main/java/com/stock/console/notification/NotificationItem.java
- stock-server/src/main/java/com/stock/console/notification/NotificationRepository.java
- stock-server/src/test/java/com/stock/console/notification/NotificationRepositoryTest.java

### Web 前端

- stock-client/src/desktop/services/notifications.ts
- stock-client/src/desktop/components/NotificationCenter.tsx
- stock-client/src/desktop/components/NotificationCenter.module.css
- stock-client/src/desktop/layouts/DesktopLayout.tsx
<!-- AI_GENERATE_END -- -->
