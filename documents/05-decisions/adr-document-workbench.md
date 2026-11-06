---
id: adr-document-workbench
title: ADR：内置 Markdown 文档工作台
sidebar_position: 20
owner: decisions
type: decision
status: ACCEPTED
last_verified: 2026-11-06
slug: /decisions/document-workbench/
source_paths:
  - stock-client/src/desktop/pages/documentation
  - documents
---

# ADR：内置 Markdown 文档工作台

## 背景

项目的业务主线和观察层位于不同 Git 分支，历史设计又散落在提交、阶段报告和旧模块文档中。只恢复旧文件会把已删除实现误认为现行能力，只保留 README 则不足以解释业务策略和技术取舍。

## 决策

在 `stock-web` PC 控制台内维护 Markdown 工作台，并以根 `documents/` 作为统一开发文档源。内容同时覆盖 `stock-trading` Python 主线与 `stock-web` 只读控制台，Front Matter 中用 `branch@path` 标识跨分支源码。

工作台提供目录树、全文搜索、章节导航、源码链接、Mermaid、代码复制和相邻文档导航。入口只放在 PC 顶栏，移动端不加载文档组件树。

## 内容治理

- 当前源码、配置和业务事实优先于历史描述。
- 现行功能写入系统、业务、代码和运维章节。
- 历史方案只保留仍能解释当前决策的结论。
- 与当前实现冲突的类名、接口、数据库、调度和部署拓扑应删除。
- 文档必须说明验证层级，不用源码检查替代编译、运行或生产证据。

## 后果

文档变更会进入前端构建制品，也会改变 Backend 和 Agent 可检索的证据，因此需要像代码一样审查。Front Matter 必须保持在文件开头，不能添加会破坏解析的首行标记。
