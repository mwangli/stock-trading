import { defineConfig } from '@umijs/max';

export default defineConfig({
  antd: {},
  access: {},
  model: {},
  initialState: {},
  request: {},
  layout: {
    title: 'AI Shopping',
    locale: true,
  },
  routes: [
    {
      path: '/',
      redirect: '/home',
    },
    {
      name: '首页',
      path: '/home',
      component: './Home',
    },
    {
      name: '商品管理',
      path: '/products',
      component: './Products',
    },
    {
      name: '任务管理',
      path: '/collection-tasks',
      component: './CollectionTasks',
    },
    {
      name: '日志管理',
      path: '/log-manager',
      component: './LogManager',
    },
  ],
  npmClient: 'npm',
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    '/ws': {
      target: 'ws://localhost:8080',
      ws: true,
      changeOrigin: true,
    },
  },
});
