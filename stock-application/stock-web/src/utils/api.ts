// AI_GENERATE_START--
import axios from 'axios';

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  total?: number;
}

export const readApiData = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (!response.success) {
    throw new Error(response.message || fallback);
  }
  return response.data;
};

export const getApiErrorMessage = (cause: unknown, fallback: string): string => {
  if (axios.isAxiosError<ApiResponse<unknown>>(cause)) {
    return cause.response?.data?.message || cause.message || fallback;
  }
  return cause instanceof Error ? cause.message : fallback;
};
// AI_GENERATE_END--
