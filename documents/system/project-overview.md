---
id: project-overview
title: 系统总览
sidebar_position: 1
owner: system
type: overview
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
source_paths:
  - README.md
  - pom.xml
  - docker-compose.yml
---

<DocMetadata />

# AI-AGENT 智能股票交易系统

面向 A 股市场的数据采集、模型预测、策略分析、Agent 决策增强、风险控制和自动交易平台。

系统采用三模块架构：`stock-backend` 承载核心业务与交易安全边界，`stock-agent` 承载独立 Agent 能力，`stock-frontend` 承载独立 Web 应用。Java Backend 始终保留权威事实校验、硬风险、幂等和券商执行权。

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
- React 管理界面独立构建，通过 Nginx 反向代理访问 Backend 与 Agent。

## 项目结构

```text
stock-trading4/
├── stock-backend/           # Spring Boot 后端，内部按业务包组织
├── stock-agent/             # Agent、RAG、Memory 和决策增强
├── stock-frontend/          # React + Vite 独立前端
├── documents/               # Markdown 文档唯一事实源
│   ├── system/              # 系统总览、治理规范和架构决策
│   ├── backend/             # 后端领域与模型专题
│   ├── agent/               # Agent 架构与能力设计
│   └── frontend/            # 前端页面与交互设计
├── stock-frontend/src/pages/documentation/ # 内置 Markdown 文档工作台
├── model-files/             # Git LFS 固定模型制品，发布时构建进对应镜像
├── .tmp/                    # 临时文件和中间脚本，不提交
├── docker-compose.yml
├── pom.xml                  # 三模块聚合与统一版本管理
├── README.md                # 仓库入口和文档门户导航
└── AGENTS.md
```

`documents/` 统一维护系统架构、领域设计、专项方案、运行边界和运维文档；`stock-frontend/src/pages/documentation/` 只维护只读预览、导航和链接解析。仓库只保留根目录项目级 `README.md` 作为入口，模块和源码包不再维护 README，Javadoc 维护类、方法和局部业务约束。

## 系统架构

### 模块职责

| 模块 | 当前职责 | 默认端口 |
|---|---|---:|
| `stock-backend` | 数据、模型、策略、交易、Quartz、内部事实 API 和唯一真实交易执行边界 | 8080 |
| `stock-agent` | LangChain4j、RAG、Memory、研究诊断、交易计划草案和独立交易建议 | 8081 |
| `stock-frontend` | React 页面、交互状态及 Nginx API 分流 | 80 |

Backend 启动类位于共同父包 `com.stock`，内部使用 `market`、`model`、`decision`、`execution`、`schedule` 和 `platform` 表达业务边界，这些包不是独立 Maven 模块。Agent 与 Backend 不共享 Java 实现或 Maven DTO JAR，通过受保护的 HTTP JSON 契约协作。

### 运行关系

