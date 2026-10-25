<!-- AI_GENERATE_START ------- -->
# Frontend Documentation 构建组件

本目录只维护 Docusaurus 配置、主题、组件、静态资源和文档校验脚本。Markdown 正文统一存放在仓库根 `documents/`，React 19、Docusaurus、Vite 和 Node.js 依赖统一由 `stock-frontend/package.json` 管理。

## 本地使用

```bash
cd stock-frontend
npm ci
npm run dev
```

该命令统一启动交易前端和文档开发服务。交易前端地址为 `http://localhost:5173/`，文档通过同域地址 `http://localhost:5173/docs/` 访问；内部 Docusaurus 服务监听 `3000` 端口并由 Vite 代理，不需要单独启动。部署路径默认保持 `/docs/`，可通过 `DOCS_URL` 和 `DOCS_BASE_URL` 覆盖。

## 统一构建

在仓库根目录执行：

```bash
mvn -pl stock-frontend package
```

前端构建先生成 React 应用到 `stock-frontend/dist/`，再生成 Docusaurus 文档到 `stock-frontend/dist/docs/`。Docker 将整个 `dist/` 复制到同一个 Nginx 镜像，因此交易前端与文档一起发布和回滚。

本地需要单独构建时可直接执行 `npm run build:app` 或 `npm run build:docs`。Docker/CI 使用独立的 App 与 Docs 构建阶段：文档输入不变时复用 Docs 缓存，前端输入不变时复用 App 缓存；只有 `package.json` 或锁文件变化时才需要同时重建两部分。

## 静态检查

```bash
npm run validate:docs
```

检查内容包括 Front Matter、文档状态、文档 ID 唯一性和关联源码路径。Docusaurus 构建还会检查站内 Markdown 链接。

## 内容边界

- 根 `documents/` 保存跨类、跨模块和需要可视化导航的权威正文。
- 本目录不保存业务 Markdown 正文。
- 源码旁 README 只保留稳定职责和门户入口。
- Javadoc 维护类、方法、字段和局部业务不变量。
- `build/` 和 `.docusaurus/` 是生成目录，不提交到仓库。
<!-- AI_GENERATE_END ------- -->
