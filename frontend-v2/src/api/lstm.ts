import request from '../utils/request';

/** 后端 ApiResponse 结构 */
interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

/** LSTM 模型列表项，与后端 LstmModelListItemDto 一致 */
export interface LstmModelListItem {
  id: string;
  modelName: string;
  name: string;
  epoch: number;
  createdAt: string;
  modelVersion: string;
  /** 收益金额（元），暂无时为 null */
  profitAmount?: number | null;
  /** 效果分数 0～100，由验证集推导 */
  score?: number | null;
}

/** 分页结果，与后端 PageResult 一致 */
export interface LstmModelListResult {
  list: LstmModelListItem[];
  total: number;
}

/** LSTM 模型列表查询参数 */
export interface LstmModelListParams {
  keyword?: string;
  current?: number;
  pageSize?: number;
  sortBy?: 'createdAt' | 'epoch' | 'valLoss';
  sortOrder?: 'asc' | 'desc';
}

/**
 * 分页查询 LSTM 模型列表
 */
export async function getLstmModelList(params?: LstmModelListParams): Promise<LstmModelListResult> {
  const res = await request.get('/lstm/models', { params }) as ApiResponse<LstmModelListResult>;
  if (res?.success && res.data != null) {
    const { list = [], total = 0 } = res.data;
    return { list: Array.isArray(list) ? list : [], total: Number(total) };
  }
  throw new Error(res?.message ?? '获取模型列表失败');
}

/** 模型查询结果（收益金额与分数，不展示损失） */
export interface LstmModelResult {
  modelId: string;
  modelName: string;
  /** 收益金额（元） */
  profitAmount?: number | null;
  /** 效果分数 0～100 */
  score?: number | null;
}

/**
 * 查询指定模型的结果数据（收益金额与分数）
 */
export async function getLstmModelResult(id: string): Promise<LstmModelResult> {
  const res = await request.get(`/lstm/models/${encodeURIComponent(id)}/result`) as ApiResponse<LstmModelResult>;
  if (res?.success && res.data != null) return res.data;
  throw new Error(res?.message ?? '获取结果失败');
}
