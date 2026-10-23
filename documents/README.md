<!-- AI_GENERATE_START--------------- -->
# 项目文档

本目录维护 `stock-trading4` 当前有效的架构、领域和运维说明。项目只有 `stock-backend`、`stock-agent`、`stock-frontend` 三个 Maven 模块；Backend 内部领域文档按业务名称分目录，不代表独立 Maven 模块。

## 阅读入口

| 目标 | 文档 |
|---|---|
| 了解系统与存储边界 | [系统概览](00-系统架构/系统概览.md) |
| 了解三模块和内部 API | [模块依赖与边界](00-系统架构/模块依赖与边界.md) |
| 了解数据到交易的完整过程 | [核心业务链路](00-系统架构/核心业务链路.md) |
| 了解六服务部署与发布 | [部署与运行](00-系统架构/部署与运行.md) |

## Maven 模块文档

| Maven 模块 | 业务定位 | 文档 |
|---|---|---|
| `stock-backend` | 核心业务、数据、模型、策略、交易和 Quartz | [模块说明](stock-backend/启动配置与持久化.md) |
| `stock-agent` | 研究、诊断、RAG、Memory 和独立交易建议 | [模块说明](stock-agent/模块说明.md)、[运行机制与工具](stock-agent/运行机制与工具.md)、[RAG 与 Memory](stock-agent/RAG与Memory.md)、[交易建议边界](stock-agent/交易建议边界.md) |
| `stock-frontend` | React 管理与交易操作界面 | [前端功能与交互](stock-frontend/前端功能与交互.md) |

## Backend 领域文档

| Backend 领域 | 文档 |
|---|---|
| 公共契约 | [说明](stock-base/模块说明.md) |
| 数据采集与质量 | [领域说明](stock-data/模块说明.md)、[数据采集与质量](stock-data/数据采集与质量.md) |
| 模型 | [领域说明](stock-model/模块说明.md)、[训练推理与模型生命周期](stock-model/训练推理与模型生命周期.md)、[模型制品](stock-model/模型制品.md) |
| 策略 | [领域说明](stock-strategy/模块说明.md)、[选股信号与回测](stock-strategy/选股信号与回测.md) |
| 交易 | [领域说明](stock-trading/模块说明.md)、[风控与交易执行](stock-trading/风控与交易执行.md)、[券商接入与账户同步](stock-trading/券商接入与账户同步.md) |
| 任务编排 | [任务调度与业务编排](stock-job/任务调度与业务编排.md) |

## 文档维护原则

1. 文档只描述当前实现，不保留已经删除的目录、模块、接口或兼容方案。
2. Backend 领域名称可以沿用 `stock-data`、`stock-model` 等业务称呼，但必须明确其不是 Maven 模块。
3. 模块职责、数据所有权或 API 边界变化时，同步修改架构和对应领域文档。
4. 文档不得记录真实密码、Token、账户、生产数据或临时排查输出。
5. 源码静态检查、编译、测试、运行和生产验证是不同证据层级，不得混淆。
<!-- AI_GENERATE_END--------------- -->
