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
