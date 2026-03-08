import request from '../utils/request';

/** 情感分析服务健康状态（与后端 health 接口一致） */
export interface SentimentHealth {
  status: string;
  service: string;
  modelLoaded: boolean;
  lastLoadedTime?: string;
}

/**
 * 获取情感分析服务健康状态
 */
export async function getSentimentHealth(): Promise<SentimentHealth> {
  const data = await request.get('models/sentiment/health') as SentimentHealth;
  return data ?? { status: 'UNKNOWN', service: 'Sentiment', modelLoaded: false };
}

/** 情感分析单条结果（与后端 analyze 接口一致） */
export interface SentimentAnalyzeResult {
  success?: boolean;
  message?: string;
  label?: string;
  score?: number;
  confidence?: number;
  probabilities?: Record<string, number>;
  text?: string;
  modelLoaded?: boolean;
}

/**
 * 情感分析：输入文本，返回预测结果
 */
export async function analyzeSentiment(text: string): Promise<SentimentAnalyzeResult> {
  const data = await request.post('models/sentiment/analyze', { text }) as SentimentAnalyzeResult;
  return data ?? { success: false, message: '请求失败' };
}
