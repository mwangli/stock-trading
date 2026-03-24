import request from '../utils/request';

/** 后端 ApiResponse 结构 */
interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

/** LSTM 模型列表项，现由后端 ModelTrainingRecordDto 提供 */
export interface LstmModelListItem {
  /** 训练记录主键 ID */
  id: number;
  /** 股票代码（兼容原 modelName 字段） */
  stockCode: string;
  /** 股票名称（兼容原 name 字段） */
  stockName: string;
  /** 是否已训练（Mongo 中存在对应模型） */
  trained: boolean;
  /** 是否处于训练中状态 */
  training: boolean;
  /** 最近一次训练完成时间 */
  lastTrainTime?: string;
  /** 最近一次训练耗时（秒） */
  lastDurationSeconds?: number | null;
  /** 最近一次训练轮次（兼容原 epoch） */
  lastEpochs?: number | null;
  /** 最近一次训练集损失 */
  lastTrainLoss?: number | null;
  /** 最近一次验证集损失 */
  lastValLoss?: number | null;
  /** 最近一次训练对应 Mongo 模型 ID */
  lastModelId?: string | null;
  /** 记录创建时间 */
  createdAt: string;
  /** 记录最近更新时间 */
  updatedAt: string;
  /** 兼容旧字段：模型名称 = 股票代码 */
  modelName: string;
  /** 兼容旧字段：展示名称 = 股票名称 */
  name: string;
  /** 兼容旧字段：训练轮次 */
  epoch: number;
  /** 兼容旧字段：模型版本，暂无则为 null */
  modelVersion: string | null;
  /** 收益金额（元），暂无时为 null */
  profitAmount?: number | null;
  /** 效果分数 0～100，由验证集推导，当前为 null 默认占位 */
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
 * 分页查询 LSTM 模型训练记录列表
 */
export async function getLstmModelList(params?: LstmModelListParams): Promise<LstmModelListResult> {
  const res = await request.get('/models/training-records', { params }) as ApiResponse<LstmModelListResult>;
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
