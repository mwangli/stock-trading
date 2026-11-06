---
id: operations-local
title: 本地开发与源码验证
sidebar_position: 10
owner: operations
type: runbook
status: SOURCE_VERIFIED
last_verified: 2026-11-06
slug: /operations/local-development/
source_paths:
  - stock-trading@pyproject.toml
  - stock-trading@src/stock_trading/config.py
  - stock-trading@src/stock_trading/data_sync.py
  - stock-server/src/main/resources/application.yml
  - stock-client/vite.config.ts
  - AGENTS.md
---

# 本地开发与源码验证

## 分支选择

- 修改行情、模型、交易、状态或发布脚本：使用 `stock-trading`。
- 修改 Java/React 控制台、Agent 或本目录文档：使用 `stock-web`。

两个分支不是可以同时编译的单一目录结构。跨分支功能核对时，应通过 Git 分支内容、数据契约和挂载路径比较，不把另一个分支已删除的目录误判为缺失代码。

## Python 本地路径

Windows 下 `AppConfig` 以 `stock-trading` 源码仓库为项目目录，运行数据位于 `data/`，日志位于 `logs/run.log`。Linux 下项目目录固定为 `~/stock-trading/`。

应用按文件名顺序读取项目根目录 `*.env`，支持 UTF-8 或带 BOM 的 UTF-8。空行和 `#` 注释被忽略，后读取文件中的同名变量覆盖先读取文件和已有进程环境。没有环境文件、文件不可读、配置行无效或券商变量缺失时只记录告警；真正执行券商功能时仍会校验必需变量。

本地可以按职责拆分 `base.env`、`broker.env` 和 `server.env`。凭据文件不得提交到版本库。`stock-sync` 还要求系统可用 `ssh`、`scp`，并从环境读取 `SERVER_IP`、`SERVER_USER`、`SERVER_PASS`。

## 控制台本地路径

Spring Boot 当前默认 `STOCK_TRADING_VAR_DIR=D:/stock-trading4/var`，这是控制台历史默认值。与最新 Python 主线联调时应显式设置为 Python 仓库的 `data` 目录，例如 `D:/stock-trading4/data`，并把 `STOCK_TASK_LOG` 指向实际 `logs/run.log`。

Vite 将 `/api` 和 `/ws` 代理到 `http://localhost:8080`，并允许读取仓库根 `documents/`。文档 Front Matter 必须从文件第一字节开始。

## 本仓库验证约束

默认只进行源码和文件层检查。未经当前请求明确授权，不执行：

- `npm run build`、TypeScript 编译、Lint、前端启动或浏览器验证。
- Maven 编译、打包、测试或 Spring Boot 启动。
- Python 业务命令、真实券商调用、Docker、部署或生产验证。

允许的轻量检查包括源码检索、Front Matter 与链接校验、Markdown 结构检查、分支差异核对和 `git diff --check`。汇报时必须区分源码检查、编译、测试、运行、真实交易与生产验证。
