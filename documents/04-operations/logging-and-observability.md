---
id: operations-observability
title: 日志与可观测性
sidebar_position: 30
owner: operations
type: runbook
status: SOURCE_VERIFIED
last_verified: 2026-11-03
slug: /operations/observability/
source_paths:
  - stock-server/src/main/java/com/stock/console/operations/OperationsController.java
  - stock-server/src/main/java/com/stock/console/operations/OperationsService.java
  - stock-client/src/desktop/pages/logs/index.tsx
---

# 日志与可观测性

## 观测对象

控制台同时观测 Backend 自身是否在线、SQLite 与 Parquet 数据是否可用、任务日志是否可读，以及 Agent Provider 是否配置。页面状态不应把这些信号合并成单一“系统正常”。

## 日志接口

- `/api/operations/logs`：按参数读取当前日志内容，适合首次加载和手动刷新。
- `/api/operations/logs/stream`：以 Server-Sent Events 持续推送新增内容。
- PC 与移动端都有独立日志页面实现，但共享同一后端接口。

## 故障判断顺序

1. `/api/system/health` 是否可达。
2. `/api/operations/status` 中数据目录、数据库、行情与日志路径是否可用。
3. 容器实际挂载是否指向预期宿主机目录。
4. 具体业务接口是否返回 `available=false`、空集合或错误原因。
5. 日志时间与数据文件更新时间是否一致。

## 证据纪律

没有日志不代表任务未运行，可能是挂载路径错误；页面为空不代表业务失败，可能是真实没有委托或交易；容器健康不代表 SQLite 与 Parquet 是最新数据。问题定位必须同时核对配置、实际挂载、文件更新时间、接口响应和日志。
