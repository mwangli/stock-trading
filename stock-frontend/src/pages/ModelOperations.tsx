// AI_GENERATE_START --------------
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { formatDateTime } from '../utils/dateTime';
import axios from 'axios';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Progress,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CheckCircleOutlined,
  CloudServerOutlined,
  ExperimentOutlined,
  ReloadOutlined,
  RollbackOutlined,
  SafetyCertificateOutlined,
  StopOutlined,
} from '@ant-design/icons';
import request from '../utils/request';
import { usePageHeader } from '../layouts/PageHeaderContext';

const { Title, Text, Paragraph } = Typography;

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

interface ModelVersion {
  id: string;
  modelName: string;
  modelVersion: string;
  parentModelVersionId?: string;
  status: string;
  featureVersion?: string;
  labelVersion?: string;
  engineName?: string;
  engineVersion?: string;
  djlVersion?: string;
  parameterSha256?: string;
  parameterSize: number;
  epoch: number;
  trainLoss?: number;
  valLoss?: number;
  trainingConfigJson?: string;
  inputContractJson?: string;
  metricsJson?: string;
  createdAt?: string;
  active: boolean;
  previous: boolean;
}

interface TrainingRun {
  id: string;
  modelName: string;
  status: string;
  triggerSource: string;
  triggeredBy: string;
  requestedConfigJson?: string;
  effectiveConfigJson?: string;
  gateResultJson?: string;
  startedAt?: string;
  finishedAt?: string;
  candidateModelVersionId?: string;
  datasetSnapshotId?: string;
  resumedFromRunId?: string;
  errorMessage?: string;
  trainedStocks?: number;
  progressStage?: string;
  progressMessage?: string;
  progressPercent?: number;
  currentEpoch?: number;
  totalEpochs?: number;
  elapsedSeconds?: number;
  estimatedRemainingSeconds?: number;
  lastProgressAt?: string;
  trainSamples?: number;
  valSamples?: number;
  trainLoss?: number;
  valLoss?: number;
  createdAt?: string;
}

interface Overview {
  modelName: string;
  activeVersion?: ModelVersion;
  previousVersion?: ModelVersion;
  latestTrainingRun?: TrainingRun;
  trainingEnabled: boolean;
  trainingAllowed: boolean;
  trainingRunning: boolean;
  tradingTime: boolean;
  activeModelHealthy: boolean;
  gateMessage: string;
}

interface PageResponse<T> {
  items: T[];
  total: number;
  current: number;
  pageSize: number;
}

interface StartTrainingRequest {
  days?: number;
  epochs?: number;
  batchSize?: number;
  learningRate?: number;
  operatorName?: string;
  resumeFromRunId?: string;
}

interface OperationResponse {
  operationType: string;
  operationId: string;
  message: string;
}

const defaultTrainingValues: StartTrainingRequest = {
  days: 1000,
  epochs: 100,
  batchSize: 32,
  learningRate: 0.001,
};

const statusColors: Record<string, string> = {
  ACTIVE: 'green',
  READY: 'cyan',
  CANDIDATE: 'gold',
  ARCHIVED: 'default',
  QUEUED: 'blue',
  RUNNING: 'processing',
  SUCCEEDED: 'green',
  FAILED: 'red',
  STOPPING: 'orange',
  CANCELLED: 'default',
};


const formatLoss = (value?: number) => value === undefined || value === null
  ? '-'
  : value.toFixed(6);

const formatBytes = (value: number) => {
  if (!value) {
    return '-';
  }
  return `${(value / 1024 / 1024).toFixed(2)} MB`;
};

const progressStageLabels: Record<string, string> = {
  QUEUED: '排队中',
  PREPARING_DATA: '准备数据',
  PROCESSING_SAMPLES: '构建样本',
  TRAINING: '模型训练',
  SAVING_MODEL: '保存模型',
  VALIDATING_MODEL: '候选校验',
  COMPLETED: '已完成',
  FAILED: '已失败',
};

