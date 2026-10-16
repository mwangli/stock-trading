// AI_GENERATE_START --------
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { formatDateTime } from '../utils/dateTime';
import {
  Alert,
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
  Tabs,
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
import { getApiErrorMessage, readApiData, type ApiResponse } from '../utils/api';
import { usePageHeader } from '../layouts/PageHeaderContext';
import { useTranslation } from 'react-i18next';
import { localizedText } from '../utils/localizedText';

const { Text } = Typography;

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
  module?: JobDomain;
  syncMode?: string;
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

const sourceLabelsEn: Record<string, string> = {
  SCHEDULED: 'Scheduled',
  MANUAL: 'Manual',
  RECOVERY: 'Recovery',
};

const statusLabelsEn: Record<string, string> = {
  QUEUED: 'Queued', RUNNING: 'Running', SUCCESS: 'Success', FAILED: 'Failed', SKIPPED: 'Skipped',
  REJECTED: 'Rejected', CANCELLED: 'Cancelled', PAUSED: 'Paused', NORMAL: 'Normal', BLOCKED: 'Blocked',
  ERROR: 'Error', ENABLED: 'Enabled', STOPPED: 'Stopped', NOT_REGISTERED: 'Not Registered',
};

const formatDuration = (value?: number) => {
  if (value === undefined || value === null) {
    return '-';
  }
  return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(2)} s`;
};

type JobDomain = 'data' | 'model' | 'strategy' | 'trading' | 'maintenance';

const jobDomains: Array<{ key: JobDomain; label: string }> = [
  { key: 'data', label: '数据采集' },
  { key: 'model', label: '模型任务' },
  { key: 'strategy', label: '策略分析' },
  { key: 'trading', label: '交易执行' },
  { key: 'maintenance', label: '系统维护' },
];

const jobDomainLabelsEn: Record<JobDomain, string> = {
  data: 'Data Collection',
  model: 'Model Tasks',
  strategy: 'Strategy Analysis',
  trading: 'Trading Execution',
  maintenance: 'System Maintenance',
};

const resolveJobDomain = (jobName: string, module?: JobDomain): JobDomain => {
  if (module && jobDomains.some((domain) => domain.key === module)) {
    return module;
  }
  const normalized = jobName.toLowerCase();
  if (normalized.includes('lstm') || normalized.includes('modelprediction') || normalized.includes('sentiment')) return 'model';
  if (normalized.includes('strategy') || normalized.includes('selection') || normalized.includes('backtest')) return 'strategy';
  if (normalized.includes('trading') || normalized.includes('broker') || normalized.includes('performance')) return 'trading';
  if (normalized.includes('sync') || normalized.includes('news') || normalized.includes('calendar') || normalized.includes('stockdata')) return 'data';
  return 'maintenance';
};

const renderStatus = (status?: string) => {
  if (!status) {
    return <Text type="secondary">-</Text>;
  }
  return <Tag color={statusColors[status] ?? 'default'}>{localizedText(statusLabels[status] ?? status, statusLabelsEn[status] ?? status)}</Tag>;
};

const JobManagement: React.FC = () => {
  const { t } = useTranslation();
  const [jobs, setJobs] = useState<JobItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [operatingJob, setOperatingJob] = useState<string | null>(null);
  const [operatingRun, setOperatingRun] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

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
    const data = readApiData(response, '读取任务状态失败');
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
      const data = readApiData(response, '读取任务执行历史失败');
      setHistoryRuns(data.items ?? []);
      setHistoryTotal(data.total);
      setHistoryPage(data.current);
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '读取任务执行历史失败'));
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
      setError(getApiErrorMessage(cause, '读取任务管理数据失败'));
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
        setError(getApiErrorMessage(cause, '刷新任务执行状态失败'));
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
        const result = readApiData(response, '提交手动任务失败');
        message.success(`${result.message}, ${localizedText('运行 ID', 'Run ID')}: ${result.runId}`);
      } else {
        const endpoint = action === 'pause' ? 'pause' : 'resume';
        const response = await request.post(`/jobs/${endpoint}/${encodeURIComponent(job.jobName)}`) as ApiResponse<null>;
        readApiData(response, action === 'pause' ? '暂停任务失败' : '恢复任务失败');
        message.success(action === 'pause' ? localizedText('任务已暂停', 'Job paused') : localizedText('任务已启用', 'Job enabled'));
      }
      await refreshAfterOperation();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '任务操作失败'));
    } finally {
      setOperatingJob(null);
    }
  };

  const confirmStopRun = (run: ExecutionItem) => {
    Modal.confirm({
      title: localizedText('停止任务执行', 'Stop Job Run'),
      content: run.status === 'QUEUED'
        ? localizedText('该任务仍在排队，停止后将取消本次执行。', 'The job is queued and this run will be cancelled.')
        : localizedText('系统将发送线程中断请求。任务只能在业务代码响应中断后真正停止。', 'The system will request a thread interrupt; the job stops only after business code responds.'),
      okText: localizedText('停止任务', 'Stop Run'),
      cancelText: localizedText('取消', 'Cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        setOperatingRun(run.runId);
        try {
          const response = await request.post(`/job-runs/stop/${encodeURIComponent(run.runId)}`, undefined, {
            params: { operator: 'web-console' },
          }) as ApiResponse<StopResponse>;
          const result = readApiData(response, '停止任务失败');
          message.success(result.message);
          await refreshAfterOperation();
        } catch (cause) {
          message.error(getApiErrorMessage(cause, '停止任务失败'));
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
      setSelectedRun(readApiData(response, '读取执行记录详情失败'));
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '读取执行记录详情失败'));
      setDetailsOpen(false);
    } finally {
      setDetailsLoading(false);
    }
  };

  const historyColumns: ColumnsType<ExecutionItem> = [
    {
      title: localizedText('运行 ID', 'Run ID'),
      dataIndex: 'runId',
      key: 'runId',
      width: 230,
      ellipsis: true,
      render: (value: string) => (
        <Button type="link" className="!px-0" onClick={() => void showRunDetails(value)}>{value}</Button>
      ),
    },
    { title: t('common.status'), dataIndex: 'status', key: 'status', width: 100, render: renderStatus },
    { title: localizedText('触发来源', 'Trigger Source'), dataIndex: 'triggerSource', key: 'triggerSource', width: 110, render: (value: string) => localizedText(sourceLabels[value] ?? value, sourceLabelsEn[value] ?? value) },
    { title: localizedText('操作人', 'Operator'), dataIndex: 'triggeredBy', key: 'triggeredBy', width: 130, render: (value?: string) => value || '-' },
    { title: localizedText('开始时间', 'Started At'), dataIndex: 'startedTime', key: 'startedTime', width: 180, render: formatDateTime },
    { title: localizedText('结束时间', 'Finished At'), dataIndex: 'finishedTime', key: 'finishedTime', width: 180, render: formatDateTime },
    { title: localizedText('耗时', 'Duration'), dataIndex: 'durationMs', key: 'durationMs', width: 100, render: formatDuration },
    { title: localizedText('错误摘要', 'Error Summary'), dataIndex: 'errorMessage', key: 'errorMessage', width: 220, ellipsis: true, render: (value?: string) => value || '-' },
    {
      title: localizedText('操作', 'Actions'),
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
              {localizedText('停止', 'Stop')}
            </Button>
          )
          : <Text type="secondary">-</Text>
      ),
    },
  ];

  const jobColumns: ColumnsType<JobItem> = [
    {
      title: localizedText('任务名称', 'Job Name'),
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
    { title: localizedText('任务状态', 'Job Status'), dataIndex: 'taskStatus', key: 'taskStatus', width: 120, render: renderStatus },
    {
      title: localizedText('最近执行', 'Latest Run'),
      key: 'latestRun',
      width: 190,
      render: (_, record) => record.latestRun ? (
        <Space direction="vertical" size={0}>
          {renderStatus(record.latestRun.status)}
          <Text type="secondary">{formatDateTime(record.latestRun.finishedTime ?? record.latestRun.createdAt)}</Text>
        </Space>
      ) : <Text type="secondary">{localizedText('暂无记录', 'No record')}</Text>,
    },
    {
      title: localizedText('操作', 'Actions'),
      key: 'actions',
      fixed: 'right',
      width: 390,
      render: (_, record) => {
        const operating = operatingJob === record.jobName;
        const activeRun = record.latestRun
          && (record.latestRun.status === 'QUEUED' || record.latestRun.status === 'RUNNING')
          ? record.latestRun
          : undefined;
        return (
          <Space size="small">
            {record.enabled && (
              <Tooltip title={localizedText('立即提交一次手动执行；重复执行由后端返回明确错误', 'Submit one manual run now; duplicate runs are rejected by the backend')}>
                <span>
                  <Button
                    type="primary"
                    size="small"
                    icon={<PlayCircleOutlined />}
                    loading={operating}
                    onClick={() => void operateJob(record, 'trigger')}
                  >
                    {localizedText('触发', 'Trigger')}
                  </Button>
                </span>
              </Tooltip>
            )}
            {record.enabled ? (
              <Button size="small" icon={<PauseCircleOutlined />} loading={operating} onClick={() => void operateJob(record, 'pause')}>
                {localizedText('暂停', 'Pause')}
              </Button>
            ) : (
              <Button type="primary" size="small" icon={<PlayCircleOutlined />} loading={operating} onClick={() => void operateJob(record, 'resume')}>
                {localizedText('启用', 'Enable')}
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
                {localizedText('停止', 'Stop')}
              </Button>
            )}
            <Button type="text" size="small" icon={<HistoryOutlined />} onClick={() => openHistory(record)}>{localizedText('历史', 'History')}</Button>
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

  usePageHeader(t('jobsPage.subtitle'));

  return (
    <div className="app-page">
      {error && <Alert className="mb-4" type="error" showIcon message={localizedText('任务管理数据读取失败', 'Failed to load job management data')} description={error} closable onClose={() => setError(null)} />}
      <Card
        className="surface-card"
        title={<Space><ControlOutlined />{t('jobsPage.status')}</Space>}
        extra={<Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadAll()}>{t('jobsPage.refresh')}</Button>}
      >
        <Tabs
          items={jobDomains.map((domain) => {
          const domainJobs = jobs.filter((job) => resolveJobDomain(job.jobName, job.module) === domain.key);
            return {
              key: domain.key,
              label: `${localizedText(domain.label, jobDomainLabelsEn[domain.key])} (${domainJobs.length})`,
              children: (
                <Table<JobItem>
                  rowKey="jobName"
                  columns={jobColumns}
                  dataSource={domainJobs}
                  loading={loading}
                  locale={{ emptyText: <Empty description={t('jobsPage.emptyGroup')} /> }}
                  scroll={{ x: 1200 }}
                  pagination={{
                    pageSize: 10,
                    showSizeChanger: true,
                    showTotal: (total) => localizedText(`共 ${total} 个任务`, `${total} jobs`),
                  }}
                />
              ),
            };
          })}
        />
      </Card>

      <Modal
        title={historyJob ? `${t('jobsPage.history')}: ${historyJob.description || historyJob.jobName}` : t('jobsPage.history')}
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
              placeholder={localizedText('按状态筛选', 'Filter by status')}
              value={historyStatus}
              options={Object.entries(statusLabels).map(([value, label]) => ({ label, value }))}
              onChange={(value: string | undefined) => setHistoryStatus(value)}
            />
            <Select
              allowClear
              className="w-36"
              placeholder={localizedText('按来源筛选', 'Filter by source')}
              value={historySource}
              options={Object.entries(sourceLabels).map(([value, label]) => ({ label, value }))}
              onChange={(value: string | undefined) => setHistorySource(value)}
            />
            <Button icon={<ClockCircleOutlined />} onClick={applyHistoryFilter}>{localizedText('查询', 'Query')}</Button>
          </Space>
          <Table<ExecutionItem>
            rowKey="runId"
            columns={historyColumns}
            dataSource={historyRuns}
            loading={historyLoading}
          locale={{ emptyText: <Empty description={t('jobsPage.emptyRuns')} /> }}
            scroll={{ x: 1360, y: 'calc(80vh - 270px)' }}
            pagination={{
              current: historyPage,
              pageSize: historyPageSize,
              total: historyTotal,
              showSizeChanger: false,
              showTotal: (total) => localizedText(`共 ${total} 条记录`, `${total} records`),
            }}
            onChange={handleHistoryTableChange}
          />
        </div>
      </Modal>

      <Drawer title={t('jobsPage.details')} width={680} open={detailsOpen} loading={detailsLoading} onClose={() => setDetailsOpen(false)}>
        {selectedRun && (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label={localizedText('运行 ID', 'Run ID')}>{selectedRun.runId}</Descriptions.Item>
            <Descriptions.Item label={localizedText('任务名称', 'Job Name')}>{selectedRun.jobName}</Descriptions.Item>
            <Descriptions.Item label={localizedText('运行状态', 'Run Status')}>{renderStatus(selectedRun.status)}</Descriptions.Item>
            <Descriptions.Item label={localizedText('触发来源', 'Trigger Source')}>{localizedText(sourceLabels[selectedRun.triggerSource] ?? selectedRun.triggerSource, sourceLabelsEn[selectedRun.triggerSource] ?? selectedRun.triggerSource)}</Descriptions.Item>
            <Descriptions.Item label={localizedText('操作人', 'Operator')}>{selectedRun.triggeredBy || '-'}</Descriptions.Item>
            <Descriptions.Item label={localizedText('计划时间', 'Scheduled At')}>{formatDateTime(selectedRun.scheduledTime)}</Descriptions.Item>
            <Descriptions.Item label={localizedText('排队时间', 'Queued At')}>{formatDateTime(selectedRun.queuedTime)}</Descriptions.Item>
            <Descriptions.Item label={localizedText('开始时间', 'Started At')}>{formatDateTime(selectedRun.startedTime)}</Descriptions.Item>
            <Descriptions.Item label={localizedText('结束时间', 'Finished At')}>{formatDateTime(selectedRun.finishedTime)}</Descriptions.Item>
            <Descriptions.Item label={localizedText('执行耗时', 'Duration')}>{formatDuration(selectedRun.durationMs)}</Descriptions.Item>
            <Descriptions.Item label={localizedText('执行节点', 'Execution Node')}>{selectedRun.hostName || '-'}</Descriptions.Item>
            <Descriptions.Item label={localizedText('结果摘要', 'Result Summary')}>{selectedRun.resultSummary || '-'}</Descriptions.Item>
            <Descriptions.Item label={localizedText('异常类型', 'Error Type')}>{selectedRun.errorType || '-'}</Descriptions.Item>
            <Descriptions.Item label={localizedText('错误信息', 'Error Message')}>{selectedRun.errorMessage || '-'}</Descriptions.Item>
            <Descriptions.Item label={localizedText('创建时间', 'Created At')}>{formatDateTime(selectedRun.createdAt)}</Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  );
};

export default JobManagement;
// AI_GENERATE_END --------
