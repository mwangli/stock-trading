---
id: code-api-contracts
title: Web API 与数据契约
sidebar_position: 30
owner: code
type: reference
status: SOURCE_VERIFIED
last_verified: 2026-11-06
slug: /code/api-contracts/
source_paths:
  - stock-server/src/main/java/com/stock/console
  - stock-client/src/utils/request.ts
---

# Web API 与数据契约

本页只描述 `stock-web` 分支。Frontend 统一以 `/api` 为基础路径，Backend 返回 `ApiResponse`；认证使用 Servlet Session，未登录的受保护请求跳转到 `/login`。

## 只读业务接口

| 领域 | 方法与路径 | 说明 |
|---|---|---|
| 系统 | `GET /api/system/health` | Backend 健康与基本状态 |
| 认证 | `POST /api/auth/login` | 登录并建立会话 |
| 认证 | `GET /api/auth/me` | 当前用户 |
| 认证 | `POST /api/auth/logout` | 退出 |
| 总览 | `GET /api/dashboard/overview` | 数据源、账户、持仓和绩效聚合 |
| 行情 | `GET /api/market/status` | 行情文件可用性 |
| 行情 | `GET /api/market/instruments` | 股票搜索 |
| 行情 | `GET /api/market/active-instruments` | 活跃股票分页 |
| 行情 | `GET /api/market/bars` | 日线或 5 分钟线 |
| 行情 | `GET /api/market/statistics` | 最新交易日统计 |
| 行情 | `GET /api/market/daily-ranking` | 日行情排名 |
| 交易 | `GET /api/trading/status` | 交易事实可用性 |
| 交易 | `GET /api/trading/orders` | 券商委托与本地尝试 |
| 交易 | `GET /api/trading/fills` | 成交摘要 |
| 交易 | `GET /api/trading/trades` | 已实现交易 |
| 交易 | `GET /api/trading/positions` | 从成交事实推导的持仓 |
| 模型 | `GET /api/models/overview` | 模型、预测和真实表现聚合 |
| 模型 | `GET /api/models/predictions` | 完整预测列表 |
| 模型 | `GET /api/models/evaluations` | 模型评估列表 |
| 运维 | `GET /api/operations/status` | 数据与日志状态 |
| 运维 | `GET /api/operations/logs` | 分页读取日志 |
| 运维 | `GET /api/operations/logs/stream` | SSE 日志流 |
| 文档 | `GET /api/documents` | 文档列表 |
| 文档 | `GET /api/documents/content` | 文档内容 |
| 文档 | `GET /api/documents/search` | 简单全文检索 |

## Agent 接口

运营 Agent 提供状态、会话、历史和聊天接口；开发 Agent 提供状态、任务创建、查询、取消和最近任务接口。开发 Agent 是 Web 控制台唯一写能力，但写入目标是配置的 GitHub 仓库，不是交易数据。

| 方法与路径 | 说明 |
|---|---|
| `GET /api/agent/status` | Provider 与 Agent 状态 |
| `POST /api/agent/sessions` | 创建会话 |
| `GET /api/agent/sessions` | 会话列表 |
| `GET /api/agent/sessions/{id}/history` | 消息历史 |
| `POST /api/agent/chat` | 只读问答 |
| `GET /api/agent/development/status` | 自动开发配置状态 |
| `POST /api/agent/development/tasks` | 创建开发任务 |
| `GET /api/agent/development/tasks/{id}` | 查询任务 |
| `POST /api/agent/development/tasks/{id}/cancel` | 取消任务 |
| `GET /api/agent/development/tasks/recent` | 最近任务 |

## 契约维护

接口字段来自 SQLite 或 Parquet 时，后端 SQL 别名、前端类型、页面列名和本目录文档应一起修改。控制台不迁移 Python 数据库；上游 Schema 变化必须先在 `stock-trading` 分支明确，再调整只读查询。当前预测接口读取 Schema v4 的 `prediction_rank`，不是历史 `prediction` 表。
