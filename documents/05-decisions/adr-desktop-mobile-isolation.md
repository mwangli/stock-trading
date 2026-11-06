---
id: adr-platform-isolation
title: ADR：PC 与移动端完全隔离
sidebar_position: 30
owner: decisions
type: decision
status: ACCEPTED
last_verified: 2026-11-06
slug: /decisions/platform-isolation/
source_paths:
  - stock-client/src/App.tsx
  - stock-client/src/desktop
  - stock-client/src/mobile
---

# ADR：PC 与移动端完全隔离

## 问题

当 PC 与移动端复用同一页面 DOM 和大范围响应式 CSS 时，PC 菜单、表格、布局和组件改造会通过媒体查询影响移动端，导致固定四菜单和高频流程被破坏。

## 决策

PC 与移动端使用独立应用入口、路由、布局、页面组件和页面级样式。平台只共享认证、主题、请求、国际化和无布局状态。移动端菜单固定为收益总览、交易记录、Agent 对话和系统日志。

## 约束

- `desktop` 不导入 `mobile` 页面，`mobile` 不导入 `desktop` 页面。
- PC 专属路由在移动端必须显式重定向。
- 文档工作台属于 PC 专属能力。
- 不用媒体查询把同一完整 PC 表格压缩成移动布局。
- 平台边界切换时重新加载，避免 CSS 与交互状态残留。

## 后果

代码量会增加，但两端改造的影响范围清晰。共享逻辑只有在确认不含布局和交互假设时才允许下沉，避免重新形成隐式耦合。
