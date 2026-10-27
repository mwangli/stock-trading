---
id: project-overview
title: 系统总览
sidebar_position: 1
owner: system
type: overview
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-26
source_paths:
  - README.md
  - pom.xml
  - docker-compose.yml
slug: /system/
---

<DocMetadata />

# AI-AGENT 智能股票交易系统

面向 A 股市场的数据采集、模型预测、策略分析、Agent 决策增强、风险控制和自动交易平台。

系统采用“多 Maven 子模块、单 Spring Boot 应用”的模块化单体架构：`stock-backend` 内部划分市场、模型、决策、执行、Agent、调度和应用装配模块，`stock-frontend` 承载独立 Web 应用。Java Backend 始终保留权威事实校验、硬风险、幂等和券商执行权。

## 系统目标与设计要求

项目目标是形成一个自学习、自适应、无人值守的自动交易闭环。Ranking LSTM、MASTER 及情感候选模型在正常运行中不依赖人工审批、人工选择策略或人工调整融合权重，系统根据数据、预测、成交和绩效反馈自动完成训练、评估、晋级、交易、降权、回退和再训练。新的情感候选模型首次替换生产基线前必须完成金标训练、校准和影子评估硬门禁，通过后由确定性规则自动激活。

```text
市场与账户事实
  → 自动数据质量检查
  → 周期训练和候选生成
  → Champion-Challenger 自动比较
  → 小权重上线和滚动升权
  → 策略、风控与自动委托
  → 成交和绩效反馈
  → 自动降权、回退或再训练
```

设计以最简单、最快形成生产闭环为原则：

- 只保留一个权威训练、评估、晋级、融合和回退流程，不维护重复 Gate、兼容旁路或正常流程中的人工确认门禁。
- 自学习优先采用周期重训、不可变数据快照和生产反馈评估，不预先建设复杂在线学习平台。
- 自适应集中在模型版本、融合权重、策略阈值和有界仓位参数，所有调整必须有上下限和自动回退条件。
- Gate 只阻止数据/制品无效、非有限数值、输出塌缩、样本明显不足、回撤越界和真实交易安全违规。
- Rank IC、ICIR、扣成本收益、方向质量等普通模型指标合并为一个综合评分，用于自动比较候选和当前生产模型，不再逐项一票否决。
- Ranking LSTM 与 MASTER 每日同时预测，系统按最近样本外窗口在 `0~1` 内自动搜索双模型权重；任一模型生产影响可双向降为零，权重为零时仍继续评估以支持自动恢复。
- 人工界面主要承担观察、诊断、配置边界和紧急停机，不是 LSTM、MASTER 正常训练、晋级或下单链路的前置条件；情感生产基线首次切换是明确例外。
- 无人值守不取消交易安全：权限、资金、持仓、T+1、交易时间、价格保护、仓位、最大亏损、幂等和券商执行始终由 Backend 的确定性 Java 规则最终控制。
- 本项目按新系统治理，不要求保留被替代机制的前向兼容；新路径完成后直接删除旧状态、旧配置、旧接口和冗余实现。

## 核心能力

- 股票资料、日线、分钟线、新闻、交易日历和可交易状态维护。
- Java DJL 与 PyTorch Engine 承载 LSTM 训练、推理和财经情感分析。
- 确定性选股、技术指标、信号生成、策略状态和历史回测。
- Agent 研究、诊断、RAG、Memory、交易计划和受控候选判断。
- 账户、持仓、订单、成交、T+1、风险门禁和券商委托。
- Quartz 统一任务管理、执行记录和跨模块业务编排。
- React 管理界面独立构建，通过 Nginx 反向代理访问唯一 Application。

## 项目结构

