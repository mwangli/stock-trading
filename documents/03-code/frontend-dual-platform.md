---
id: code-frontend-platforms
title: PC 与移动端独立前端
sidebar_position: 20
owner: code
type: implementation
status: SOURCE_VERIFIED
last_verified: 2026-11-06
slug: /code/frontend-dual-platform/
source_paths:
  - stock-client/src/App.tsx
  - stock-client/src/app/usePlatform.ts
  - stock-client/src/desktop/DesktopApp.tsx
  - stock-client/src/mobile/MobileApp.tsx
  - stock-client/scripts/check-platform-boundaries.mjs
---

# PC 与移动端独立前端

## 设计目标

PC 和移动端不是同一页面 DOM 的媒体查询重排，而是两套独立组件树。这样 PC 的完整菜单、表格和工作台改造不会改变移动端固定的四个高频入口。

## 目录边界

```text
src/
├── desktop/     # PC 路由、布局、页面、组件和样式
├── mobile/      # 移动端路由、布局、页面、组件和样式
├── app/         # 平台识别
├── auth/        # 共享认证
├── theme/       # 共享语义 Token 与主题
├── utils/       # 请求、时间和健康检查
└── store/       # 可共享的无布局状态
```

`App.tsx` 只负责选择并懒加载平台应用。跨越 `991px` 平台边界时，平台识别逻辑重载当前地址，避免上一平台 CSS 和组件状态残留。

## 路由边界

PC 端包含收益、行情、股票资料、历史委托、交易记录、模型、Agent、日志和文档等完整页面。移动端只包含：

1. `/dashboard` 收益总览
2. `/tradeRecords` 交易记录
3. `/agentConversation` Agent 对话
4. `/logs` 系统日志

移动端对 `/docs/*` 和其他 PC 专属地址显式重定向到 `/dashboard`，且不导入文档工作台代码。

## 样式边界

- PC 只加载 `desktop/styles/app.css` 与 PC 页面样式。
- 移动端只加载 `mobile/styles/app.css`、`layout.css` 与移动页面样式。
- 页面级 DOM 和 CSS 不跨端复用。
- 共享内容限制为 Token、认证、请求和真正无布局的基础能力。

## 静态边界检查

`check-platform-boundaries.mjs` 用于检查跨平台导入和移动端菜单数量。它只能证明源码引用边界，不等同于编译或浏览器适配通过。
