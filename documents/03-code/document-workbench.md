---
id: code-document-workbench
title: 文档工作台实现
sidebar_position: 40
owner: code
type: implementation
status: SOURCE_VERIFIED
last_verified: 2026-11-06
slug: /code/document-workbench/
source_paths:
  - stock-client/src/desktop/pages/documentation/index.tsx
  - stock-client/src/desktop/pages/documentation/documentRegistry.ts
  - stock-client/src/desktop/pages/documentation/MarkdownDocument.tsx
  - stock-client/src/desktop/pages/documentation/MermaidDiagram.tsx
  - stock-server/src/main/java/com/stock/console/documents/DocumentService.java
---

# 文档工作台实现

## 入口与加载

文档只在 PC 顶栏提供入口，路由为 `/docs/*`；移动端显式重定向，不加载文档组件树。前端通过 `import.meta.glob` 在构建期读取根 `documents/**/*.md`，因此无需为文档浏览单独请求 Backend。

每篇文档必须从第一字节开始提供 Front Matter，至少包含 `id` 和 `title`。常用字段如下：

```yaml
---
id: unique-id
title: 文档标题
sidebar_position: 10
owner: system
type: architecture
status: SOURCE_VERIFIED
last_verified: 2026-11-06
slug: /system/example/
source_paths:
  - stock-trading@src/stock_trading/example.py
  - stock-server/src/main/java/example.java
---
```

`source_paths` 默认指向 `stock-web` 分支；使用 `branch@path` 可以链接其他分支，例如 `stock-trading@src/stock_trading/config.py`。

## 交互能力

- 左侧按目录和 `sidebar_position` 构建树，支持标题与正文搜索。
- 左右侧栏可拖动调整宽度，并保存到 `localStorage`。
- 右侧目录从 Markdown 二至四级标题生成，并跟随正文滚动。
- URL Hash 支持章节直达。
- Markdown 支持 GFM 表格、代码高亮、复制、内部文档链接和 Mermaid。
- 文档底部提供上一篇与下一篇导航。

## Backend 消费

Backend 递归索引 `STOCK_DOCUMENT_ROOTS` 中的 Markdown，提供列表、内容和简单全文搜索，并为运营 Agent 提供文档证据。前端工作台和 Backend 使用同一个 `documents/`，但前者是构建期导航，后者是运行期只读索引。

## 维护约束

- Front Matter 前不能插入 AI 标记、空行或其他注释。
- 删除或改名文档时必须检查相对链接和首页导航。
- 当前源码与数据契约优先于历史描述。
- Python 和 Web 源码必须标明分支，避免源码链接落到错误分支。
- Frontend Docker 构建必须复制根 `documents/`，否则 Vite 无法发现文档。
