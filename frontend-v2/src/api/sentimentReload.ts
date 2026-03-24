import request from '../utils/request';

/**
 * 重新加载情感分析模型
 * 对应后端 /api/model-sentiment/reload 接口
 */
export interface SentimentReloadResponse {
  success: boolean;
  message?: string;
  modelLoaded?: boolean;
}

export async function reloadSentimentModel(): Promise<SentimentReloadResponse> {
  const data = await request.post('model-sentiment/reload') as SentimentReloadResponse;
  return data ?? { success: false, message: '情感模型重载失败' };
}

