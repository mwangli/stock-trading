// AI_GENERATE_START -------
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { formatDateTime } from '../utils/dateTime';
import axios from 'axios';
import {
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import {
  ClockCircleOutlined,
  ControlOutlined,
  HistoryOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  StopOutlined,
} from '@ant-design/icons';
import request from '../utils/request';
import { usePageHeader } from '../layouts/PageHeaderContext';

const { Text } = Typography;

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

interface ExecutionItem {
  runId: string;
  jobName: string;
  triggerSource: string;
  triggeredBy?: string;
  status: string;
  scheduledTime?: string;
  queuedTime?: string;
  startedTime?: string;
  finishedTime?: string;
  durationMs?: number;
  errorType?: string;
  errorMessage?: string;
  resultSummary?: string;
  hostName?: string;
  createdAt?: string;
}

interface JobItem {
  jobName: string;
  description?: string;
  cronExpression: string;
  enabled: boolean;
  taskStatus: string;
  latestRun?: ExecutionItem;
}

interface JobListResponse {
  items: JobItem[];
}

interface ExecutionPageResponse {
  items: ExecutionItem[];
  total: number;
  current: number;
  pageSize: number;
}

interface TriggerResponse {
  runId: string;
  message: string;
}

interface StopResponse {
  runId: string;
  status: string;
  interrupted: boolean;
  message: string;
}

const statusColors: Record<string, string> = {
  QUEUED: 'blue',
  RUNNING: 'processing',
  SUCCESS: 'green',
  REJECTED: 'orange',
  CANCELLED: 'default',
  FAILED: 'red',
  SKIPPED: 'default',
  PAUSED: 'orange',
  NORMAL: 'green',
  BLOCKED: 'orange',
  ERROR: 'red',
  ENABLED: 'green',
  STOPPED: 'default',
  NOT_REGISTERED: 'red',
};

const statusLabels: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '执行中',
  SUCCESS: '成功',
  FAILED: '失败',
  SKIPPED: '已跳过',
  REJECTED: '已拒绝',
  CANCELLED: '已取消',
  PAUSED: '已暂停',
  NORMAL: '正常',
  BLOCKED: '已阻塞',
  ERROR: '异常',
  ENABLED: '启用中',
  STOPPED: '已停止',
  NOT_REGISTERED: '未注册',
};

const sourceLabels: Record<string, string> = {
  SCHEDULED: '定时触发',
  MANUAL: '手动触发',
  RECOVERY: '恢复触发',
};