```text
stock-trading4/
├── stock-backend/           # Backend Maven 聚合器
│   ├── stock-base/      # 最小公共契约
│   ├── stock-data/        # 市场事实
│   ├── stock-model/         # 模型训练与推理
│   ├── stock-strategy/      # 策略与决策
│   ├── stock-trading/     # 风控与交易执行
│   ├── stock-agent/         # Agent、RAG、Memory 和决策增强
│   ├── stock-platform/      # Quartz 调度
│   └── stock-application/   # 唯一 Spring Boot 可执行模块
├── stock-frontend/          # React + Vite 独立前端
├── documents/               # Markdown 文档唯一事实源
│   ├── system/              # 系统入口、全局架构和端到端闭环
│   ├── modules/             # Maven 模块与 Frontend 职责边界
│   ├── topics/              # 模型、Agent 等跨类或跨模块专题
│   ├── operations/          # 有实际内容时维护部署、运行和排障手册
│   ├── governance/          # 文档状态、元数据和维护规则
│   └── decisions/           # 架构决策记录 ADR
├── stock-frontend/src/pages/documentation/ # 内置 Markdown 文档工作台
├── model-files/             # Git LFS 固定模型制品，发布时构建进对应镜像
├── .tmp/                    # 临时文件和中间脚本，不提交
├── docker-compose.yml
├── pom.xml                  # Backend 与 Frontend 聚合及统一版本管理
├── README.md                # 仓库入口和文档门户导航
└── AGENTS.md
```

`documents/` 统一维护系统架构、领域设计、专项方案、运行边界和运维文档；`stock-frontend/src/pages/documentation/` 只维护只读预览、导航和链接解析。仓库只保留根目录项目级 `README.md` 作为入口，模块和源码包不再维护 README，Javadoc 维护类、方法和局部业务约束。

## 系统架构

### 模块职责

| 模块 | 当前职责 | 默认端口 |
|---|---|---:|
| `stock-application` | 聚合全部 Backend 子模块，提供唯一 Spring Boot 进程和真实交易执行边界 | 8080 |
| `stock-agent` | Application 内部 Maven 模块，承载 LangChain4j、RAG、Memory 和受控建议 | 同进程 |
| `stock-frontend` | React 页面、交互状态及 Nginx API 代理 | 80 |

Backend 启动类位于 `stock-application` 的共同父包 `com.stock`。业务能力拆为独立 Maven JAR，但只由 `stock-application` 装配运行；模块依赖固定为 `base → data → model → strategy → trading → platform → agent → application`。Agent 直接调用 Platform 公开 Service，不访问 Backend Repository，也不再通过内部 HTTP 调用自身进程。

### 运行关系

