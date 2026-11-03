---
id: operations-local
title: 本地配置与源码验证
sidebar_position: 10
owner: operations
type: runbook
status: SOURCE_VERIFIED
last_verified: 2026-11-03
slug: /operations/local-development/
source_paths:
  - stock-server/src/main/resources/application.yml
  - stock-client/vite.config.ts
  - stock-client/package.json
  - AGENTS.md
---

# 本地配置与源码验证

## 默认路径

本地 Backend 默认读取 `D:/stock-trading4/data`，其中 `state/trading.db` 是交易事实库，`market/` 是 Parquet 根目录。日志默认位于数据目录下 `logs/run.log`。文档根默认依次尝试 `documents` 和 `../documents`。

## Frontend 开发代理

Vite 把 `/api` 代理到 `http://localhost:8080`，把 `/ws` 代理到同一 Backend。仓库根被加入 Vite 文件访问允许列表，使 PC 文档工作台可以在开发模式读取根 `documents/`。

## 静态检查边界

本项目默认只做源码与文件层检查，未经明确授权不执行：

- `npm run build`、TypeScript 编译、Lint、前端启动或浏览器验证。
- Maven 编译、打包、测试或 Spring Boot 启动。
- Docker 构建、Compose 启动、部署或生产健康检查。

可以执行的轻量检查包括路径检索、Front Matter 与链接检查、平台导入边界检查、`git diff --check` 和源码人工核对。汇报时必须把“源码检查通过”与“编译/运行通过”分开。

## 敏感配置

密码哈希和 Provider Key 通过环境变量提供，不应写入文档、日志或版本库。文档示例只列变量名和用途，不保存真实值。