const formatDuration = (seconds?: number) => {
  if (seconds === undefined || seconds === null || seconds < 0) {
    return '-';
  }
  if (seconds < 60) {
    return `${seconds} 秒`;
  }
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainingSeconds = seconds % 60;
  if (hours > 0) {
    return `${hours} 小时 ${minutes} 分`;
  }
  return `${minutes} 分 ${remainingSeconds} 秒`;
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

/**
 * 模型版本、训练和生产切换运维页面。
 */
const ModelOperations: React.FC = () => {
  const [form] = Form.useForm<StartTrainingRequest>();
  const [overview, setOverview] = useState<Overview | null>(null);
  const [versions, setVersions] = useState<ModelVersion[]>([]);
  const [trainingRuns, setTrainingRuns] = useState<TrainingRun[]>([]);
  const [versionTotal, setVersionTotal] = useState(0);
  const [runTotal, setRunTotal] = useState(0);
  const [versionPage, setVersionPage] = useState(1);
  const [runPage, setRunPage] = useState(1);
  const [pageSize] = useState(10);
  const [operatorName, setOperatorName] = useState('web-console');
  const [loading, setLoading] = useState(false);
  const [operating, setOperating] = useState(false);
  const [trainingModalOpen, setTrainingModalOpen] = useState(false);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [selectedVersion, setSelectedVersion] = useState<ModelVersion | null>(null);
  const [progressClock, setProgressClock] = useState(() => Date.now());

  const loadOverview = useCallback(async () => {
    const response = await request.get('/model-operations/overview') as ApiResponse<Overview>;
    setOverview(responseData(response, '读取模型运维概览失败'));
  }, []);

  const loadVersions = useCallback(async (current: number) => {
    const response = await request.get('/model-versions', {
      params: { current, pageSize },
    }) as ApiResponse<PageResponse<ModelVersion>>;
    const page = responseData(response, '读取模型版本失败');
    setVersions(page.items ?? []);
    setVersionTotal(page.total);
    setVersionPage(page.current);
  }, [pageSize]);

  const loadTrainingRuns = useCallback(async (current: number) => {
    const response = await request.get('/model-training-runs', {
      params: { current, pageSize },
    }) as ApiResponse<PageResponse<TrainingRun>>;
    const page = responseData(response, '读取训练记录失败');
    setTrainingRuns(page.items ?? []);
    setRunTotal(page.total);
    setRunPage(page.current);
  }, [pageSize]);

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      await Promise.all([
        loadOverview(),
        loadVersions(versionPage),
        loadTrainingRuns(runPage),
      ]);
    } catch (cause) {
      message.error(getErrorMessage(cause, '读取模型运维数据失败'));
    } finally {
      setLoading(false);
    }
  }, [loadOverview, loadTrainingRuns, loadVersions, runPage, versionPage]);

  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  useEffect(() => {
    if (!overview?.trainingRunning) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void Promise.all([loadOverview(), loadTrainingRuns(runPage)]).catch((cause) => {
        message.error(getErrorMessage(cause, '刷新训练进度失败'));
      });
    }, 3000);
    return () => window.clearInterval(timer);
  }, [loadOverview, loadTrainingRuns, overview?.trainingRunning, runPage]);

  useEffect(() => {
    if (!overview?.trainingRunning) {
      return undefined;
    }
    const timer = window.setInterval(() => setProgressClock(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [overview?.trainingRunning]);

  const refreshAfterOperation = async () => {
    await Promise.all([loadOverview(), loadVersions(1), loadTrainingRuns(1)]);
  };

  const submitTraining = async () => {
    const values = await form.validateFields();
    setOperating(true);
    try {
      const response = await request.post('/model-training-runs/start', {
        ...values,
        operatorName: values.operatorName?.trim() || operatorName.trim() || 'web-console',
      }) as ApiResponse<OperationResponse>;
      const result = responseData(response, '提交训练失败');
      message.success(result.message);
      setTrainingModalOpen(false);
      form.resetFields();
      await refreshAfterOperation();
    } catch (cause) {
      message.error(getErrorMessage(cause, '提交训练失败'));
    } finally {
      setOperating(false);
    }
  };

  const stopTraining = async (runId: string) => {
    setOperating(true);
    try {
      const response = await request.post(`/model-training-runs/stop/${runId}`, undefined, {
        params: { operatorName: operatorName.trim() || 'web-console' },
      }) as ApiResponse<OperationResponse>;
      const result = responseData(response, '停止训练失败');
      message.success(result.message);
      await refreshAfterOperation();
    } catch (cause) {
      message.error(getErrorMessage(cause, '停止训练失败'));
    } finally {
      setOperating(false);
    }
  };

  const activateVersion = async (versionId: string) => {
    setOperating(true);
    try {
      const response = await request.post(`/model-versions/activate/${versionId}`, undefined, {
        params: { operatorName: operatorName.trim() || 'web-console' },
      }) as ApiResponse<OperationResponse>;
      const result = responseData(response, '激活模型版本失败');
      message.success(result.message);
      await refreshAfterOperation();
    } catch (cause) {
      message.error(getErrorMessage(cause, '激活模型版本失败'));
    } finally {
      setOperating(false);
    }
  };

  const rollback = async () => {
    if (!overview?.modelName) {
      return;
    }
    setOperating(true);
    try {
      const response = await request.post(
        `/model-versions/rollback/${encodeURIComponent(overview.modelName)}`,
        undefined,
        { params: { operatorName: operatorName.trim() || 'web-console' } },
      ) as ApiResponse<OperationResponse>;
      const result = responseData(response, '回滚模型版本失败');
      message.success(result.message);
      await refreshAfterOperation();
    } catch (cause) {
      message.error(getErrorMessage(cause, '回滚模型版本失败'));
    } finally {
      setOperating(false);
    }
  };

  const showVersionDetails = async (versionId: string) => {
    setDetailsOpen(true);
    setDetailsLoading(true);
    setSelectedVersion(null);
    try {
      const response = (await request.get(`/model-versions/details/${versionId}`)) as unknown as ApiResponse<ModelVersion>;
      setSelectedVersion(responseData(response, '读取模型版本详情失败'));
    } catch (cause) {
      message.error(getErrorMessage(cause, '读取模型版本详情失败'));
      setDetailsOpen(false);
    } finally {
      setDetailsLoading(false);
    }
  };

  const versionColumns: ColumnsType<ModelVersion> = [
    {
      title: '版本',
      key: 'version',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Button type="link" className="!px-0" onClick={() => void showVersionDetails(record.id)}>
            {record.modelVersion || record.id}
          </Button>
          <Text type="secondary" className="text-xs">{record.id}</Text>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: string, record) => (
        <Space size={4} wrap>
          <Tag color={statusColors[status] ?? 'default'}>{status}</Tag>
          {record.active && <Tag color="green">生产</Tag>}
          {record.previous && <Tag color="blue">可回滚</Tag>}
        </Space>
      ),
    },
    { title: '特征版本', dataIndex: 'featureVersion', key: 'featureVersion', width: 140 },
    { title: '轮次', dataIndex: 'epoch', key: 'epoch', width: 80 },
    { title: '训练损失', dataIndex: 'trainLoss', key: 'trainLoss', width: 110, render: formatLoss },
    { title: '验证损失', dataIndex: 'valLoss', key: 'valLoss', width: 110, render: formatLoss },
    { title: '参数大小', dataIndex: 'parameterSize', key: 'parameterSize', width: 110, render: formatBytes },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180, render: formatDateTime },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 100,
      render: (_, record) => record.status === 'READY' ? (
        <Popconfirm
          title="激活该模型版本？"
          description="生产推理指针将切换到该版本，当前版本会成为回滚版本。"
          okText="确认激活"
          cancelText="取消"
          onConfirm={() => void activateVersion(record.id)}
        >
          <Button type="primary" size="small">激活</Button>
        </Popconfirm>
      ) : <Text type="secondary">-</Text>,
    },
  ];

  const runColumns: ColumnsType<TrainingRun> = [
    { title: '运行 ID', dataIndex: 'id', key: 'id', width: 220, ellipsis: true },
    { title: '状态', dataIndex: 'status', key: 'status', width: 110, render: (status: string) => <Tag color={statusColors[status] ?? 'default'}>{status}</Tag> },
    { title: '来源', dataIndex: 'triggerSource', key: 'triggerSource', width: 140 },
    { title: '操作人', dataIndex: 'triggeredBy', key: 'triggeredBy', width: 130 },
    {
      title: '训练进度',
      key: 'progress',
      width: 300,
      render: (_, record) => (
        <Space direction="vertical" size={0} className="w-full">
          <Progress
            percent={record.progressPercent ?? (record.status === 'SUCCEEDED' ? 100 : 0)}
            size="small"
            status={record.status === 'FAILED'
              ? 'exception'
              : record.status === 'SUCCEEDED' ? 'success' : 'active'}
          />
          <Text type="secondary" className="text-xs break-words">
            {record.progressMessage
              || progressStageLabels[record.progressStage ?? '']
              || record.progressStage
              || '-'}
            {record.totalEpochs ? ` · ${record.currentEpoch ?? 0}/${record.totalEpochs} 轮` : ''}
          </Text>
        </Space>
      ),
    },
    { title: '训练样本', dataIndex: 'trainSamples', key: 'trainSamples', width: 100, render: (value?: number) => value ?? '-' },
    { title: '验证样本', dataIndex: 'valSamples', key: 'valSamples', width: 100, render: (value?: number) => value ?? '-' },
    { title: '验证损失', dataIndex: 'valLoss', key: 'valLoss', width: 110, render: formatLoss },
    { title: '候选版本', dataIndex: 'candidateModelVersionId', key: 'candidateModelVersionId', width: 200, ellipsis: true, render: (value?: string) => value ?? '-' },
    { title: '开始时间', dataIndex: 'startedAt', key: 'startedAt', width: 180, render: formatDateTime },
    { title: '结束时间', dataIndex: 'finishedAt', key: 'finishedAt', width: 180, render: formatDateTime },
    { title: '失败原因', dataIndex: 'errorMessage', key: 'errorMessage', width: 220, ellipsis: true, render: (value?: string) => value ?? '-' },
  ];

  const latestTraining = overview?.latestTrainingRun;
  const legacyProgress = latestTraining?.status === 'RUNNING'
    && latestTraining.progressStage == null
    && latestTraining.progressPercent == null
    && latestTraining.currentEpoch == null;
  const displayedElapsedSeconds = latestTraining?.startedAt && latestTraining.status === 'RUNNING'
    ? Math.max(
        latestTraining.elapsedSeconds ?? 0,
        Math.floor((progressClock - new Date(latestTraining.startedAt).getTime()) / 1000),
      )
    : latestTraining?.elapsedSeconds;

  const headerActions = useMemo(() => (
    <Space wrap>
      <Input
        className="w-44"
        value={operatorName}
        maxLength={64}
        placeholder="操作人"
        onChange={(event) => setOperatorName(event.target.value)}
      />
      <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadAll()}>刷新</Button>
      <Button
        type="primary"
        icon={<ExperimentOutlined />}
        onClick={() => {
          form.setFieldsValue({
            ...defaultTrainingValues,
            operatorName: operatorName.trim() || 'web-console',
          });
          setTrainingModalOpen(true);
        }}
      >
        触发训练
      </Button>
    </Space>
  ), [form, loadAll, loading, operatorName]);

  usePageHeader('模型版本、训练门禁、候选激活与生产回滚', headerActions);

  return (
    <div className="app-page">
      {overview && (
        <Tag color={overview.trainingAllowed ? 'green' : 'orange'}>
          {overview.trainingAllowed ? '训练门禁已通过' : '训练门禁当前不通过'}
        </Tag>
      )}

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card className="surface-card compact-card" loading={loading}>
            <Statistic
              title="生产版本"
              value={overview?.activeVersion?.modelVersion || '未激活'}
              prefix={<CloudServerOutlined />}
            />
            <div className="mt-3">
              <Tag color={overview?.activeModelHealthy ? 'green' : 'red'}>
                {overview?.activeModelHealthy ? '校验正常' : '未通过校验'}
              </Tag>
            </div>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="surface-card compact-card" loading={loading}>
            <Statistic
              title="上一版本"
              value={overview?.previousVersion?.modelVersion || '无'}
              prefix={<RollbackOutlined />}
            />
            <div className="mt-3">
              <Popconfirm
                title="回滚到上一版本？"
                description="当前生产版本和上一版本指针将交换。"
                okText="确认回滚"
                cancelText="取消"
                onConfirm={() => void rollback()}
              >
                <Button
                  danger
                  size="small"
                  icon={<RollbackOutlined />}
                >
                  执行回滚
                </Button>
              </Popconfirm>
            </div>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="surface-card compact-card" loading={loading}>
            <Statistic
              title="节点训练能力"
              value={overview?.trainingEnabled ? '已开启' : '已关闭'}
              prefix={<SafetyCertificateOutlined />}
            />
            <div className="mt-3">
              <Tag color={overview?.tradingTime ? 'red' : 'green'}>
                {overview?.tradingTime ? '交易时段' : '非交易时段'}
              </Tag>
            </div>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="surface-card compact-card" loading={loading}>
            <Statistic
              title="最近训练"
              value={overview?.latestTrainingRun?.status || '无记录'}
              prefix={<CheckCircleOutlined />}
            />
            <div className="mt-3 text-xs text-gray-400">
              {formatDateTime(overview?.latestTrainingRun?.createdAt)}
            </div>
          </Card>
        </Col>
      </Row>

      {latestTraining && (
        <Card className="surface-card" title="训练进度">
          <Space direction="vertical" size="middle" className="w-full">
            <div className="flex flex-wrap items-center gap-2">
              <Tag color={statusColors[latestTraining.status] ?? 'default'}>
                {latestTraining.status}
              </Tag>
              <Tag color="blue">
                {legacyProgress
                  ? '数据处理中'
                  : progressStageLabels[latestTraining.progressStage ?? '']
                    ?? latestTraining.progressStage
                    ?? '等待进度'}
              </Tag>
              <Text>
                {legacyProgress
                  ? '当前任务由旧版后端启动，暂未上报轮次和损失；已运行时长仍实时更新。'
                  : latestTraining.progressMessage || '等待训练进度更新'}
              </Text>
              {(latestTraining.status === 'QUEUED' || latestTraining.status === 'RUNNING' || latestTraining.status === 'STOPPING') && (
                <Button
                  danger
                  size="small"
                  icon={<StopOutlined />}
                  onClick={() => void stopTraining(latestTraining.id)}
                >
                  停止训练
                </Button>
              )}
            </div>
            <Progress
              percent={legacyProgress
                ? 0
                : latestTraining.progressPercent
                  ?? (latestTraining.status === 'SUCCEEDED' ? 100 : 0)}
              status={latestTraining.status === 'FAILED'
                ? 'exception'
                : latestTraining.status === 'SUCCEEDED' ? 'success' : 'active'}
            />
            <Descriptions bordered size="small" column={{ xs: 1, sm: 2, lg: 4 }}>
              <Descriptions.Item label="训练轮次">
                {legacyProgress
                  ? '待后端上报'
                  : latestTraining.totalEpochs
                    ? `${latestTraining.currentEpoch ?? 0} / ${latestTraining.totalEpochs}`
                    : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="已耗时">
                {formatDuration(displayedElapsedSeconds)}
              </Descriptions.Item>
              <Descriptions.Item label="预计剩余">
                {legacyProgress
                  ? '待后端上报'
                  : latestTraining.status === 'RUNNING' && !latestTraining.currentEpoch
                    ? '计算中'
                    : formatDuration(latestTraining.estimatedRemainingSeconds)}
              </Descriptions.Item>
              <Descriptions.Item label="最近更新">
                {formatDateTime(latestTraining.lastProgressAt || latestTraining.startedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="训练损失">
                {legacyProgress ? '待后端上报' : formatLoss(latestTraining.trainLoss)}
              </Descriptions.Item>
              <Descriptions.Item label="验证损失">
                {legacyProgress ? '待后端上报' : formatLoss(latestTraining.valLoss)}
              </Descriptions.Item>
              <Descriptions.Item label="有效股票">
                {legacyProgress ? '待后端上报' : latestTraining.trainedStocks ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="样本规模">
                {legacyProgress
                  ? '待后端上报'
                  : `${latestTraining.trainSamples ?? '-'} / ${latestTraining.valSamples ?? '-'}`}
              </Descriptions.Item>
            </Descriptions>
          </Space>
        </Card>
      )}

      <Card className="surface-card" title="模型版本">
        <Table<ModelVersion>
          rowKey="id"
          columns={versionColumns}
          dataSource={versions}
          loading={loading}
          locale={{ emptyText: <Empty description="暂无模型版本" /> }}
          scroll={{ x: 1380 }}
          pagination={{
            current: versionPage,
            pageSize,
            total: versionTotal,
            showSizeChanger: false,
            showTotal: (total) => `共 ${total} 个版本`,
            onChange: (current) => {
              setVersionPage(current);
            },
          }}
        />
      </Card>

      <Card className="surface-card" title="训练记录">
        <Table<TrainingRun>
          rowKey="id"
          columns={runColumns}
          dataSource={trainingRuns}
          loading={loading}
          locale={{ emptyText: <Empty description="暂无训练记录" /> }}
          scroll={{ x: 1980 }}
          pagination={{
            current: runPage,
            pageSize,
            total: runTotal,
            showSizeChanger: false,
            showTotal: (total) => `共 ${total} 次训练`,
            onChange: (current) => {
              setRunPage(current);
            },
          }}
        />
      </Card>

      <Modal
        title="触发受控模型训练"
        open={trainingModalOpen}
        okText="提交训练"
        cancelText="取消"
        confirmLoading={operating}
        onOk={() => void submitTraining()}
        onCancel={() => setTrainingModalOpen(false)}
        destroyOnHidden
      >
        <Alert
          className="mb-4"
          type="info"
          showIcon
          message="训练成功后仅生成 READY 候选版本，不会自动切换生产模型。"
        />
        <Form<StartTrainingRequest> form={form} layout="vertical">
          <Form.Item name="operatorName" label="操作人" rules={[{ required: true, message: '请输入操作人' }]}>
            <Input maxLength={64} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="days" label="训练交易日数" extra="留空使用默认值，允许 90-2000">
                <InputNumber className="w-full" min={90} max={2000} precision={0} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="epochs" label="训练轮次" extra="留空使用默认值，允许 1-500">
                <InputNumber className="w-full" min={1} max={500} precision={0} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="batchSize" label="批次大小" extra="留空使用默认值，允许 8-512">
                <InputNumber className="w-full" min={8} max={512} precision={0} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="learningRate" label="学习率" extra="留空使用默认值，允许 0.000001-0.1">
                <InputNumber className="w-full" min={0.000001} max={0.1} step={0.0001} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      <Drawer
        title="模型版本详情"
        width={720}
        open={detailsOpen}
        loading={detailsLoading}
        onClose={() => setDetailsOpen(false)}
      >
        {selectedVersion && (
          <Space direction="vertical" size="large" className="w-full">
            <Descriptions bordered column={2} size="small">
              <Descriptions.Item label="模型名称">{selectedVersion.modelName}</Descriptions.Item>
              <Descriptions.Item label="模型版本">{selectedVersion.modelVersion}</Descriptions.Item>
              <Descriptions.Item label="状态"><Tag color={statusColors[selectedVersion.status]}>{selectedVersion.status}</Tag></Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatDateTime(selectedVersion.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="执行引擎">{selectedVersion.engineName || '-'}</Descriptions.Item>
              <Descriptions.Item label="引擎版本">{selectedVersion.engineVersion || '-'}</Descriptions.Item>
              <Descriptions.Item label="DJL 版本">{selectedVersion.djlVersion || '-'}</Descriptions.Item>
              <Descriptions.Item label="参数大小">{formatBytes(selectedVersion.parameterSize)}</Descriptions.Item>
              <Descriptions.Item label="训练损失">{formatLoss(selectedVersion.trainLoss)}</Descriptions.Item>
              <Descriptions.Item label="验证损失">{formatLoss(selectedVersion.valLoss)}</Descriptions.Item>
              <Descriptions.Item label="参数摘要" span={2}>{selectedVersion.parameterSha256 || '-'}</Descriptions.Item>
            </Descriptions>
            {[
              ['训练配置快照', selectedVersion.trainingConfigJson],
              ['输入契约快照', selectedVersion.inputContractJson],
              ['模型指标快照', selectedVersion.metricsJson],
            ].map(([label, value]) => (
              <div key={label} className="w-full">
                <Title level={5}>{label}</Title>
                <Paragraph>
                  <pre className="max-h-72 overflow-auto whitespace-pre-wrap break-all rounded bg-black/30 p-3 text-xs text-gray-300">
                    {value || '无'}
                  </pre>
                </Paragraph>
              </div>
            ))}
          </Space>
        )}
      </Drawer>
    </div>
  );
};

export default ModelOperations;
// AI_GENERATE_END --------------
