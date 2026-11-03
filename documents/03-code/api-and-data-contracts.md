---
id: code-api-contracts
title: API 与数据契约
sidebar_position: 30
owner: code
type: reference
status: SOURCE_VERIFIED
last_verified: 2026-11-03
slug: /code/api-contracts/
source_paths:
  - stock-server/src/main/java/com/stock/console
  - stock-client/src/utils/request.ts
---

# API 与数据契约

## 统一约定

Frontend 请求工具以 `/api` 为基础路径，Backend Controller 返回统一 `ApiResponse`。认证依赖 Servlet Session；未登录请求由拦截器拒绝，前端受保护路由重定向到 `/login` 并保留原地址。

## 接口目录

| 领域 | 方法与路径 | 说明 |
|---|---|---|
| 认证 | `POST /api/auth/login` | 登录并建立会话 |
| 认证 | `GET /api/auth/me` | 当前用户 |
| 认证 | `POST /api/auth/logout` | 退出 |
| 总览 | `GET /api/dashboard/overview` | 数据源、账户、持仓、绩效、收益排名 |
| 行情 | `GET /api/market/status` | 行情数据可用性 |
| 行情 | `GET /api/market/instruments` | 股票搜索 |
| 行情 | `GET /api/market/active-instruments` | 活跃股票分页 |
| 行情 | `GET /api/market/bars` | 日线或 5 分钟线 |
| 行情 | `GET /api/market/statistics` | 最新交易日统计 |
| 行情 | `GET /api/market/daily-ranking` | 涨幅排名 |
| 交易 | `GET /api/trading/orders` | 委托与本地尝试 |
| 交易 | `GET /api/trading/fills` | 成交摘要 |
| 交易 | `GET /api/trading/trades` | 已实现交易 |
| 交易 | `GET /api/trading/positions` | 推导持仓 |
| 模型 | `GET /api/models/overview` | 模型综合信息 |
| 模型 | `GET /api/models/predictions` | 预测列表 |
| 模型 | `GET /api/models/evaluations` | 评估列表 |
| 运维 | `GET /api/operations/status` | 数据与日志状态 |
| 运维 | `GET /api/operations/logs` | 分页读取日志 |
| 运维 | `GET /api/operations/logs/stream` | SSE 日志流 |
| 文档 | `GET /api/documents` | 文档列表 |
| 文档 | `GET /api/documents/content` | 文档内容 |
| 文档 | `GET /api/documents/search` | 全文检索 |
| Agent | `POST /api/agent/sessions` | 创建会话 |
| Agent | `GET /api/agent/sessions` | 会话列表 |
| Agent | `GET /api/agent/sessions/{id}/history` | 消息历史 |
| Agent | `POST /api/agent/chat` | 只读问答 |

## 契约维护

接口字段来自 SQLite 或 Parquet 时，必须保留源字段语义和时间信息。前端类型、后端 SQL 别名和页面列名应同步修改；禁止只在 UI 层猜测字段含义。分页上限、股票代码校验和 Agent 问题长度限制由后端负责最终约束。
