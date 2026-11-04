---
id: operations-deployment
title: 部署、发布与数据挂载
sidebar_position: 20
owner: operations
type: runbook
status: SOURCE_VERIFIED
last_verified: 2026-11-04
slug: /operations/deployment/
source_paths:
  - docker-compose.yml
  - .github/workflows/ci-cd.yml
  - stock-client/Dockerfile
  - stock-server/Dockerfile
---

# 部署、发布与数据挂载

## 发布触发

GitHub Actions 在推送 `stock-web`、手工触发或推送 `vX.Y.Z` 标签时运行。`prepare` 校验发布输入，Backend 与 Frontend 随后独立构建并推送镜像；部署阶段只把 Compose 文件临时上传到 `/tmp/stock-web-deploy`，由 GitHub `production` Environment 的 Variables 和 Secrets 注入运行配置，再依次更新后端、前端容器并等待健康检查。部署使用 `--no-deps --no-build`，失败时恢复上一组镜像，流程结束后删除临时 Compose 目录。

## 数据挂载

| 宿主机来源 | 容器目标 | 模式 | 用途 |
|---|---|---|---|
| `/root/stock-trading/data` | `/data/stock-trading` | 只读 | Trading 的 SQLite、Parquet 与模型制品 |

配置正确不代表挂载正确。生产核对应同时检查容器环境变量和 `docker inspect` 中的实际宿主机源路径，防止容器读取到旧数据目录。

## 文档制品

文档有两条消费路径：Frontend 构建时把根 `documents/` 编入静态资源；Backend 镜像构建时把同一目录复制到 `/app/documents`。服务器不再保存独立文档目录，也不通过运行时卷覆盖镜像文档。

## 验证层级

1. 源码与配置检查：只能证明声明一致。
2. 镜像构建成功：只能证明制品可生成。
3. 容器健康：只能证明进程入口可用。
4. 挂载与接口检查：证明目标数据确实可读。
5. 页面与业务终态：需要实际浏览器、日志、订单和收益事实。

文档中的静态描述不代替发布证据；每次发布都应以对应 Actions 任务、镜像 SHA、容器健康状态和生产接口结果为准。