```text
浏览器
  |
stock-frontend:80
  |-- /api/agent/** --> stock-agent:8081
  |-- /api/** -------> stock-backend:8080
  `-- /ws/** --------> stock-backend:8080

stock-agent -- /api/internal/agentFacts/** --> stock-backend
```

- `/api/internal/**` 不向浏览器开放，Nginx 对外返回 `404`。
- Backend 与 Agent 的内部调用使用 `X-Internal-Token`。
- Agent 可在 Backend 不在线时独立启动；普通对话、Provider、RAG、Memory 和会话管理继续可用。
- Backend 事实源不可用时，行情、候选、券商、策略和交易决策功能返回 `FACT_SOURCE_UNAVAILABLE`，不得使用 Mock、空对象或模型猜测替代权威事实。

### 数据所有权

| 数据类别 | 权威存储 | 所有者 |
|---|---|---|
| 用户、配置、任务、模型元数据、交易批次与状态 | MySQL | Backend |
| 行情、新闻、预测明细、回测、账户和持仓快照 | MongoDB | Backend |
| Agent 会话、任务、Memory、RAG、计划与审计 | MongoDB | Agent |
| 登录 Session、可重建查询缓存 | Redis | Backend 与 Agent |
| LSTM 参数和训练快照 | MongoDB GridFS | Backend |
| Embedding 固定制品 | `model-files/embedding` | Agent |
| Sentiment 固定制品 | `model-files/sentiment` | Backend |

Redis 不保存不可丢失的业务事实。跨模块传递明确 DTO，不传递持久化 Entity、Repository 或供应商原始响应对象。

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

### Compose 服务

```text
stock-frontend:80
  |-- stock-backend:8080
  `-- stock-agent:8081

stock-backend --> stock-mysql + stock-mongo + stock-redis
stock-agent   --> stock-mongo + stock-redis
stock-agent   -.按需.-> stock-backend
```

Backend 和 Agent 端口只绑定宿主机回环地址，外部请求统一从 Frontend Nginx 进入。Agent 不应通过 Docker `depends_on` 强制等待 Backend，Backend HTTP 连接只在实际事实查询时发生。

### 构建产物与路由

| 模块 | 产物 | 发布容器 |
|---|---|---|
| `stock-backend` | `stock-backend-*.jar`，继承 Sentiment 模型运行时镜像 | Java 17 JRE，8080 |
| `stock-agent` | `stock-agent-*.jar`，继承 Embedding 模型运行时镜像 | Java 17 JRE，8081 |
| `stock-frontend` | Vite `dist/` | Nginx，80 |

| 路径 | 目标 |
|---|---|
| `/api/agent/**` | Agent |
| `/api/internal/**` | 外部拒绝 |
| 其他 `/api/**` | Backend |
| `/ws/**` | Backend |
| 其他路径 | React SPA |

### 配置与持久化

- `.env` 只保存数据库密码、内部令牌、Provider Key、券商凭据和签名密钥。
- 数据库地址、端口、库名、用户名、运行开关和普通参数统一维护在对应模块的 `application.yml`；容器数据目录维护在 `docker-compose.yml`。
- `INTERNAL_API_TOKEN` 同时注入 Backend 与 Agent，不能发送到浏览器。
- MySQL、MongoDB 使用持久化目录；Redis 使用 AOF，但仍只承担 Session 和可重建缓存。
- GitHub Runner 仅在模型发布工作流中下载目标 Git LFS 目录并完成精确清单校验，分别生成 Backend Sentiment 和 Agent Embedding 模型运行时镜像。
- 应用镜像以对应模型运行时镜像为基础镜像，只追加业务 JAR；最终 Backend 和 Agent 应用镜像内部仍包含完整模型，生产服务器不保存或挂载模型目录。
- 模型镜像使用模型目录 Git tree SHA 作为不可变标签；应用镜像使用“源码 SHA + 模型 tree SHA 前 12 位”的复合标签，并在 OCI label 中记录完整源码、模型版本和模型镜像引用。

### CI/CD

| 标签 | 工作流 | 目标服务 |
|---|---|---|
| `backend-vX.Y[.Z]` | `backend-ci-cd.yml` | `stock-backend` |
| `agent-vX.Y[.Z]` | `agent-ci-cd.yml` | `stock-agent` |
| `frontend-vX.Y[.Z]` | `frontend-ci-cd.yml` | `stock-frontend` |
| `master` 中 `model-files/sentiment/**` 变化 | `backend-model-image.yml` | Backend 模型镜像，然后自动触发 `stock-backend` 重建部署 |
| `master` 中 `model-files/embedding/**` 变化 | `agent-model-image.yml` | Agent 模型镜像，然后自动触发 `stock-agent` 重建部署 |

Backend 与 Agent 的业务源码由 `release_ref` 确定，模型版本由独立的 `model_ref` 确定；模型工作流会把已校验模型所在的不可变提交 SHA 传给应用工作流。模型变化时，模型工作流先发布独立模型镜像，再主动派发对应应用工作流，因此模型镜像不会孤立发布，应用会自动重新构建并部署。普通应用构建只验证目标模型镜像已存在，不重复下载 LFS 大文件。生产服务器只通过镜像仓库拉取最终应用镜像并启动，SCP 仅同步小型 Compose 和部署脚本，不传输模型或应用制品；失败时收集目标服务日志并恢复原镜像。

本地通过 Compose 构建 Backend 或 Agent 前，需要先生成本地模型运行时镜像：

```bash
docker build -f .github/docker/backend-model-runtime.Dockerfile \
  -t stock-backend-model-runtime:local model-files/sentiment
docker build -f .github/docker/agent-model-runtime.Dockerfile \
  -t stock-agent-model-runtime:local model-files/embedding
```

### 运行环境

- Java 17
- Maven 3.8+
- Node.js 22.22.3（前端与文档模块统一强制版本）
- MySQL 8
- MongoDB 8
- Redis 7
- Docker 与 Docker Compose

### 验证边界

容器健康或 HTTP 可达只证明进程入口可用，不代表数据库、模型、Provider、Quartz、券商和业务任务全部正常。发布后需要分别核对镜像 SHA、容器健康、启动日志、依赖状态和目标业务终态。

## 文档导航

| 阅读目标 | 权威文档 |
|---|---|
| Backend 整体架构和模块边界 | [Backend 总览](../backend/后端总览.md) |
| 市场事实和新闻质量 | [Market](../backend/market/市场事实.md) |
| 训练、推理和模型专项设计 | [Model](../backend/model/模型模块.md) |
| 选股、信号和回测 | [Decision](../backend/decision/智能决策.md) |
| 风控、券商和真实交易 | [Execution](../backend/execution/交易执行.md) |
| Quartz 任务编排 | [Schedule](../backend/schedule/任务调度.md) |
| 公共装配和内部事实 API | [Platform](../backend/platform/平台装配.md) |
| Agent 总体边界 | [Agent 总览](../agent/智能代理总览.md) |
| Agent 各能力设计 | [Agent 能力目录](../agent/智能代理总览.md#能力目录) |
| 前端页面、状态和接口分流 | [Frontend](../frontend/前端设计.md) |

## 工程文档治理规范

### 文档归属

1. `documents/system` 维护系统总览、跨模块链路、全局约束和架构决策。
2. `documents/backend`、`agent` 和 `frontend` 按领域维护当前设计与专项方案。
3. Proposal、运行手册和 ADR 使用对应类型目录及状态元数据，不再混入模块总览。
4. 根目录项目级 `README.md` 只提供稳定职责、源码位置和门户入口，模块与源码包不再维护 README。
5. Javadoc 与 TypeScript 类型维护类、方法、字段和局部不变量。
6. 模型制品自身的来源、格式和加载要求仍保留在对应 `model-files/**/README.md`。

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
- Ranking LSTM 与 MASTER 的统一输入为 `cross-sectional-temporal-news-v6`，包含 53 个连续特征和 3 个类别索引，共 56 维。旧 31 维制品不兼容，必须重新训练。
- `RISK_ONLY` 事件不进入普通 Alpha 特征，继续由 Java 权限、风控、资金、持仓、幂等和券商执行边界处理。
- Mengzi 影子门槛通过后自动切换为 `ACTIVE`；运行故障或滚动指标跌破门槛时自动回滚 FinBERT。任何模型输出都不能直接调用真实交易写接口。
- 模型证据必须分层记录：文件和 SHA-256 完整、DJL 加载、固定样本推理、接口启动、金标质量、影子收益分别验收，前一层通过不能替代后一层。

详细输入、训练数据、快照维度和影子评估契约见[模型文档](../backend/model/模型模块.md)；事件事实、截止时间和新闻治理契约见[市场数据文档](../backend/market/市场事实.md)。