const formatDuration = (value?: number) => {
  if (value === undefined || value === null) {
    return '-';
  }
  return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(2)} s`;
};

const getErrorMessage = (cause: unknown, fallback: string) => {
  if (axios.isAxiosError<ApiResponse<unknown>>(cause)) {
    return cause.response?.data?.message || cause.message || fallback;
  }
  return cause instanceof Error ? cause.message : fallback;
};

const responseData = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (!response.success) {
    throw new Error(response.message || fallback);
  }
  return response.data;
};

const renderStatus = (status?: string) => {
  if (!status) {
    return <Text type="secondary">-</Text>;
  }
  return <Tag color={statusColors[status] ?? 'default'}>{statusLabels[status] ?? status}</Tag>;
};

const JobManagement: React.FC = () => {
  const [jobs, setJobs] = useState<JobItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [operatingJob, setOperatingJob] = useState<string | null>(null);
  const [operatingRun, setOperatingRun] = useState<string | null>(null);
  const [, setError] = useState<string | null>(null);

  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyJob, setHistoryJob] = useState<JobItem | null>(null);
  const [historyRuns, setHistoryRuns] = useState<ExecutionItem[]>([]);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [historyPage, setHistoryPage] = useState(1);
  const [historyPageSize] = useState(10);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyStatus, setHistoryStatus] = useState<string | undefined>();
  const [historySource, setHistorySource] = useState<string | undefined>();

  const [selectedRun, setSelectedRun] = useState<ExecutionItem | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [detailsLoading, setDetailsLoading] = useState(false);

  const loadJobs = useCallback(async () => {
    const response = await request.get('/jobs') as ApiResponse<JobListResponse>;
    const data = responseData(response, '读取任务状态失败');
    setJobs(data.items ?? []);
  }, []);

  const loadHistory = useCallback(async (jobName: string, current: number) => {
    setHistoryLoading(true);
    try {
      const response = await request.get('/job-runs', {
        params: {
          jobName,
          status: historyStatus,
          source: historySource,
          current,
          pageSize: historyPageSize,
        },
      }) as ApiResponse<ExecutionPageResponse>;
      const data = responseData(response, '读取任务执行历史失败');
      setHistoryRuns(data.items ?? []);
      setHistoryTotal(data.total);
      setHistoryPage(data.current);
    } catch (cause) {
      message.error(getErrorMessage(cause, '读取任务执行历史失败'));
    } finally {
      setHistoryLoading(false);
    }
  }, [historyPageSize, historySource, historyStatus]);

  const loadAll = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      await loadJobs();
      if (historyOpen && historyJob) {
        await loadHistory(historyJob.jobName, historyPage);
      }
    } catch (cause) {
      setError(getErrorMessage(cause, '读取任务管理数据失败'));
    } finally {
      setLoading(false);
    }
  }, [historyJob, historyOpen, historyPage, loadHistory, loadJobs]);

  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  const hasActiveHistoryRuns = useMemo(
    () => historyRuns.some((run) => run.status === 'QUEUED' || run.status === 'RUNNING'),
    [historyRuns],
  );

  const hasActiveJobs = useMemo(
    () => jobs.some((job) => job.taskStatus === 'QUEUED'
      || job.taskStatus === 'RUNNING'
      || job.latestRun?.status === 'QUEUED'
      || job.latestRun?.status === 'RUNNING'),
    [jobs],
  );

  useEffect(() => {
    if (!hasActiveJobs && (!historyOpen || !historyJob || !hasActiveHistoryRuns)) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      const refreshRequests: Promise<void>[] = [loadJobs()];
      if (historyOpen && historyJob) {
        refreshRequests.push(loadHistory(historyJob.jobName, historyPage));
      }
      void Promise.all(refreshRequests).catch((cause: unknown) => {
        setError(getErrorMessage(cause, '刷新任务执行状态失败'));
      });
    }, 3000);
    return () => window.clearInterval(timer);
  }, [hasActiveHistoryRuns, hasActiveJobs, historyJob, historyOpen, historyPage, loadHistory, loadJobs]);

  const refreshAfterOperation = async () => {
    await loadJobs();
    if (historyOpen && historyJob) {
      await loadHistory(historyJob.jobName, historyPage);
    }
  };

  const operateJob = async (job: JobItem, action: 'trigger' | 'pause' | 'resume') => {
    setOperatingJob(job.jobName);
    try {
      if (action === 'trigger') {
        const response = await request.post(`/jobs/trigger/${encodeURIComponent(job.jobName)}`, undefined, {
          params: { operator: 'web-console' },
        }) as ApiResponse<TriggerResponse>;
        const result = responseData(response, '提交手动任务失败');
        message.success(`${result.message}，运行 ID：${result.runId}`);
      } else {
        const endpoint = action === 'pause' ? 'pause' : 'resume';
        const response = await request.post(`/jobs/${endpoint}/${encodeURIComponent(job.jobName)}`) as ApiResponse<null>;
        responseData(response, action === 'pause' ? '暂停任务失败' : '恢复任务失败');
        message.success(action === 'pause' ? '任务已暂停' : '任务已启用');
      }
      await refreshAfterOperation();
    } catch (cause) {
      message.error(getErrorMessage(cause, '任务操作失败'));
    } finally {
      setOperatingJob(null);
    }
  };

  const confirmStopRun = (run: ExecutionItem) => {
    Modal.confirm({
      title: '停止任务执行',
      content: run.status === 'QUEUED'
        ? '该任务仍在排队，停止后将取消本次执行。'
        : '系统将发送线程中断请求。任务只能在业务代码响应中断后真正停止。',
      okText: '停止任务',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setOperatingRun(run.runId);
        try {
          const response = await request.post(`/job-runs/stop/${encodeURIComponent(run.runId)}`, undefined, {
            params: { operator: 'web-console' },
          }) as ApiResponse<StopResponse>;
          const result = responseData(response, '停止任务失败');
          message.success(result.message);
          await refreshAfterOperation();
        } catch (cause) {
          message.error(getErrorMessage(cause, '停止任务失败'));
        } finally {
          setOperatingRun(null);
        }
      },
    });
  };

  const openHistory = (job: JobItem) => {
    setHistoryJob(job);
    setHistoryOpen(true);
    setHistoryPage(1);
    setHistoryStatus(undefined);
    setHistorySource(undefined);
    void loadHistory(job.jobName, 1);
  };

  const showRunDetails = async (runId: string) => {
    setDetailsOpen(true);
    setDetailsLoading(true);
    setSelectedRun(null);
    try {
      const response = await request.get(`/job-runs/details/${encodeURIComponent(runId)}`) as ApiResponse<ExecutionItem>;
      setSelectedRun(responseData(response, '读取执行记录详情失败'));
    } catch (cause) {
      message.error(getErrorMessage(cause, '读取执行记录详情失败'));
      setDetailsOpen(false);
    } finally {
      setDetailsLoading(false);
    }
  };

  const historyColumns: ColumnsType<ExecutionItem> = [
    {
      title: '运行 ID',
      dataIndex: 'runId',
      key: 'runId',
      width: 230,
      ellipsis: true,
      render: (value: string) => (
        <Button type="link" className="!px-0" onClick={() => void showRunDetails(value)}>{value}</Button>
      ),
    },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100, render: renderStatus },
    { title: '触发来源', dataIndex: 'triggerSource', key: 'triggerSource', width: 110, render: (value: string) => sourceLabels[value] ?? value },
    { title: '操作人', dataIndex: 'triggeredBy', key: 'triggeredBy', width: 130, render: (value?: string) => value || '-' },
    { title: '开始时间', dataIndex: 'startedTime', key: 'startedTime', width: 180, render: formatDateTime },
    { title: '结束时间', dataIndex: 'finishedTime', key: 'finishedTime', width: 180, render: formatDateTime },
    { title: '耗时', dataIndex: 'durationMs', key: 'durationMs', width: 100, render: formatDuration },
    { title: '错误摘要', dataIndex: 'errorMessage', key: 'errorMessage', width: 220, ellipsis: true, render: (value?: string) => value || '-' },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 90,
      render: (_, record) => (
        record.status === 'QUEUED' || record.status === 'RUNNING'
          ? (
            <Button
              danger
              type="text"
              size="small"
              icon={<StopOutlined />}
              loading={operatingRun === record.runId}
              onClick={() => confirmStopRun(record)}
            >
              停止
            </Button>
          )
          : <Text type="secondary">-</Text>
      ),
    },
  ];

  const jobColumns: ColumnsType<JobItem> = [
    {
      title: '任务名称',
      dataIndex: 'jobName',
      key: 'jobName',
      width: 230,
      render: (value: string, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.description || value}</Text>
          <Text type="secondary" className="text-xs">{value}</Text>
        </Space>
      ),
    },
    { title: 'Cron', dataIndex: 'cronExpression', key: 'cronExpression', width: 170, render: (value: string) => <Text code>{value}</Text> },
    { title: '任务状态', dataIndex: 'taskStatus', key: 'taskStatus', width: 120, render: renderStatus },
    { title: '最近执行', key: 'latestRun', width: 150, render: (_, record) => record.latestRun ? renderStatus(record.latestRun.status) : <Text type="secondary">暂无记录</Text> },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 390,
      render: (_, record) => {
        const operating = operatingJob === record.jobName;
        const activeRun = record.latestRun
          && (record.latestRun.status === 'QUEUED' || record.latestRun.status === 'RUNNING')
          ? record.latestRun
          : undefined;
        const triggerDisabled = !record.enabled
          || record.taskStatus === 'NOT_REGISTERED'
          || record.taskStatus === 'QUEUED'
          || record.taskStatus === 'RUNNING'
          || operating;
        return (
          <Space size="small">
            {record.enabled && (
              <Tooltip title={triggerDisabled ? '任务需已注册、启用且当前没有排队或执行中的实例' : '立即提交一次手动执行'}>
                <span>
                  <Button
                    type="primary"
                    size="small"
                    icon={<PlayCircleOutlined />}
                    disabled={triggerDisabled}
                    loading={operating}
                    onClick={() => void operateJob(record, 'trigger')}
                  >
                    触发
                  </Button>
                </span>
              </Tooltip>
            )}
            {record.enabled ? (
              <Button size="small" icon={<PauseCircleOutlined />} disabled={operating} onClick={() => void operateJob(record, 'pause')}>
                暂停
              </Button>
            ) : (
              <Button type="primary" size="small" icon={<PlayCircleOutlined />} disabled={operating} loading={operating} onClick={() => void operateJob(record, 'resume')}>
                启用
              </Button>
            )}
            {activeRun && (
              <Button
                danger
                size="small"
                icon={<StopOutlined />}
                loading={operatingRun === activeRun.runId}
                onClick={() => confirmStopRun(activeRun)}
              >
                停止
              </Button>
            )}
            <Button type="text" size="small" icon={<HistoryOutlined />} onClick={() => openHistory(record)}>历史</Button>
          </Space>
        );
      },
    },
  ];

  const handleHistoryTableChange = (pagination: TablePaginationConfig) => {
    const current = pagination.current ?? 1;
    setHistoryPage(current);
    if (historyJob) {
      void loadHistory(historyJob.jobName, current);
    }
  };

  const applyHistoryFilter = () => {
    if (historyJob) {
      setHistoryPage(1);
      void loadHistory(historyJob.jobName, 1);
    }
  };

  const headerActions = useMemo(() => (
    <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadAll()}>刷新</Button>
  ), [loadAll, loading]);

  usePageHeader('查询任务运行状态，手动触发或停止任务并按任务追踪执行历史', headerActions);

  return (
    <div className="app-page">
      <Card className="surface-card" title={<Space><ControlOutlined />任务状态</Space>}>
        <Table<JobItem>
          rowKey="jobName"
          columns={jobColumns}
          dataSource={jobs}
          loading={loading}
          locale={{ emptyText: <Empty description="暂无任务配置" /> }}
          scroll={{ x: 1200 }}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 个任务`,
          }}
        />
      </Card>

      <Modal
        title={historyJob ? `执行历史：${historyJob.description || historyJob.jobName}` : '执行历史'}
        width="80vw"
        centered
        open={historyOpen}
        onCancel={() => setHistoryOpen(false)}
      >
        <div className="max-h-[calc(80vh-150px)] overflow-y-auto">
          <Space wrap className="mb-4">
            <Select
              allowClear
              className="w-36"
              placeholder="按状态筛选"
              value={historyStatus}
              options={Object.entries(statusLabels).map(([value, label]) => ({ label, value }))}
              onChange={(value: string | undefined) => setHistoryStatus(value)}
            />
            <Select
              allowClear
              className="w-36"
              placeholder="按来源筛选"
              value={historySource}
              options={Object.entries(sourceLabels).map(([value, label]) => ({ label, value }))}
              onChange={(value: string | undefined) => setHistorySource(value)}
            />
            <Button icon={<ClockCircleOutlined />} onClick={applyHistoryFilter}>查询</Button>
          </Space>
          <Table<ExecutionItem>
            rowKey="runId"
            columns={historyColumns}
            dataSource={historyRuns}
            loading={historyLoading}
            locale={{ emptyText: <Empty description="该任务暂无执行记录" /> }}
            scroll={{ x: 1360, y: 'calc(80vh - 270px)' }}
            pagination={{
              current: historyPage,
              pageSize: historyPageSize,
              total: historyTotal,
              showSizeChanger: false,
              showTotal: (total) => `共 ${total} 条记录`,
            }}
            onChange={handleHistoryTableChange}
          />
        </div>
      </Modal>

      <Drawer title="执行记录详情" width={680} open={detailsOpen} loading={detailsLoading} onClose={() => setDetailsOpen(false)}>
        {selectedRun && (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="运行 ID">{selectedRun.runId}</Descriptions.Item>
            <Descriptions.Item label="任务名称">{selectedRun.jobName}</Descriptions.Item>
            <Descriptions.Item label="运行状态">{renderStatus(selectedRun.status)}</Descriptions.Item>
            <Descriptions.Item label="触发来源">{sourceLabels[selectedRun.triggerSource] ?? selectedRun.triggerSource}</Descriptions.Item>
            <Descriptions.Item label="操作人">{selectedRun.triggeredBy || '-'}</Descriptions.Item>
            <Descriptions.Item label="计划时间">{formatDateTime(selectedRun.scheduledTime)}</Descriptions.Item>
            <Descriptions.Item label="排队时间">{formatDateTime(selectedRun.queuedTime)}</Descriptions.Item>
            <Descriptions.Item label="开始时间">{formatDateTime(selectedRun.startedTime)}</Descriptions.Item>
            <Descriptions.Item label="结束时间">{formatDateTime(selectedRun.finishedTime)}</Descriptions.Item>
            <Descriptions.Item label="执行耗时">{formatDuration(selectedRun.durationMs)}</Descriptions.Item>
            <Descriptions.Item label="执行节点">{selectedRun.hostName || '-'}</Descriptions.Item>
            <Descriptions.Item label="结果摘要">{selectedRun.resultSummary || '-'}</Descriptions.Item>
            <Descriptions.Item label="异常类型">{selectedRun.errorType || '-'}</Descriptions.Item>
            <Descriptions.Item label="错误信息">{selectedRun.errorMessage || '-'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{formatDateTime(selectedRun.createdAt)}</Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  );
};

export default JobManagement;
// AI_GENERATE_END -------
