<!-- AI_GENERATE_START----- ------------------------------------------------------------- -->
# AI-AGENT 智能股票交易系统

本文件是仓库入口。系统架构、领域设计、运行边界和专项方案由前端内置 Markdown 文档工作台统一展示。

## 项目结构

```text
stock-trading4/
├── stock-server/    # 多 Maven 子模块、单 Spring Boot 应用
│   ├── stock-base/    # 最小公共契约
│   ├── stock-data/      # 市场事实
│   ├── stock-model/       # 模型训练与推理
│   ├── stock-strategy/    # 策略与决策
│   ├── stock-trading/   # 风控与交易执行
│   ├── stock-agent/       # Agent、RAG、Memory 和受控增强
│   ├── stock-platform/    # Quartz 调度
│   └── stock-application/ # 唯一可执行 Spring Boot 模块
├── stock-client/   # React + Vite 管理界面，内含只读 Markdown 文档工作台
├── documents/        # 系统、模块、专题、运维、治理和 ADR 唯一事实源
├── model-files/      # 固定模型制品
├── pom.xml           # Backend Maven 父工程与聚合器
└── docker-compose.yml
```

## 文档入口

- [查看统一工程文档](documents/system/project-overview.md)
- 本地站点：`http://localhost:5173/docs/system`
- 部署运行：[部署与发布](documents/operations/部署与发布.md)

前端与文档工作台使用同一个本地进程：

```bash
cd stock-client
npm ci
npm run dev
```

前端生产构建由 npm/Vite 独立管理，将交易页面和 `/docs/*` 文档路由生成到同一个 SPA 制品：

```bash
cd stock-client
npm run build
```

交易页面和文档工作台统一使用 React 19、Vite 与 Node.js `22.22.3`，共享路由、主题、`stock-client/package.json`、锁文件和 `stock-client/dist/` 构建结果，并通过独立 Frontend Dockerfile 进入同一个 Nginx 镜像发布。Frontend 不参与 Maven Reactor。

## 维护规则

- 本文件只保留源码目录入口和稳定职责，不再承载详细设计正文。
- 模块职责维护在 `documents/modules`，跨模块流程、状态机和算法维护在 `documents/topics`。
- 运行手册、文档治理和架构决策分别维护在 `documents/operations`、`documents/governance` 和 `documents/decisions`。
- 类、方法、字段和局部不变量继续维护在 Javadoc 中。
- 修改实现时同步核对专题文档的 `source_paths`、状态和最后核对日期。
<!-- AI_GENERATE_END----- --------- -->
