// 运行时配置
import type { RequestConfig } from '@umijs/max';

// 全局初始化数据
export async function getInitialState(): Promise<{ name: string }> {
  return { name: 'AI Shopping' };
}

// 请求配置
export const request: RequestConfig = {
  timeout: 10000,
  errorConfig: {
    errorThrower: (res: any) => {
      const { success, data, errorCode, errorMessage } = res;
      if (!success) {
        const error: any = new Error(errorMessage);
        error.name = 'BizError';
        error.info = { errorCode, errorMessage, data };
        throw error;
      }
    },
    errorHandler: (error: any) => {
      console.error('请求错误:', error);
      return Promise.reject(error);
    },
  },
  requestInterceptors: [
    (config: any) => {
      // 在请求头中添加 token
      const token = localStorage.getItem('token');
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    },
  ],
  responseInterceptors: [
    (response: any) => {
      return response;
    },
  ],
};
