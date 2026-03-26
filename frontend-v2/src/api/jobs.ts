import request from '../utils/request';

export interface Job {
  id: number;
  jobName: string;
  description: string;
  cronExpression: string;
  lastRunTime: string | null;
  status: number;
}

/**
 * 获取任务列表
 */
export async function getJobList(): Promise<Job[]> {
  return await request.get('/jobs') as unknown as Job[];
}

/**
 * 手动运行任务
 */
export async function runJob(id: number): Promise<void> {
  await request.post(`/jobs/${id}/run`);
}

/**
 * 切换任务状态
 */
export async function toggleJobStatus(id: number, active: boolean): Promise<void> {
  await request.post(`/jobs/${id}/status`, { active });
}

/**
 * 更新任务Cron表达式
 */
export async function updateJobCron(id: number, cron: string): Promise<void> {
  await request.post(`/jobs/${id}/cron`, { cron });
}
