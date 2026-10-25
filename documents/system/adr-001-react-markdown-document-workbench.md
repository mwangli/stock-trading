---
id: adr-001-react-markdown-document-workbench
title: ADR-001：采用内置 Markdown 文档工作台
sidebar_position: 3
owner: system
type: decision
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
source_paths:
  - AGENTS.md
  - stock-frontend/src/pages/documentation/index.tsx
  - stock-frontend/src/pages/documentation/documentRegistry.ts
  - stock-frontend/src/pages/documentation/MarkdownDocument.tsx
  - stock-frontend/scripts/validate-docs.mjs
---

<DocMetadata />

# ADR-001：采用内置 Markdown 文档工作台

## 背景

项目文档规模较小，正文统一保存在 `documents/`。原 Docusaurus 方案虽然提供导航、搜索和 Mermaid，但开发时需要额外进程和独立路由、主题、构建链，增加了前端依赖与维护边界。

## 决策

使用现有 React、Vite、React Router 和 `react-markdown` 实现只读文档工作台。Vite 在构建时直接读取 `documents/**/*.md`，前端统一提供目录树、中文正文搜索、Front Matter 元数据、源码链接、代码高亮、Mermaid、页内目录和相邻文档导航。

文档工作台使用现有登录态、主题、布局和 `/docs/*` 路由，不提供在线编辑、保存接口或独立运行进程。Markdown 继续通过 IDEA、Git 和代码评审维护。

## 后果

- 交易页面和文档预览只使用一个 Vite 开发进程、一个 React 路由树和一个构建制品。
- `documents/` 继续作为 Markdown 正文唯一事实源，前端组件不复制文档内容。
- Front Matter、源码入口和文档链接继续由静态校验脚本检查。
- 目录、搜索和 Markdown 扩展由项目自行维护，但可以完全对齐交易终端的视觉和交互。
- 浏览器内编辑、多人协作和版本冲突处理不属于当前能力范围。