```text
浏览器
  |
stock-frontend:80
  |-- /api/** --> stock-application:8080
  `-- /ws/** -> stock-application:8080

stock-agent Service -> stock-platform 公开 Service -> Backend 领域 Service
```

- Agent 与 Backend 共用进程、登录 Session、Mongo 客户端和生命周期，代码依赖通过 Maven 模块顺序和公开 Service 保持单向边界。
- Backend 事实查询失败时，Agent 行情、候选、券商、策略和交易决策功能明确失败，不得使用 Mock、空对象或模型猜测替代权威事实。

### 数据所有权

| 数据类别 | 权威存储 | 所有者 |
|---|---|---|
| 用户、配置、任务、模型元数据、交易批次与状态 | MySQL | Backend |
| 行情、新闻、预测明细、回测、账户和持仓快照 | MongoDB | Backend |
| Agent 会话、任务、Memory、RAG、计划与审计 | MongoDB | Agent |
| 登录 Session | Servlet 单实例内存 | Application |
| 可重建查询缓存 | Caffeine 进程内缓存 | Application |
| LSTM 参数和训练快照 | MongoDB GridFS | Backend |
| Embedding 固定制品 | `model-files/embedding` | Agent |
| Sentiment 固定制品 | `model-files/sentiment` | Backend |

Session 和 Caffeine 缓存随进程重启丢失，不保存不可丢失业务事实。跨模块传递明确 DTO，不传递持久化 Entity、Repository 或供应商原始响应对象。

### 核心安全边界

- 行情、账户、持仓、订单和交易日历属于事实；模型和 Agent 输出属于推断。
- 模型预测、策略信号和 Agent 建议都不是最终订单。
- 真实委托前由 Backend 重新读取最新事实，完成权限、交易时间、价格、资金、持仓、T+1、仓位、幂等和券商校验。
- Agent 不持有券商凭据，不直接调用券商接口，不控制、暂停或放行真实订单。
- Ranking LSTM 与 MASTER 训练成功后先生成候选版本，由自动评估、渐进升权和自动回退流程决定生产生效；新的情感候选模型通过影子门槛后也由确定性规则自动激活，运行异常或滚动指标劣化时自动回退。
- Agent、模型、外部 Provider 或缓存不可用时，不得扩大交易权限。
- `trading.real-write-enabled` 默认关闭。

## 核心业务链路

```text
市场数据采集
  → 数据质量与交易日确认
  → 模型训练或批量推理
  → 策略排名与候选信号
  → Java 风控复核
  → 券商委托
  → 订单、成交、持仓与绩效回写
```

### 数据准备

Backend 从证券接口采集股票资料、行情、复权因子、可交易状态、新闻和交易日历，完成解析、去重、覆盖范围与来源状态检查后写入权威存储。日常增量和历史回补独立运行；数据源整体为空、解析失败或关键范围不明确时必须失败。

### 模型与策略

模型模块从合格数据生成不可变训练快照、候选模型和预测批次。训练与生产切换解耦，但候选比较、渐进上线、滚动升权和异常回退由系统自动完成；预测结果携带模型版本、目标交易日、信息截止时间和质量状态。

策略模块将模型预测、情感、技术指标、成交量和配置规则转换为排名、信号与候选集合。候选必须携带来源批次、策略版本和有效期，预测完成不等于具备交易资格。

### Agent 增强

Agent 通过只读工具获取最小、脱敏事实，生成研究、诊断、报告、交易计划草案或独立交易建议。建议单独保存和评估，不进入 Backend 自动交易执行链。

### 交易执行

Backend 在提交委托前重新读取最新账户、持仓、交易时间、交易日历、价格和运行开关，并执行权限、资金、仓位、T+1、停牌、涨跌停、幂等和批次隔离检查。券商订单和成交回写后，系统基于真实成交聚合交易记录与已实现收益。

### 任务编排

Quartz Job 只确定触发时机、调用领域 Service 并记录执行结果，不复制数据、模型、策略或交易算法。所有任务通过 `JobConfig + JobSchedulerService` 注册，不在业务类中新增 `@Scheduled`。

### 失败与降级

| 故障 | 处理原则 |
|---|---|
| Agent Provider 超时或熔断 | 记录建议失败，不影响 Backend 确定性交易链 |
| Backend 事实 API 不可用 | Agent 核心保持可用，事实依赖能力明确降级 |
| Embedding 不可用 | RAG 和向量 Memory 不可用，其他 Agent 任务按依赖继续 |
| 情感模型不可用 | 停止依赖情感的增强评分并暴露降级状态 |
| 历史行情存在缺口 | 日常增量继续，缺口交由独立回补任务处理 |
| 券商会话失效 | 禁止实时查询和真实写入，保留本地历史诊断 |
| 交易日历未确认 | 禁止自动交易，不使用工作日猜测替代 |
| 训练失败 | 保留当前激活模型，失败候选不得生效 |

## 部署运行

系统以一个 Backend Application 容器和一个 Frontend Nginx 容器发布，Backend 连接 MySQL 与 MongoDB。Agent、模型运行时和文档都进入统一 Application 生命周期，不建立独立服务。

运行拓扑、镜像内容、配置持久化、CI/CD 触发条件和分层验证要求统一见[部署与发布](../operations/部署与发布.md)。

## 文档导航

| 阅读目标 | 权威文档 |
|---|---|
| Backend 整体架构和模块边界 | [Backend 模块总览](../modules/后端模块总览.md) |
| 最小公共响应和缓存契约 | [stock-base](../modules/stock-base/公共契约.md) |
| 市场事实和新闻质量 | [stock-data](../modules/stock-data/市场事实.md) |
| 训练、推理和模型生命周期 | [stock-model](../modules/stock-model/模型模块.md) |
| 选股、信号和回测 | [stock-strategy](../modules/stock-strategy/智能决策.md) |
| 风控、券商和真实交易 | [stock-trading](../modules/stock-trading/交易执行.md) |
| Quartz 任务编排 | [stock-platform](../modules/stock-platform/任务调度.md) |
| 应用启动、存储配置和最终装配 | [stock-application](../modules/stock-application/应用装配.md) |
| Agent 总体边界 | [stock-agent](../modules/stock-agent/智能代理总览.md) |
| Agent 各能力设计 | [Agent 专题](../topics/agent/交互能力.md) |
| 前端页面、状态和接口分流 | [stock-frontend](../modules/stock-frontend/前端设计.md) |
| 部署、镜像、配置和 CI/CD | [部署与发布](../operations/部署与发布.md) |

## 工程文档治理规范

### 文档归属

1. `documents/system` 维护系统入口、全局架构和端到端闭环。
2. `documents/modules` 按实际 Maven 模块和 Frontend 维护职责、依赖边界与源码入口。
3. `documents/topics` 维护模型生命周期、Agent 能力等跨类或跨模块专项设计。
4. `documents/operations` 仅在存在真实部署、运行检查、模型制品或排障手册时建立内容。
5. `documents/governance` 和 `documents/decisions` 分别维护治理规则与 ADR。
6. 根目录项目级 `README.md` 只提供稳定职责、源码位置和门户入口，模块与源码包不再维护 README。
7. Javadoc 与 TypeScript 类型维护类、方法、字段和局部不变量。
8. `model-files/` 只保留运行必需的权重和加载配置，不保存重复 README、manifest 或校验元数据。

### 强制维护规则

- 文档与所属代码在同一变更中更新；新增、移动、重构或删除能力时同步修改对应专题文档。
- 同一事实只在一篇权威文档中完整定义，其他页面使用相对链接引用。
- 每篇专题文档必须维护 owner、type、status、last_verified 和 source_paths。
- 文档只描述当前事实和明确状态，不保留已废弃目录、旧 URL、兼容路径或两套并行设计。
- 文档不得记录真实密码、Token、Cookie、账户、生产数据或临时排查输出。
- 文档评审必须检查链接、模块归属、依赖方向、状态描述和验证证据是否一致。

### 设计状态

| 状态 | 含义 |
|---|---|
| `PROPOSED` | 仅完成设计，尚未完成源码实现 |
| `SOURCE_IMPLEMENTED` | 源码已经实现或正在工作区实现，但未完成编译或运行验证 |
| `COMPILED` | 已完成编译或构建，不代表业务效果有效 |
| `RUNTIME_VERIFIED` | 已完成文档声明范围内的运行验证 |
| `SUPERSEDED` | 已废弃、被替代或不再继续实施 |

每项专项设计必须说明状态、目标与非目标、当前证据、设计方案、影响边界、验收条件、未验证事项和退出条件。源码存在、编译成功、运行成功、部署成功和生产效果是不同证据层级，禁止混为一谈。

## 免责声明

本项目用于研究、工程实践和个人交易辅助。模型预测、回测结果、策略评分和 Agent 输出均不构成投资建议，也不能保证真实收益。真实交易必须在充分理解风险、完成独立验证并显式开启写入能力后使用。
## 情感与时序模型生产边界

- FinBERT 是当前生产情感基线；Mengzi 首版采用官方金融编码器加冻结编码器的三分类头，没有人工金标训练、温度校准和影子评估证据前不得激活。
- 情感模型只输出文本三分类概率、不确定性和覆盖信息，不直接生成最终选股分数。Ranking LSTM 与 MASTER 使用 `temporal-news-v1` 日级快照学习新闻、行情和市场状态对未来收益的联合关系。
- Ranking LSTM 与 MASTER 当前统一输入为 `tushare-free-temporal-news-v6-53d`，包含 25 个免费行情和市场连续特征、25 个新闻连续特征及 3 个类别索引，共 53 维。模型日线由 Tushare 免费 `daily` 自动准备，价格使用 `pre_close` 连续化，市场基准由训练股票池等权构造；付费数据增强目标是在同一 V6 代际恢复 3 项特征并升级为 56 维新制品。旧 31 维和旧 56 维制品均不能冒充当前契约。
- `RISK_ONLY` 事件不进入普通 Alpha 特征，继续由 Java 权限、风控、资金、持仓、幂等和券商执行边界处理。
- Mengzi 影子门槛通过后自动切换为 `ACTIVE`；运行故障或滚动指标跌破门槛时自动回滚 FinBERT。任何模型输出都不能直接调用真实交易写接口。
- 模型证据必须分层记录：DJL 加载、固定样本推理、接口启动、金标质量和影子收益分别验收，前一层通过不能替代后一层。

详细输入、训练数据、快照维度和影子评估契约见[模型文档](../modules/stock-model/模型模块.md)；事件事实、截止时间和新闻治理契约见[市场数据文档](../modules/stock-data/市场事实.md)。
