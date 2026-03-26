import request from '../utils/request';

export interface StockInfo {
  code: string;
  name: string;
  price: number;
  changePercent: number;
  totalMarketValue: number;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  total?: number;
}

export interface MarketStats {
  marketStatus?: string;
  changePercent?: number;
  upCount?: number;
  downCount?: number;
  flatCount?: number;
  totalAmount?: number;
  totalVolume?: number;
  topGainerCode?: string | null;
  topGainerName?: string | null;
  topGainerChange?: number | null;
  topLoserCode?: string | null;
  topLoserName?: string | null;
  topLoserChange?: number | null;
  totalCount?: number;
  avgTurnoverRate?: number;
}

export interface KlineData {
  dates: string[];
  kline: number[][];
  volumes: number[];
}

export interface TopGainerItem {
  code: string;
  name: string;
  changePercent: number;
}

/**
 * 分页查询股票列表
 */
export async function getStockList(params: {
  current: number;
  pageSize: number;
  keywords?: string;
}): Promise<ApiResponse<StockInfo[]> & { total?: number }> {
  return await request.get('/stock-info/list', { params }) as unknown as ApiResponse<StockInfo[]> & { total?: number };
}

/**
 * 获取市场统计信息
 */
export async function getMarketStats(): Promise<ApiResponse<MarketStats>> {
  return await request.get('/stock-info/marketStats') as ApiResponse<MarketStats>;
}

/**
 * 获取涨幅榜TOP10
 */
export async function getTopGainers(): Promise<{ success: boolean; data: TopGainerItem[] }> {
  return await request.get('/stock-info/listIncreaseRate') as { success: boolean; data: TopGainerItem[] };
}

/**
 * 获取K线数据
 */
export async function getKlineData(
  code: string,
  type: 'daily' | 'weekly' | 'monthly',
  timeRange: string
): Promise<{ success: boolean; data: KlineData; total: number }> {
  return await request.get(`/stock-data/kline/${code}`, {
    params: { type, timeRange }
  }) as { success: boolean; data: KlineData; total: number };
}
