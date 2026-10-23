// AI_GENERATE_START---
import axios from 'axios';

const request = axios.create({
  baseURL: '/api', // Vite proxy configured in vite.config.ts
  timeout: 60000, // 所有接口统一响应超时时间调整为 60 秒
  withCredentials: true,
});

// Response interceptor
request.interceptors.response.use(
  (response) => {
    return response.data;
  },
  (error) => {
    if (error.response?.status === 401) {
      window.dispatchEvent(new Event('auth:expired'));
    }
    const is502 = error.response?.status === 502;
    const isBackendDown =
      is502 ||
      error.code === 'ERR_NETWORK' ||
      (error.message && String(error.message).includes('ECONNREFUSED'));
    if (isBackendDown) {
      console.warn('[API] 后端未响应', is502 ? '代理返回 502' : '网络连接失败');
    } else {
      console.error('[API] 请求失败', error);
    }
    return Promise.reject(error);
  }
);

export default request;
// AI_GENERATE_END---
