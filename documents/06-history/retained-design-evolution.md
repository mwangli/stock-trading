---
id: history-design-evolution
title: 历史设计演进与保留结论
sidebar_position: 20
owner: history
type: history
status: HISTORICAL
last_verified: 2026-11-03
slug: /history/design-evolution/
source_paths:
  - documents
  - stock-server/src/main/java/com/stock/console
  - stock-client/src/desktop
  - stock-client/src/mobile
---

# 历史设计演进与保留结论

## 编排原则

历史提交包含大量设计文档，覆盖数据采集、四层架构、JPA、Redis Pub/Sub、模型训练、DJL、LSTM、新闻情绪、七模块 Maven、Agent、RAG、文档治理、纯 Python 重建和 Web 控制台。重建时按三类处理：仍与当前事实一致的内容并入主题文档；仍有解释价值但实现已删除的内容放在本页；与当前源码冲突且没有解释价值的接口和类级细节删除。

## 架构演进

| 阶段 | 历史设计 | 当前处理 |
|---|---|---|
| 早期 Java | 四层/多模块交易系统、Mapper 到 JPA 迁移、模块集成测试 | 实现已删除；保留分层、单一职责和统一响应原则 |
| 数据总线 | Redis Pub/Sub 模块通信 | 已删除；当前控制台直接读取文件事实 |
| 模型平台 | LSTM、DJL、PyTorch、模型二进制存储、训练控制器 | 已删除；保留时间切分、制品版本和样本外评估原则 |
| 数据治理 | 股票资料、行情、新闻、事件事实、质量 Gate | 新闻链已删除；Parquet 分区和质量审计原则保留 |
| 七模块 Maven | base/data/model/trading/platform/agent/application | 已删除；不能再用于解释当前包结构 |
| Python T+1 | BaoStock、Qlib、LightGBM、SQLite、券商 HTTP | 成为上游业务权威和当前数据来源 |
| 只读 Web | Spring Boot + React 读取 Python 事实 | 当前仓库实现 |

## 历史文档主题清单

| 历史主题 | 设计信息 | 重编排位置或处置 |
|---|---|---|
| 四层架构与模块 1-9 | 数据、模型、交易、任务的分层职责和验收思路 | 通用原则并入系统与代码说明；旧模块编号删除 |
| MyBatis/MyBatis-Plus 到 JPA | ORM 边界、Repository 和实体治理 | 当前无 ORM 主链，保留“单一数据访问入口”原则 |
| Redis Pub/Sub 模块通信 | 模块解耦、事件发布与消费 | 当前无消息总线，作为已删除方案记录 |
| `ApiResponse` 设计 | 统一成功/失败响应和异常映射 | 并入 [API 与数据契约](../03-code/api-and-data-contracts.md) |
| 数据采集 v3 与智能增量 | Token 接口、增量过滤、周全量、Tushare 接入 | 当前只读 Parquet 契约并入 [市场数据与股票池](../02-business/market-data.md) |
| LSTM 训练控制器与特征工程 | 时间窗口、特征、训练入口和模型存储 | 原实现删除，防泄漏与版本化原则并入模型说明 |
| MongoDB 模型二进制存储 | 模型制品与元数据一致性 | 当前使用文件制品与 SQLite 元数据，旧存储方案删除 |
| DJL/PyTorch 迁移 | Java 推理引擎、依赖和模型加载 | 已被 Python Qlib/LightGBM 路径替代 |
| 综合预测与反馈迭代 | 预测回填、新闻水位、模型反馈 | 预测可追溯原则保留，新闻/反馈实现删除 |
| Ranking LSTM / MASTER | 四输出、多任务损失、横截面注意力、连续权重融合 | 作为研究历史保留，不描述为当前模型 |
| Sentiment / Mengzi | 固定制品、影子部署、结果对比和回滚门槛 | 当前无对应运行路径，保留影子验证原则 |
| 七模块 Maven | base、data、model、trading、platform、agent、application | 已删除，当前 Java 包结构另行说明 |
| Agent RAG 与 Memory | 摄取、Embedding、长期记忆、工具审计、Provider 治理 | 收敛为当前文档搜索与只读快照问答 |
| 前端设计与可调宽面板 | 语义 Token、完整页面、拖拽侧栏、文档门户 | 继续用于 PC 工作台，并增加跨端隔离 |
| 部署与多镜像发布 | 镜像、标签、Compose、文档打包和验证层级 | 按当前双容器工作流重写 |
| Python 阶段 01-12 | 市场、模型、回测、状态、券商、推理、计划、执行、绩效、验证、收口、直记 | 汇总到 [阶段交付记录](delivery-stages.md) |

