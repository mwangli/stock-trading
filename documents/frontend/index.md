---
id: frontend-overview
title: Frontend 前端设计
sidebar_position: 1
owner: frontend
type: domain
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
slug: /frontend/
source_paths:
  - stock-frontend/src/App.tsx
  - stock-frontend/src/layouts/DashboardLayout.tsx
  - stock-frontend/src/pages/market/index.tsx
  - stock-frontend/src/pages/orderRecords/index.tsx
  - stock-frontend/src/pages/modelOperations/SentimentModelStatusPanel.tsx
---

<DocMetadata />

**IDEA 源码入口**：[路由入口 `App.tsx`](../../stock-frontend/src/App.tsx) · [主布局 `DashboardLayout.tsx`](../../stock-frontend/src/layouts/DashboardLayout.tsx) · [文档跳转配置 `documentation.ts`](../../stock-frontend/src/utils/documentation.ts)

# AI-AGENT 智能股票交易系统前端

## 模块定位

`stock-frontend` 是独立 React、TypeScript 和 Vite Maven 模块，负责展示市场事实、模型运行状态、策略分析、Agent 交易计划、任务调度和交易审计信息。生产静态资源由 Nginx 独立托管，不打入 Backend JAR。

前端只负责交互、展示和请求编排，不直接访问数据库、内部事实 API 或券商接口，也不替代 Backend 的权限、风险和交易校验。

## 主要页面

| 路由 | 功能 |
|---|---|
| `/login` | 系统用户登录 |
| `/dashboard` | 账户资产和收益统计总览 |
| `/market` | 股票行情、市场统计、股票详情和 K 线走势 |
| `/orderRecords` | 通过页签查看券商历史订单和 FIFO 聚合交易记录 |
| `/logs` | Backend 实时日志 |
| `/modelOperations` | 模型训练、版本、预测、评估和新闻模型运行状态 |
| `/agentConversation` | 独立 Agent 对话、研究和诊断 |
| `/adaptiveStrategy` | 策略模式、候选、运行证据和紧急停止 |
| `/jobs` | Quartz 任务调度及执行历史 |

系统文档不作为交易终端内部路由或 iframe 页面加载。全局页头提供“文档查阅”按钮，通过同域 `/docs/` 在新窗口打开独立文档站点。

路由、页面目录和 API 路径统一使用 lowerCamelCase。调整既有路径时同步修改调用方，只保留当前路径，不新增旧路径兼容映射。

## 接口分流

| 请求 | 开发代理 | 生产 Nginx |
|---|---|---|
| `/api/agent/**` | `localhost:8081` | `stock-agent:8081` |
| 其他 `/api/**` | `localhost:8080` | `stock-backend:8080` |
| `/ws/**` | `localhost:8080` | `stock-backend:8080` |
| `/api/internal/**` | 禁止调用 | 返回 `404` |

Backend 与 Agent 当前共享服务端 Redis Session，浏览器只维护登录 Cookie。`X-Internal-Token` 仅用于 Agent 与 Backend 的服务间调用，不能写入前端配置、发送到浏览器或出现在客户端日志中。

## 技术与状态管理

- React Router 负责页面路由和受保护入口。
- Zustand 管理跨页面共享状态，局部交互优先保留在页面或组件内部。
- Axios 封装 HTTP 请求、Session 失效和统一错误转换。
- Ant Design 提供表格、表单、弹窗、状态标签和反馈组件。
- TailwindCSS 承担布局和视觉样式，不新增无必要的传统 CSS 或行内样式。
- 接口读取 `ResponseDTO<T>`，前端类型必须与后端 DTO 对齐，禁止使用 `any` 掩盖契约变化。

## 页面状态设计

每个数据页面必须分别表达以下状态：

| 状态 | 表达要求 |
|---|---|
| 加载中 | 保持布局稳定，显示明确加载状态，不闪烁旧数据 |
| 空数据 | 说明当前查询无结果，不伪装成接口错误 |
| 请求失败 | 就近显示失败原因并保留可重试入口 |
| 操作禁用 | 保留操作位置并置灰，不能通过隐藏造成表格布局跳变 |
| 降级 | 区分 Agent 核心、Provider、Embedding 和 Backend 事实源状态 |

Mock 数据只能作为明确标注的展示兜底，不能伪装成真实行情、模型结果、持仓、订单或交易事实。前端按钮状态只改善交互，不能替代服务端安全校验。

## 布局与交互约束

- 页面级纵向滚动统一由布局层 `.app-content` 承担。
- 普通表格不设置固定 `scroll.y`、`100vh` 或视口计算高度，只在超宽时使用横向滚动。
- Modal、Drawer 和日志面板等限定高度区域可以内部纵向滚动，但必须避免父子容器重复承载同一滚动内容。
- 密集型表格的操作列使用稳定列宽和轻量文字操作；不可用操作保留位置并禁用。
- 错误、风险和运行状态使用字段文本、`Tag`、统计项、空状态或 Toast 表达，不新增常驻大块告警横幅。
- 图标按钮提供 `Tooltip` 或 `aria-label`，长文本通过省略、换行或横向滚动保持可读。
- 日期范围统一使用 `DatePicker.RangePicker`，显示和接口参数格式为 `YYYY-MM-DD`。

## 认证与安全边界

1. 登录由 Backend 提供，前端不保存账号密码或复制认证逻辑。
2. Session Cookie 由浏览器按服务端策略发送，前端不把内部 Token 当作用户令牌。
3. Agent 页面可以展示 `backendFactsStatus=UNAVAILABLE`，但不得把事实依赖结果改写为模型猜测。
4. 真实交易相关页面只提交用户意图；最终权限、资金、持仓、T+1、幂等和券商校验由 Backend 完成。
5. 日志、错误提示和调试输出不得泄露密码、Cookie、Token、私钥或券商凭据。

## 构建与发布

本地开发：

```bash
npm install
npm run dev
```

Maven `frontend-maven-plugin` 安装固定 Node/npm，并执行 `npm ci` 与生产构建。Docker 多阶段构建将 `dist/` 复制到 Nginx 镜像。Frontend 使用独立标签和工作流发布，不重建 Backend 或 Agent。

生产 Nginx 负责 SPA 回退、API 分流和 `/api/internal/**` 拒绝。前端容器健康只证明静态资源和 Nginx 入口可用，不证明 Backend、Agent、数据库或业务任务正常。

## 主要代码位置

```text
stock-frontend/
├── src/pages/        # 路由页面和页面级状态
├── src/components/   # 可复用业务与展示组件
├── src/layouts/      # 页面框架和统一滚动容器
├── src/store/        # Zustand 状态
├── src/locales/      # 国际化资源
├── src/utils/        # 请求、格式和通用工具
├── nginx.conf        # 生产分流和 SPA 配置
└── vite.config.ts    # 开发代理和构建配置
```

## 文档维护

页面、路由、状态模型、接口分流、认证方式或发布方式变化时，必须在同一变更中更新本页面及对应专题文档。前端模块不再维护独立 README，也不在其他位置复制当前架构说明。
