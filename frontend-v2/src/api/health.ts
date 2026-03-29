import request from '../utils/request';

export interface HealthStatus {
  status: string;
  timestamp: number;
}

export async function checkHealth(): Promise<HealthStatus> {
  return await request.get('/browser/debug/health');
}