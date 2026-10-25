<!-- AI_GENERATE_START --------------------- -->
# Frontend 前端设计

React 页面、状态表达、接口分流和视觉约束统一维护在文档门户。

本模块统一使用 React 19 和 Node.js `22.22.3`。交易应用与 `documentation/` 下的 Docusaurus 构建组件共享一个 `package.json` 和锁文件，生产构建分别输出到 `dist/` 与 `dist/docs/`。

本地执行 `npm run dev` 会统一启动 Vite 与 Docusaurus，并由 Vite 将同域 `/docs/` 代理到内部文档开发服务。开发者只需要访问 `http://localhost:5173`，不需要分别管理两个终端。

Docker 构建将依赖、React 应用和 Docusaurus 文档拆分为独立缓存阶段。只修改 `documents/` 或 `documentation/` 时复用 App 构建层，只修改 `src/`、`public/` 或 Vite 配置时复用 Docs 构建层；依赖清单变化时两者同时重建，最终仍合并为一个 Nginx 镜像。

## 文档入口

- [查看统一工程文档](../documents/frontend/index.md)
- 本地站点：`http://localhost:5173/docs/frontend/`
- 全局页头提供“文档查阅”按钮，通过同域 `/docs/` 在新窗口打开独立文档站点；不再维护侧边栏文档菜单、内嵌文档路由或 iframe 页面。开发环境由 Vite 代理到本地文档服务，生产环境由 Nginx 托管或代理该路径。

## 维护规则

- 本文件只保留源码目录入口和稳定职责，不再承载详细设计正文。
- 跨类流程、状态机、算法、运行边界和专项方案统一维护在 `documents`。
- 类、方法、字段和局部不变量继续维护在 Javadoc 中。
- 修改实现时同步核对专题文档的 `source_paths`、状态和最后核对日期。
<!-- AI_GENERATE_END ------------ -->
