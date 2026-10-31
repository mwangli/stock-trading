// AI_GENERATE_START -
import axios from 'axios'

export interface ApiEnvelope<T> {
  success: boolean
  data: T
  message: string | null
}

export type DataRecord = Record<string, unknown>

export const api = axios.create({
  baseURL: '/api',
  timeout: 30_000,
  withCredentials: true,
})

api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      window.dispatchEvent(new Event('stock-console:unauthorized'))
    }
    return Promise.reject(error)
  },
)

export async function getData<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const response = await api.get<ApiEnvelope<T>>(url, { params })
  if (!response.data.success) {
    throw new Error(response.data.message || '请求失败')
  }
  return response.data.data
}

export async function postData<T>(url: string, body?: unknown): Promise<T> {
  const response = await api.post<ApiEnvelope<T>>(url, body)
  if (!response.data.success) {
    throw new Error(response.data.message || '请求失败')
  }
  return response.data.data
}
// AI_GENERATE_END -
