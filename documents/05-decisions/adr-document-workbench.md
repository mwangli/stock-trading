---
id: adr-document-workbench
title: ADR：内置 Markdown 文档工作台
sidebar_position: 20
owner: decisions
type: decision
status: ACCEPTED
last_verified: 2026-11-03
slug: /decisions/document-workbench/
source_paths:
  - stock-client/src/desktop/pages/documentation
  - documents
---

# ADR：内置 Markdown 文档工作台

## 背景

历史设计信息分散在提交、阶段报告和多个模块文档中。仓库重构后，单纯恢复旧文件会把已删除模块误认为现行能力；只保留少量 README 又无法解释业务和代码。

## 决策

在 PC 控制台中恢复内置 Markdown 工作台，并重建根 `documents/` 作为统一文档源。工作台提供左侧目录树、全文搜索、右侧章节导航、源码链接、Mermaid 和相邻文档导航。

入口只放在 PC 顶栏右上角，不加入业务侧边菜单；移动端不加载文档组件树。

## 内容治理

- 当前源码和数据契约优先于历史描述。
- 历史设计中仍有效的业务原则重新编排到现行主题中。
- 与现有代码冲突的类名、接口、数据库和部署拓扑删除或明确标为历史。
- Front Matter 提供稳定 ID、排序、状态、核对日期与源码路径。

## 后果

Frontend 和 Backend 都消费同一文档目录：Frontend 用于可视化浏览，Backend 用于 API 与 Agent 检索。文档变更会影响前端构建制品，也可能改变 Agent 的证据内容，因此应像代码一样审查。
