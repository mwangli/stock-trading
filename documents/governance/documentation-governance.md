---
id: documentation-governance
title: 文档治理规范
sidebar_position: 2
owner: system
type: governance
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
slug: /governance/
source_paths:
  - AGENTS.md
  - stock-client/src/pages/documentation/documentRegistry.ts
  - stock-client/src/pages/documentation/MarkdownDocument.tsx
  - stock-client/scripts/validate-docs.mjs
  - stock-client/package.json
---

<DocMetadata />

# 文档治理规范

## 权威层级

| 层级 | 内容 | 权威位置 |
|---|---|---|
| L0 系统 | 项目目标、全局闭环、安全原则 | `system` |
| L1 模块 | Maven 模块关系、数据所有权、职责与依赖边界 | `modules` |
| L2 专题 | 状态机、算法、跨模块流程和设计取舍 | `topics` |
| L3 运维 | 部署、模型制品、运行检查和故障处理 | `operations` |
| L3 决策 | 已接受的重要架构选择及其后果 | `decisions` |
| L3 治理 | 文档状态、元数据、维护和验证规则 | `governance` |
| L4 实现 | 类、方法、字段和局部不变量 | Java Javadoc、TypeScript 类型与源码 |

## 单一事实来源

- 详细设计正文只保留在 `documents`。
- `system` 只维护系统入口、全局架构和端到端闭环。
- `modules` 按实际 Maven 模块和 Frontend 维护职责、边界与源码入口。
- `topics` 维护跨类或跨模块的算法、状态机和专项设计，不模拟 Java package 结构。
- `operations` 维护真实部署、模型制品、运行检查或排障手册，不复制系统架构正文。
- `governance` 与 `decisions` 分别维护治理规则和 ADR。
- 仓库只保留根目录项目级 `README.md` 作为统一入口，不维护模块级或源码包级 README。
- 目录入口文档使用与内容一致的中文语义文件名，不使用 `index.md`；稳定访问路径统一由 Front Matter 的 `slug` 声明。
- Javadoc 只描述单类、单方法契约，不复制跨类流程。
- 精确默认值、Cron、状态枚举和计算公式以源码、类型化配置和数据库基线为准。
- 提案和已实现设计使用相同目录体系，通过状态区分，不复制为另一套文档。

## 状态语义

`PROPOSED`、`SOURCE_IMPLEMENTED`、`COMPILED`、`RUNTIME_VERIFIED`、`PRODUCTION_VERIFIED`、`SUPERSEDED` 只表达证据等级。源码存在不能被描述为编译、运行或生产验证通过。

## 修改要求

修改模块职责、跨模块契约、核心流程、状态机或安全边界时，必须同批更新专题文档；修改局部实现时，优先更新 Javadoc 和关联源码索引。

## IDEA 源码与文档双向跳转

### 文档跳转源码

源码入口统一维护在 Front Matter 的 `source_paths` 中，不在 Markdown 正文重复维护“IDEA 源码入口”。例如：

```yaml
source_paths:
  - stock-backend/stock-model/src/main/java/com/stock/model/algorithm/RankingLSTMModel.java
```

路径使用仓库根目录相对路径。前端 `MarkdownDocument` 将 `source_paths` 统一渲染为页面顶部的“关联源码”，因此源码入口只维护一份；普通正文中的业务引用仍可按需使用 Markdown 相对链接。

### 源码跳转文档

Java 类级 Javadoc 使用稳定的前端文档路由：

```java
@see <a href="http://localhost:5173/docs/topics/modelLifecycle/ranking-lstm">Ranking LSTM 设计文档</a>
```

IDEA 的 Javadoc Rendered View 可直接点击外部链接。生产部署后，将示例地址替换为正式同域 `/docs/` 地址；源码中的链接只指向当前权威页面，不复制设计正文。

### 维护规则

1. 每篇专题文档至少保留一个指向核心实现类的相对 Markdown 链接，并维护完整 `source_paths`。
2. 每个被专题文档解释的核心 Java 类，在类级 Javadoc 中保留一个稳定文档 URL。
3. 移动源码或文档时，同批更新 Markdown 相对链接、`source_paths` 和 Javadoc URL。
4. 不使用 `file:///D:/...` 等本机绝对链接，避免团队成员和 CI 环境失效。
5. 不使用 `pathname://` 等私有协议；这类链接会被 IDEA 交给操作系统处理，无法完成项目内源码跳转。