## 市场数据设计

历史文档曾定义股票资料全量同步、退市治理、日线增量与回补、工作日规则、新闻多来源采集、原始文档与事件事实分离、历史回填、质量 Gate 和时序新闻特征。当前仓库只保留股票目录、日线、日线历史和 5 分钟 Parquet 的只读查询。

仍有效的结论：原始事实不可静默覆盖；数据源、时间边界和版本必须可追踪；缺失与真实空值必须区分；进入模型前需要质量审计。新闻集合、MongoDB 文档结构和具体采集类已与当前代码不符，未恢复。

## 模型生命周期设计

历史模型文档覆盖不可变训练快照、防泄漏、Ranking LSTM 四输出、MASTER 横截面注意力、市场状态门控、候选晋级、预测队列、交易资格、Sentiment、Mengzi 影子部署和双模型连续权重融合。

当前保留的抽象只有：

- 模型版本、特征集、制品哈希和指标必须可审计。
- 预测必须绑定日期、股票、排名、分数和模型版本。
- 训练、验证、样本外和真实交易指标不能混用。
- 模型切换需要候选与当前模型的可比较证据。
- 真实交易表现按模型版本归属。

具体 LSTM、MASTER、Mengzi、DJL、情绪模型路由和 Java 训练类已经过时，不再列为当前能力。

## 交易设计

历史 Java 交易模块曾实现上午分阶段卖出、下午前三名竞争唯一资金执行权、五档行情、活动订单、部分成交锁定、撤单终态、重启恢复、T+1 可卖数量和绩效回写。Python 重建继承了“计划、尝试、券商事实、已实现交易分层”和幂等原则，随后阶段 12 删除了复杂提单门禁与正常流程成交等待。

当前控制台只展示最终留下的数据：本地尝试、券商订单、已实现交易、推导持仓和绩效。旧 Java 的任务开关、Quartz Job、策略配置、止损止盈和活动订单内存状态不再存在。

## Agent 设计

历史 Agent 文档分为交互、交易计划、支撑、工具、知识、运行和集成能力，并设计过 RAG 摄取、Embedding、Memory、Provider 路由、预算、熔断、工具审计、证据组装、计划草案、人工审批与用户隔离。

当前实现收敛为：Servlet Session 下的内存会话、同会话串行化、只读业务快照、Markdown 搜索、单一 OpenAI 兼容 Provider 和本地降级回答。长期 Memory、向量库、可写工具、交易计划审批和独立 Agent 容器均未恢复。

## 前端与文档治理

历史前端设计确立了统一语义 Token、可调宽侧栏、业务页面完整性、文档源码跳转和内置 Markdown 工作台。当前继续保留这些原则，并新增 PC/移动端完全隔离：PC 面向完整工作台，移动端固定四菜单。

文档治理继续采用“源码和运行事实优先、状态等级明确、关联路径可追踪、历史内容显式标识”的规则。根 `documents/` 同时服务 PC 浏览和 Agent 检索，是当前系统说明的单一文档来源。

## 已明确删除的过时内容

- 七模块 Maven 依赖图和各模块类目录。
- MySQL/MongoDB 作为当前主数据源的描述。
- Redis Pub/Sub、Quartz、独立模型服务和独立 Agent 服务拓扑。
- DJL、Ranking LSTM、MASTER、Mengzi 与情绪模型的当前接口声明。
- 旧 React 路由、旧侧边菜单和同一 DOM 的跨端响应式方案。
- 已删除的真实交易开关、复杂准入命令和 Java 交易写接口。

删除这些内容不代表历史工作没有价值，而是避免使用已失效实现指导当前开发。
