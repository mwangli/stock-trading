---
id: documentation-governance
title: 文档治理规范
sidebar_position: 1
owner: system
type: governance
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
source_paths:
  - AGENTS.md
  - stock-frontend/documentation/docusaurus.config.ts
  - stock-frontend/package.json
---

<DocMetadata />

**IDEA 配置入口**：[工程规范 `AGENTS.md`](../../AGENTS.md) · [Docusaurus 配置](../../stock-frontend/documentation/docusaurus.config.ts) · [前端依赖与脚本](../../stock-frontend/package.json)

# 文档治理规范

## 权威层级

| 层级 | 内容 | 权威位置 |
|---|---|---|
| L0 系统 | 项目目标、全局闭环、安全原则 | `system` |
| L1 架构 | 模块关系、数据所有权、跨模块流程 | `system` |
| L2 领域 | 领域职责、边界、主要能力 | `backend/*`、`agent/*`、`frontend` |
| L3 专题 | 状态机、算法、跨类流程、设计取舍 | 各领域的 `designs`、`proposals`、`runbooks` |
| L4 实现 | 类、方法、字段和局部不变量 | Java Javadoc、TypeScript 类型与源码 |

## 单一事实来源

- 详细设计正文只保留在 `documents`。
- 源码旁 README 只提供稳定职责和文档入口。
- Javadoc 只描述单类、单方法契约，不复制跨类流程。
- 精确默认值、Cron、状态枚举和计算公式以源码、类型化配置和数据库基线为准。
- 提案和已实现设计使用相同目录体系，通过状态区分，不复制为另一套文档。

## 状态语义

`PROPOSED`、`SOURCE_IMPLEMENTED`、`COMPILED`、`RUNTIME_VERIFIED`、`PRODUCTION_VERIFIED`、`SUPERSEDED` 只表达证据等级。源码存在不能被描述为编译、运行或生产验证通过。

## 修改要求

修改模块职责、跨模块契约、核心流程、状态机或安全边界时，必须同批更新专题文档；修改局部实现时，优先更新 Javadoc 和关联源码索引。

## IDEA 源码与文档双向跳转

### 文档跳转源码

专题 Markdown 在正文中使用相对当前文档的仓库文件链接，不使用本机绝对路径。例如：

```markdown
[打开 RankingLSTMModel.java](../../stock-backend/src/main/java/com/stock/model/algorithm/RankingLSTMModel.java)
```

实际相对层级以文档所在目录为准。IDEA 内置 Markdown 插件支持相对文件链接补全和 `Ctrl+Click` 跳转，因此文档作者可以直接进入源码；Docusaurus 页面仍通过 Front Matter 的 `source_paths` 生成 GitHub 源码入口。

### 源码跳转文档

Java 类级 Javadoc 使用稳定的 Docusaurus 页面 URL：

```java
@see <a href="http://localhost:5173/docs/backend/model/designs/ranking-lstm">Ranking LSTM 设计文档</a>
```

IDEA 的 Javadoc Rendered View 可直接点击外部链接。生产部署后，将示例地址替换为正式同域 `/docs/` 地址；源码中的链接只指向当前权威页面，不复制设计正文。

### 维护规则

1. 每篇专题文档至少保留一个指向核心实现类的相对 Markdown 链接，并维护完整 `source_paths`。
2. 每个被专题文档解释的核心 Java 类，在类级 Javadoc 中保留一个稳定文档 URL。
3. 移动源码或文档时，同批更新 Markdown 相对链接、`source_paths` 和 Javadoc URL。
4. 不使用 `file:///D:/...` 等本机绝对链接，避免团队成员和 CI 环境失效。
