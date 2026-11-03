---
id: code-document-workbench
title: 文档工作台实现
sidebar_position: 40
owner: code
type: implementation
status: SOURCE_VERIFIED
last_verified: 2026-11-03
slug: /code/document-workbench/
source_paths:
  - stock-client/src/desktop/pages/documentation/index.tsx
  - stock-client/src/desktop/pages/documentation/documentRegistry.ts
  - stock-client/src/desktop/pages/documentation/MarkdownDocument.tsx
  - stock-client/src/desktop/pages/documentation/MermaidDiagram.tsx
  - stock-server/src/main/java/com/stock/console/documents/DocumentService.java
---

# 文档工作台实现

## PC 入口

文档只在 PC 顶栏右上角提供 `BookOpenText` 图标入口，不进入左侧业务菜单。路由为 `/docs/*`，移动端显式重定向，确保文档工作台不会改变移动端四菜单结构。

## 前端注册机制

`documentRegistry.ts` 使用 Vite `import.meta.glob` 读取根 `documents/**/*.md`。每篇文档必须在文件第一行开始提供 Front Matter，至少包含 `id` 和 `title`，可选字段用于排序、状态、核对日期、路由和源码链接。

```yaml
---
id: unique-id
title: 文档标题
sidebar_position: 10
owner: system
type: architecture
status: SOURCE_VERIFIED
last_verified: 2026-11-03
slug: /system/example/
source_paths:
  - stock-server/src/main/java/example.java
---
```

## 交互能力

- 左侧按目录构建树，并支持标题与正文搜索。
- 左右侧栏可拖动调整宽度，结果保存在 `localStorage`。
- 右侧章节目录从 Markdown 二至四级标题生成，并跟随正文滚动。
- URL Hash 支持章节直达。
- Markdown 支持 GFM 表格、代码高亮、复制、内部文档链接和 Mermaid。
- 文档底部提供上一篇与下一篇导航。

## Backend 与 Agent

Backend 仍会递归索引 `STOCK_DOCUMENT_ROOTS` 中的 Markdown，向 API 和 Agent 提供列表、内容和简单全文搜索。前端工作台使用构建期文档以获得完整树和无请求导航；两者共享同一个根 `documents/`，但用途不同。

## 维护约束

- Front Matter 必须位于文件字节开头，不能在它之前插入注释标记。
- 删除或改名文档时必须检查内部相对链接。
- 设计说明必须与当前源码路径匹配；历史内容应明确标记，不得伪装成现行能力。
- Frontend Docker 构建必须复制根 `documents/`，否则 `import.meta.glob` 无法发现文件。
