---
id: operations-deployment
title: 部署、发布与数据挂载
sidebar_position: 20
owner: operations
type: runbook
status: SOURCE_VERIFIED
last_verified: 2026-11-03
slug: /operations/deployment/
source_paths:
  - docker-compose.yml
  - .github/workflows/ci-cd.yml
  - stock-client/Dockerfile
  - stock-server/Dockerfile
---

# 部署、发布与数据挂载

## 发布触发

GitHub Actions 在推送 `stock-web`、手工触发或推送 `vX.Y.Z` 标签时运行。`prepare` 校验发布输入，Backend 与 Frontend 随后独立构建并推送镜像；部署阶段把 Compose 文件和根 `documents/` 上传到服务器，再依次更新后端、前端容器并等待健康检查。部署使用 `--no-deps --no-build`，失败时恢复上一组镜像。

## 数据挂载

| 宿主机来源 | 容器目标 | 模式 | 用途 |
|---|---|---|---|
| `/data/stock-trading` | `/data/stock-trading` | 只读 | SQLite 与 Parquet |
| `${STOCK_TRADING_LOG_DIR}` | `/var/log/stock-trading` | 只读 | 运行日志 |
| `${STOCK_TRADING_DOCUMENTS_DIR}` | `/data/stock-trading-documents` | 只读 | 外部文档 |
| `./documents` | `/app/documents` | 只读 | 当前仓库系统文档 |

配置正确不代表挂载正确。生产核对应同时检查容器环境变量和 `docker inspect` 中的实际宿主机源路径，防止容器读取到旧数据目录。

## 文档制品

文档有两条消费路径：Frontend 构建时把根 `documents/` 编入静态资源；Backend 运行时从只读卷索引 Markdown。发布时必须保证构建上下文包含文档，并保证服务器部署目录或外部文档目录存在对应文件。

## 验证层级

1. 源码与配置检查：只能证明声明一致。
2. 镜像构建成功：只能证明制品可生成。
3. 容器健康：只能证明进程入口可用。
4. 挂载与接口检查：证明目标数据确实可读。
5. 页面与业务终态：需要实际浏览器、日志、订单和收益事实。

文档中的静态描述不代替发布证据；每次发布都应以对应 Actions 任务、镜像 SHA、容器健康状态和生产接口结果为准。
