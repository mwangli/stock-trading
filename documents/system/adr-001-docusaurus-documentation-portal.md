---
id: adr-001-docusaurus-documentation-portal
title: ADR-001：采用 Docusaurus 文档门户
sidebar_position: 1
owner: system
type: decision
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
source_paths:
  - stock-frontend/documentation/docusaurus.config.ts
  - stock-frontend/documentation/sidebars.ts
  - AGENTS.md
---

<DocMetadata />

**IDEA 配置入口**：[Docusaurus 配置](pathname://../../stock-frontend/documentation/docusaurus.config.ts) · [文档导航配置](pathname://../../stock-frontend/documentation/sidebars.ts) · [工程规范 `AGENTS.md`](pathname://../../AGENTS.md)

# ADR-001：采用 Docusaurus 文档门户

## 背景

源码旁 README 已混合模块导航、算法细节、专项方案、运行证据和验收步骤，复杂模块持续膨胀，难以搜索和分层阅读。

## 决策

在 `stock-frontend/documentation/` 中维护 Docusaurus 构建组件，在根 `documents/` 中维护 Markdown 正文。详细文档按系统、架构、领域、专题和实现层级统一管理，源码目录继续保留精简 README 和 Javadoc，形成双向入口。

## 后果

- 文档可以统一导航、全文搜索、展示 Mermaid 图和状态元数据。
- 详细正文从源码目录迁出，但通过源码索引和 Javadoc 保持可追溯。
- 文档门户不是 Maven 业务模块，也不成为 Backend、Agent 或 Frontend 的运行依赖。
- 文档构建失败会阻断统一 Web 镜像发布，但不影响已经部署的交易服务运行。
