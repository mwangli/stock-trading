# AI Shopping - Frontend

基于 Ant Design Pro + React 的前端项目

## 技术栈

- **React**: 18.x
- **Ant Design**: 5.x
- **Umi**: 4.x
- **TypeScript**: 5.x

## 快速开始

### 安装依赖

```bash
npm install
```

### 开发环境启动

```bash
npm run dev
```

### 构建生产环境

```bash
npm run build
```

## 项目结构

```
frontend/
├── src/
│   ├── pages/          # 页面组件
│   ├── components/     # 公共组件
│   ├── services/       # API 服务
│   ├── models/         # 全局状态
│   ├── utils/          # 工具函数
│   └── app.tsx         # 应用入口
├── config/             # 配置文件
├── public/             # 静态资源
├── .umirc.ts           # Umi 配置
└── package.json        # 项目依赖
```

## 后端代理配置

开发环境已配置代理，请求 `/api` 会自动转发到 `http://localhost:8080`
