import axios from 'axios';

const request = axios.create({
  baseURL: '/api', // Vite proxy configured in vite.config.ts
  timeout: 60000, // 所有接口统一响应超时时间调整为 60 秒
});

// Response interceptor
request.interceptors.response.use(
  (response) => {
    return response.data;
  },
  (error) => {
    const is502 = error.response?.status === 502;
    const isBackendDown =
      is502 ||
      error.code === 'ERR_NETWORK' ||
      (error.message && String(error.message).includes('ECONNREFUSED'));
    if (isBackendDown) {
      console.warn(
        '[API] 后端未响应（连接被拒绝）。请先启动后端: cd backend && mvn spring-boot:run',
        is502 ? '(代理返回 502)' : '(网络错误)'
      );
    } else {
      console.error('Request Error:', error);
    }
    return Promise.reject(error);
  }
);

export default request;
