import { request } from 'umi';
import type { AnalysisData } from './data';

// export async function fakeChartData(): Promise<{ data: AnalysisData }> {
//   return request('/api/foundTrading/analysis');
// }


export async function fakeChartData(
  params: {
    startDate: any,
    endDate: any,
  },
  // sort: any,
  options?: { [key: string]: any },
) {
  return request<any>('/api/tradingRecord/analysis', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}
