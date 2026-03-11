import request from '../utils/request';

/** 后端 ResponseDTO 泛型结构 */
interface ResponseDTO<T> {
  success: boolean;
  message?: string;
  data: T;
}

/** 分析页策略项，与后端 AnalysisStrategyItemDto 一致 */
export interface AnalysisStrategyItem {
  id: string;
  type: 'selection' | 'trading';
  nameKey: string;
  active: boolean;
  winRate: number;
  pnl: number;
  totalTrades: number;
}

/**
 * 策略配置（与后端 StrategyConfig 一致，供交易使用，持久化在 MySQL）
 */
export interface StrategyConfigDto {
  id?: number;
  configId?: string;
  version?: string;
  mode?: string;
  lstmWeight?: number;
  sentimentWeight?: number;
  topN?: number;
  minScore?: number;
  trailingStopWeight?: number;
  trailingStopTolerance?: number;
  rsiWeight?: number;
  rsiOverboughtThreshold?: number;
  volumeWeight?: number;
  volumeShrinkThreshold?: number;
  bollingerWeight?: number;
  bollingerBreakoutThreshold?: number;
  highReturnThreshold?: number;
  normalReturnThreshold?: number;
  lowReturnThreshold?: number;
  lossReturnThreshold?: number;
  indicatorEnabled?: Record<string, boolean>;
  consecutiveFailureThreshold?: number;
  dailyFailureThreshold?: number;
  circuitBreakerRecoveryMinutes?: number;
  enabled?: boolean;
  updateTime?: string;
  createTime?: string;
}

/**
 * 获取分析页策略列表（选股 + 交易）
 */
export async function getAnalysisList(): Promise<AnalysisStrategyItem[]> {
  const res = await request.get('/strategy/analysis/list') as ResponseDTO<AnalysisStrategyItem[]>;
  return Array.isArray(res?.data) ? res.data : [];
}

/**
 * 设置某策略的启用状态
 */
export async function setStrategyActive(id: string, active: boolean): Promise<string> {
  const res = await request.put(`/strategy/analysis/active/${id}`, { active }) as ResponseDTO<{ success: boolean; message?: string }>;
  return res?.message ?? 'OK';
}

/**
 * 获取当前策略配置（用于参数配置弹窗，无配置时后端返回默认值）
 */
export async function getStrategyConfig(): Promise<StrategyConfigDto> {
  const res = await request.get('/strategy/config') as ResponseDTO<StrategyConfigDto>;
  return (res && res.data) ? res.data : {};
}

/**
 * 更新策略配置（全量提交，交易时使用）
 */
export async function updateStrategyConfig(config: StrategyConfigDto): Promise<string> {
  const res = await request.put('/strategy/config', config) as ResponseDTO<string>;
  return res?.data ?? 'OK';
}
