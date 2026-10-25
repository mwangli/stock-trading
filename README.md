<!-- AI_GENERATE_START ------------------------------------------------------------ -->
# AI-AGENT 智能股票交易系统

本文件是仓库入口。系统架构、领域设计、运行边界和专项方案由前端内置 Markdown 文档工作台统一展示。

## 项目结构

```text
stock-trading4/
├── stock-backend/    # Spring Boot 核心业务与交易安全边界
├── stock-agent/      # Agent、RAG、Memory 和受控决策增强
├── stock-frontend/   # React + Vite 管理界面，内含只读 Markdown 文档工作台
├── documents/        # Markdown 工程文档唯一事实源
├── model-files/      # 固定模型制品
├── pom.xml           # 三个 Maven 业务模块聚合
└── docker-compose.yml
```

## 文档入口

- [查看统一工程文档](documents/system/project-overview.md)
- 本地站点：`http://localhost:5173/docs/system/project-overview`

前端与文档工作台使用同一个本地进程：

```bash
cd stock-frontend
npm ci
npm run dev
```

前端 Maven 构建会将交易页面和 `/docs/*` 文档路由生成到同一个 SPA 制品：

```bash
mvn -pl stock-frontend package
```

交易页面和文档工作台统一使用 React 19、Vite 与 Node.js `22.22.3`，共享路由、主题、`stock-frontend/package.json`、锁文件和 `stock-frontend/dist/` 构建结果，并进入同一个 Nginx 镜像统一发布。

## 维护规则

- 本文件只保留源码目录入口和稳定职责，不再承载详细设计正文。
- 跨类流程、状态机、算法、运行边界和专项方案统一维护在 `documents`。
- 类、方法、字段和局部不变量继续维护在 Javadoc 中。
- 修改实现时同步核对专题文档的 `source_paths`、状态和最后核对日期。
<!-- AI_GENERATE_END -------- -->
