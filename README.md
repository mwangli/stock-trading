<!-- AI_GENERATE_START------- -->
# AI-AGENT 智能股票交易系统

本文件是仓库入口。系统架构、领域设计、运行边界和专项方案由前端内置 Markdown 文档工作台统一展示。

## 项目结构

```text
stock-trading4/
├── stock-server/                  # 七个 Maven 子模块、单 Spring Boot 应用
│   ├── stock-base/                # 最小公共契约
│   ├── stock-data/                # 市场事实
│   ├── stock-model/               # 模型训练、推理与排名
│   ├── stock-trading/             # 分阶段交易、券商执行与交易事实
│   ├── stock-platform/            # Quartz 调度
│   ├── stock-agent/               # Agent、RAG、Memory 和只读事实查询
│   └── stock-application/         # 唯一可执行 Spring Boot 模块
├── stock-client/                  # React + Vite 管理界面和文档工作台
├── documents/                     # 工程文档唯一事实源
├── model-files/                   # 本地模型制品；生产环境由宿主机目录只读挂载
├── pom.xml                        # Backend Maven 父工程与聚合器
└── docker-compose.yml
```

Backend 固定依赖方向：

```text
base → data → model → trading → platform → agent → application
```

## 自动交易链路

```text
模型预测与排名
→ 上午分阶段卖出
→ 下午模型前三名资金竞争
→ 单一活动买单
→ 成交与收益回写
```

交易任务启停是自动交易唯一控制入口。系统不再维护独立 Strategy 模块、策略模式、候选二次评分、止损止盈、紧急停止或真实写入总门禁。订单轮询、部分成交、撤单终态确认、资金释放、T+1 可卖数量和券商事实同步仍属于交易执行正确性要求。

## 文档入口

- [系统总览](documents/system/project-overview.md)
- [Backend 模块总览](documents/modules/后端模块总览.md)
- [Trading 交易执行](documents/modules/stock-trading/交易执行.md)
- [Platform 任务调度](documents/modules/stock-platform/任务调度.md)
- [部署与发布](documents/operations/部署与发布.md)

本地文档站点与交易前端使用同一个 React/Vite 进程，生产构建也生成同一个 SPA 制品。Frontend 不参与 Maven Reactor。

## 维护规则

- 本文件只保留稳定架构、核心链路和文档入口。
- 模块职责维护在 `documents/modules`，跨模块流程维护在 `documents/topics`。
- 类、方法、字段和局部不变量维护在 Javadoc 或 TypeScript 类型旁。
- `SOURCE_IMPLEMENTED` 只表示源码已实现，不代表编译、运行、数据库或真实券商验证通过。
<!-- AI_GENERATE_END------- -->
