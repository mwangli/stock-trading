<!-- AI_GENERATE_START ----------------------------------------------------------- -->
# AI-AGENT 智能股票交易系统

本文件是仓库入口。系统架构、领域设计、运行边界和专项方案由 Docusaurus 文档工程统一管理。

## 项目结构

```text
stock-trading4/
├── stock-backend/    # Spring Boot 核心业务与交易安全边界
├── stock-agent/      # Agent、RAG、Memory 和受控决策增强
├── stock-frontend/   # React + Vite 管理界面，内含 Docusaurus 构建组件
├── documents/        # Markdown 工程文档唯一事实源
├── model-files/      # 固定模型制品
├── pom.xml           # 三个 Maven 业务模块聚合
└── docker-compose.yml
```

## 文档入口

- [查看统一工程文档](documents/system/project-overview.md)
- 本地站点：`http://localhost:5173/docs/system/project-overview`

文档门户本地命令：

```bash
cd stock-frontend
npm ci
npm run dev
```

前端 Maven 构建会同时生成交易前端和文档站点：

```bash
mvn -pl stock-frontend package
```

交易前端和 Docusaurus 统一使用 React 19 与 Node.js `22.22.3`，共享 `stock-frontend/package.json` 和锁文件。构建结果分别输出到 `stock-frontend/dist/` 与 `stock-frontend/dist/docs/`，并进入同一个 Nginx 镜像统一发布。

## 维护规则

- 本文件只保留源码目录入口和稳定职责，不再承载详细设计正文。
- 跨类流程、状态机、算法、运行边界和专项方案统一维护在 `documents`。
- 类、方法、字段和局部不变量继续维护在 Javadoc 中。
- 修改实现时同步核对专题文档的 `source_paths`、状态和最后核对日期。
<!-- AI_GENERATE_END ------- -->
