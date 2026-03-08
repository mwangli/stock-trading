import request from '../utils/request';

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
export function getAnalysisList(): Promise<AnalysisStrategyItem[]> {
  return request.get('/strategy/analysis/list') as Promise<AnalysisStrategyItem[]>;
}

/**
 * 设置某策略的启用状态
 */
export function setStrategyActive(id: string, active: boolean): Promise<string> {
  return request.put(`/strategy/analysis/${id}/active`, { active }) as Promise<string>;
}

/**
 * 获取当前策略配置（用于参数配置弹窗，无配置时后端返回默认值）
 */
export function getStrategyConfig(): Promise<StrategyConfigDto> {
  return request.get('/strategy/config') as Promise<StrategyConfigDto>;
}

/**
 * 更新策略配置（全量提交，交易时使用）
 */
export function updateStrategyConfig(config: StrategyConfigDto): Promise<string> {
  return request.put('/strategy/config', config) as Promise<string>;
}
