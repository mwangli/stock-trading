// AI_GENERATE_START
import axios from 'axios';
import request from './request';

export interface BackendHealth {
  status: string;
  service: string;
}

interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
}

export const isBackendUnavailable = (error: unknown): boolean => {
  if (!axios.isAxiosError(error)) {
    return false;
  }
  return error.response?.status === 502
    || error.code === 'ERR_NETWORK'
    || error.code === 'ECONNABORTED'
    || String(error.message).includes('ECONNREFUSED');
};

export const checkBackendHealth = async (): Promise<boolean> => {
  const response = await request.get('/system/health', { timeout: 5000 }) as unknown as ApiResponse<BackendHealth>;
  return response.success && response.data?.status === 'UP';
};
// AI_GENERATE_END
