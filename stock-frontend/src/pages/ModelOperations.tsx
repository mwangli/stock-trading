// AI_GENERATE_START ---------------------
import React, { useCallback, useEffect, useState } from 'react';
import { formatDateTime } from '../utils/dateTime';
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
  Select,
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
  LineChartOutlined,
  ReloadOutlined,
  RollbackOutlined,
  SafetyCertificateOutlined,
  StopOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import request from '../utils/request';
import { getApiErrorMessage, readApiData, type ApiResponse } from '../utils/api';
import { usePageHeader } from '../layouts/PageHeaderContext';
import { useTranslation } from 'react-i18next';
import { localizedText } from '../utils/localizedText';

const { Title, Text, Paragraph } = Typography;

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

interface PredictionBatch {
  id: string;
  modelName: string;
  modelId: string;
  modelVersion: string;
  baseTradeDate?: string;
  targetTradeDate?: string;
  informationCutoffAt?: string;
  calendarFallback?: boolean;
  status: string;
  totalStocks: number;
  processedStocks: number;
  successCount: number;
  failedCount: number;
  evaluatedCount: number;
  noMarketDataCount: number;
  coverageRate?: number;
  evaluationRate?: number;
  abilityLevel?: string;
  iterationRecommended: boolean;
  abilityMessage?: string;
  tradingEligibility?: string;
  tradingEligibilityReason?: string;
  progressMessage?: string;
  triggeredBy?: string;
  startedAt?: string;
  finishedAt?: string;
  errorMessage?: string;
  createdAt?: string;
  updatedAt?: string;
}

interface PredictionResult {
  id: string;
  batchId: string;
  stockCode: string;
  stockName?: string;
  industryCode?: number;
  modelId: string;
  modelVersion: string;
  baseTradeDate?: string;
  targetTradeDate?: string;
  calendarFallback?: boolean;
  baseClosePrice?: number;
  predictedClosePrice?: number;
  predictedReturn?: number;
  directionProbability?: number;
  downsideRisk?: number;
  predictedReturnPercentile?: number;
  directionProbabilityPercentile?: number;
  actualClosePrice?: number;
  actualReturn?: number;
  returnError?: number;
  absoluteError?: number;
  directionCorrect?: boolean;
  evaluationStatus: string;
  predictedAt?: string;
  evaluatedAt?: string;
}

interface ModelAbility {
  batchId: string;
  modelId: string;
  modelVersion: string;
  baseTradeDate?: string;
  targetTradeDate?: string;
  predictedCount: number;
  evaluatedCount: number;
  coverageRate?: number;
  evaluationRate?: number;
  directionAccuracy?: number;
  actualUpRate?: number;
  constantBaselineAccuracy?: number;
  directionAuc?: number;
  brierScore?: number;
  brierBaseline?: number;
  expectedCalibrationError?: number;
  calibrationSampleCount?: number;
  directionProbabilityStd?: number;
  predictedReturnStd?: number;
  rollingWindowBatchCount?: number;
  rollingWindowSampleCount?: number;
  rollingAverageSpearmanRankIc?: number;
  rollingAverageTopDecileReturn?: number;
  returnMae?: number;
  returnRmse?: number;
  pearsonIc?: number;
  spearmanRankIc?: number;
  topDecileAverageReturn?: number;
  topDecileDirectionAccuracy?: number;
  highConfidenceAccuracy?: number;
  downsideRiskHitRate?: number;
  abilityLevel?: string;
  iterationRecommended: boolean;
  abilityMessage?: string;
  tradingEligibility?: string;
  tradingEligibilityReason?: string;
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
  COMPLETED: 'green',
  PARTIAL: 'orange',
  EVALUATED: 'cyan',
};

const evaluationStatusColors: Record<string, string> = {
  PENDING: 'blue',
  EVALUATED: 'green',
  NO_MARKET_DATA: 'orange',
};

const abilityLabels: Record<string, string> = {
  STRONG: '能力较强',
  ACCEPTABLE: '基本可用',
  WEAK: '能力较弱',
  INSUFFICIENT_DATA: '样本不足',
};

const abilityLabelsEn: Record<string, string> = {
  STRONG: 'Strong',
  ACCEPTABLE: 'Acceptable',
  WEAK: 'Weak',
  INSUFFICIENT_DATA: 'Insufficient Data',
};


const formatLoss = (value?: number) => value === undefined || value === null
  ? '-'
  : value.toFixed(6);

const formatPercent = (value?: number, digits = 2) => value === undefined || value === null
  ? '-'
  : `${(value * 100).toFixed(digits)}%`;

const formatNumber = (value?: number, digits = 4) => value === undefined || value === null
  ? '-'
  : value.toFixed(digits);

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

const progressStageLabelsEn: Record<string, string> = {
  QUEUED: 'Queued',
  PREPARING_DATA: 'Preparing Data',
  PROCESSING_SAMPLES: 'Building Samples',
  TRAINING: 'Training Model',
  SAVING_MODEL: 'Saving Model',
  VALIDATING_MODEL: 'Validating Candidate',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
};

const formatDuration = (seconds?: number) => {
  if (seconds === undefined || seconds === null || seconds < 0) {
    return '-';
  }
  if (seconds < 60) {
    return `${seconds} ${localizedText('秒', 's')}`;
  }
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainingSeconds = seconds % 60;
  if (hours > 0) {
    return `${hours} ${localizedText('小时', 'h')} ${minutes} ${localizedText('分', 'm')}`;
  }
  return `${minutes} ${localizedText('分', 'm')} ${remainingSeconds} ${localizedText('秒', 's')}`;
};

/**
 * 模型版本、训练和生产切换运维页面。
 */
const ModelOperations: React.FC = () => {
  const { t } = useTranslation();
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
  const [predictionLoading, setPredictionLoading] = useState(false);
  const [predictionOperating, setPredictionOperating] = useState(false);
  const [predictionBatches, setPredictionBatches] = useState<PredictionBatch[]>([]);
  const [predictionBatchTotal, setPredictionBatchTotal] = useState(0);
  const [selectedPredictionBatchId, setSelectedPredictionBatchId] = useState<string>();
  const [predictionResults, setPredictionResults] = useState<PredictionResult[]>([]);
  const [predictionResultTotal, setPredictionResultTotal] = useState(0);
  const [predictionResultPage, setPredictionResultPage] = useState(1);
  const [predictionStatus, setPredictionStatus] = useState<string>();
  const [modelAbility, setModelAbility] = useState<ModelAbility | null>(null);

  const loadOverview = useCallback(async () => {
    const response = await request.get('/model-operations/overview') as ApiResponse<Overview>;
    setOverview(readApiData(response, localizedText('读取模型运维概览失败', 'Failed to load model operations overview')));
  }, []);

  const loadVersions = useCallback(async (current: number) => {
    const response = await request.get('/model-versions', {
      params: { current, pageSize },
    }) as ApiResponse<PageResponse<ModelVersion>>;
    const page = readApiData(response, localizedText('读取模型版本失败', 'Failed to load model versions'));
    setVersions(page.items ?? []);
    setVersionTotal(page.total);
    setVersionPage(page.current);
  }, [pageSize]);

  const loadTrainingRuns = useCallback(async (current: number) => {
    const response = await request.get('/model-training-runs', {
      params: { current, pageSize },
    }) as ApiResponse<PageResponse<TrainingRun>>;
    const page = readApiData(response, localizedText('读取训练记录失败', 'Failed to load training records'));
    setTrainingRuns(page.items ?? []);
    setRunTotal(page.total);
    setRunPage(page.current);
  }, [pageSize]);


  const loadPredictionResults = useCallback(async (
    batchId: string,
    current = predictionResultPage,
    status = predictionStatus,
  ) => {
    const [resultResponse, abilityResponse] = await Promise.all([
      request.get('/model-predictions/results', {
        params: { batchId, evaluationStatus: status, current, pageSize: 20 },
      }) as Promise<ApiResponse<PageResponse<PredictionResult>>>,
      request.get('/model-predictions/ability', { params: { batchId } }) as Promise<ApiResponse<ModelAbility>>,
    ]);
      const resultPage = readApiData(await resultResponse, localizedText('读取预测结果失败', 'Failed to load prediction results'));
    setPredictionResults(resultPage.items ?? []);
    setPredictionResultTotal(resultPage.total);
    setPredictionResultPage(resultPage.current);
      setModelAbility(readApiData(await abilityResponse, localizedText('读取模型能力失败', 'Failed to load model capability')));
  }, [predictionResultPage, predictionStatus]);

  const loadPredictionData = useCallback(async () => {
    setPredictionLoading(true);
    try {
      const response = await request.get('/model-predictions/batches', {
        params: { current: 1, pageSize: 10 },
      }) as ApiResponse<PageResponse<PredictionBatch>>;
      const page = readApiData(response, localizedText('读取预测批次失败', 'Failed to load prediction batches'));
      const batches = page.items ?? [];
      setPredictionBatches(batches);
      setPredictionBatchTotal(page.total);
      const batchId = selectedPredictionBatchId && batches.some((item) => item.id === selectedPredictionBatchId)
        ? selectedPredictionBatchId
        : batches[0]?.id;
      setSelectedPredictionBatchId(batchId);
      if (batchId) {
        await loadPredictionResults(batchId);
      } else {
        setPredictionResults([]);
        setPredictionResultTotal(0);
        setModelAbility(null);
      }
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('读取预测分析数据失败', 'Failed to load prediction analysis')));
    } finally {
      setPredictionLoading(false);
    }
  }, [loadPredictionResults, selectedPredictionBatchId]);

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      await Promise.all([
        loadOverview(),
        loadVersions(versionPage),
        loadTrainingRuns(runPage),
        loadPredictionData(),
      ]);
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('读取模型运维数据失败', 'Failed to load model operations data')));
    } finally {
      setLoading(false);
    }
  }, [loadOverview, loadPredictionData, loadTrainingRuns, loadVersions, runPage, versionPage]);

  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  useEffect(() => {
    if (!overview?.trainingRunning) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void Promise.all([loadOverview(), loadTrainingRuns(runPage)]).catch((cause) => {
        message.error(getApiErrorMessage(cause, localizedText('刷新训练进度失败', 'Failed to refresh training progress')));
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


  useEffect(() => {
    const running = predictionBatches.some((item) => item.status === 'QUEUED' || item.status === 'RUNNING');
    if (!running) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void loadPredictionData();
    }, 3000);
    return () => window.clearInterval(timer);
  }, [loadPredictionData, predictionBatches]);

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
      const result = readApiData(response, localizedText('提交训练失败', 'Failed to submit training'));
      message.success(result.message);
      setTrainingModalOpen(false);
      form.resetFields();
      await refreshAfterOperation();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('提交训练失败', 'Failed to submit training')));
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
      const result = readApiData(response, localizedText('停止训练失败', 'Failed to stop training'));
      message.success(result.message);
      await refreshAfterOperation();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('停止训练失败', 'Failed to stop training')));
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
      const result = readApiData(response, localizedText('激活模型版本失败', 'Failed to activate model version'));
      message.success(result.message);
      await refreshAfterOperation();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('激活模型版本失败', 'Failed to activate model version')));
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
      const result = readApiData(response, localizedText('回滚模型版本失败', 'Failed to roll back model version'));
      message.success(result.message);
      await refreshAfterOperation();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('回滚模型版本失败', 'Failed to roll back model version')));
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
      setSelectedVersion(readApiData(response, localizedText('读取模型版本详情失败', 'Failed to load model version details')));
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('读取模型版本详情失败', 'Failed to load model version details')));
      setDetailsOpen(false);
    } finally {
      setDetailsLoading(false);
    }
  };


  const runPrediction = async () => {
    setPredictionOperating(true);
    try {
      const response = await request.post('/model-predictions/run', {
        operatorName: operatorName.trim() || 'web-console',
      }) as ApiResponse<{ batchId: string; status: string; message: string }>;
      const result = readApiData(response, localizedText('提交全市场预测失败', 'Failed to start full-market prediction'));
      message.success(result.message);
      setSelectedPredictionBatchId(result.batchId);
      await loadPredictionData();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('提交全市场预测失败', 'Failed to start full-market prediction')));
    } finally {
      setPredictionOperating(false);
    }
  };

  const evaluatePrediction = async () => {
    setPredictionOperating(true);
    try {
      const response = await request.post('/model-predictions/evaluate', {
        batchId: selectedPredictionBatchId,
      }) as ApiResponse<{ batchId: string; status: string; message: string }>;
      const result = readApiData(response, localizedText('刷新预测实际结果失败', 'Failed to refresh actual prediction results'));
      message.success(result.message);
      await loadPredictionData();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('刷新预测实际结果失败', 'Failed to refresh actual prediction results')));
    } finally {
      setPredictionOperating(false);
    }
  };

  const versionColumns: ColumnsType<ModelVersion> = [
    {
      title: localizedText('版本', 'Version'),
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
      title: localizedText('状态', 'Status'),
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: string, record) => (
        <Space size={4} wrap>
          <Tag color={statusColors[status] ?? 'default'}>{status}</Tag>
          {record.active && <Tag color="green">{localizedText('生产', 'Production')}</Tag>}
          {record.previous && <Tag color="blue">{localizedText('可回滚', 'Rollback Available')}</Tag>}
        </Space>
      ),
    },
    { title: localizedText('特征版本', 'Feature Version'), dataIndex: 'featureVersion', key: 'featureVersion', width: 140 },
    { title: localizedText('轮次', 'Epoch'), dataIndex: 'epoch', key: 'epoch', width: 80 },
    { title: localizedText('训练损失', 'Training Loss'), dataIndex: 'trainLoss', key: 'trainLoss', width: 110, render: formatLoss },
    { title: localizedText('验证损失', 'Validation Loss'), dataIndex: 'valLoss', key: 'valLoss', width: 110, render: formatLoss },
    { title: localizedText('参数大小', 'Parameter Size'), dataIndex: 'parameterSize', key: 'parameterSize', width: 110, render: formatBytes },
    { title: localizedText('创建时间', 'Created At'), dataIndex: 'createdAt', key: 'createdAt', width: 180, render: formatDateTime },
    {
      title: localizedText('操作', 'Actions'),
      key: 'actions',
      fixed: 'right',
      width: 100,
      render: (_, record) => record.status === 'READY' ? (
        <Popconfirm
          title={localizedText('激活该模型版本？', 'Activate this model version?')}
          description={localizedText('生产推理指针将切换到该版本，当前版本会成为回滚版本。', 'The production inference pointer will switch to this version, and the current version will become the rollback version.')}
          okText={localizedText('确认激活', 'Activate')}
          cancelText={localizedText('取消', 'Cancel')}
          onConfirm={() => void activateVersion(record.id)}
        >
          <Button type="primary" size="small">{localizedText('激活', 'Activate')}</Button>
        </Popconfirm>
      ) : <Text type="secondary">-</Text>,
    },
  ];

  const runColumns: ColumnsType<TrainingRun> = [
    { title: localizedText('运行 ID', 'Run ID'), dataIndex: 'id', key: 'id', width: 220, ellipsis: true },
    { title: localizedText('状态', 'Status'), dataIndex: 'status', key: 'status', width: 110, render: (status: string) => <Tag color={statusColors[status] ?? 'default'}>{status}</Tag> },
    { title: localizedText('来源', 'Source'), dataIndex: 'triggerSource', key: 'triggerSource', width: 140 },
    { title: localizedText('操作人', 'Operator'), dataIndex: 'triggeredBy', key: 'triggeredBy', width: 130 },
    {
      title: localizedText('训练进度', 'Training Progress'),
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
              || localizedText(progressStageLabels[record.progressStage ?? ''] ?? '', progressStageLabelsEn[record.progressStage ?? ''] ?? '')
              || record.progressStage
              || '-'}
            {record.totalEpochs ? ` · ${record.currentEpoch ?? 0}/${record.totalEpochs} ${localizedText('轮', 'epochs')}` : ''}
          </Text>
        </Space>
      ),
    },
    { title: localizedText('训练样本', 'Training Samples'), dataIndex: 'trainSamples', key: 'trainSamples', width: 100, render: (value?: number) => value ?? '-' },
    { title: localizedText('验证样本', 'Validation Samples'), dataIndex: 'valSamples', key: 'valSamples', width: 100, render: (value?: number) => value ?? '-' },
    { title: localizedText('验证损失', 'Validation Loss'), dataIndex: 'valLoss', key: 'valLoss', width: 110, render: formatLoss },
    { title: localizedText('候选版本', 'Candidate Version'), dataIndex: 'candidateModelVersionId', key: 'candidateModelVersionId', width: 200, ellipsis: true, render: (value?: string) => value ?? '-' },
    { title: localizedText('开始时间', 'Started At'), dataIndex: 'startedAt', key: 'startedAt', width: 180, render: formatDateTime },
    { title: localizedText('结束时间', 'Finished At'), dataIndex: 'finishedAt', key: 'finishedAt', width: 180, render: formatDateTime },
    { title: localizedText('失败原因', 'Failure Reason'), dataIndex: 'errorMessage', key: 'errorMessage', width: 220, ellipsis: true, render: (value?: string) => value ?? '-' },
  ];


  const predictionBatchColumns: ColumnsType<PredictionBatch> = [
    {
      title: localizedText('批次', 'Batch'), dataIndex: 'id', key: 'id', width: 210, ellipsis: true,
      render: (value: string, record) => (
        <Button type="link" className="!px-0" onClick={() => {
          setSelectedPredictionBatchId(record.id);
          setPredictionResultPage(1);
          void loadPredictionResults(record.id, 1, predictionStatus);
        }}>{value}</Button>
      ),
    },
    { title: localizedText('模型版本', 'Model Version'), dataIndex: 'modelVersion', key: 'modelVersion', width: 210, ellipsis: true },
    { title: localizedText('基准日', 'Base Date'), dataIndex: 'baseTradeDate', key: 'baseTradeDate', width: 110 },
    { title: localizedText('目标日', 'Target Date'), dataIndex: 'targetTradeDate', key: 'targetTradeDate', width: 110 },
    { title: localizedText('信息截止', 'Information Cutoff'), dataIndex: 'informationCutoffAt', key: 'informationCutoffAt', width: 180, render: formatDateTime },
    {
      title: localizedText('日历来源', 'Calendar Source'), dataIndex: 'calendarFallback', key: 'calendarFallback', width: 120,
      render: (value?: boolean) => value === undefined || value === null
        ? <Tag>{localizedText('未知', 'Unknown')}</Tag>
        : <Tag color={value ? 'warning' : 'green'}>{value ? localizedText('工作日推算', 'Weekday Estimate') : localizedText('正式日历', 'Official Calendar')}</Tag>,
    },
    { title: localizedText('状态', 'Status'), dataIndex: 'status', key: 'status', width: 110, render: (value: string) => <Tag color={statusColors[value] ?? 'default'}>{value}</Tag> },
    {
      title: localizedText('进度', 'Progress'), key: 'progress', width: 230,
      render: (_, record) => (
        <Space direction="vertical" size={0} className="w-full">
          <Progress percent={record.totalStocks ? Math.round(record.processedStocks * 100 / record.totalStocks) : 0} size="small" />
          <Text type="secondary" className="text-xs">{record.progressMessage || '-'}</Text>
        </Space>
      ),
    },
    { title: localizedText('成功/失败', 'Success / Failed'), key: 'counts', width: 120, render: (_, record) => `${record.successCount} / ${record.failedCount}` },
    { title: localizedText('覆盖率', 'Coverage'), dataIndex: 'coverageRate', key: 'coverageRate', width: 100, render: (value?: number) => formatPercent(value) },
    { title: localizedText('交易门禁', 'Trading Eligibility'), dataIndex: 'tradingEligibility', key: 'tradingEligibility', width: 130, render: (value?: string) => <Tag color={value === 'TRADE_ELIGIBLE' ? 'green' : value === 'DISABLED' ? 'red' : 'orange'}>{value || 'DISABLED'}</Tag> },
    { title: localizedText('创建时间', 'Created At'), dataIndex: 'createdAt', key: 'createdAt', width: 180, render: formatDateTime },
  ];

  const predictionResultColumns: ColumnsType<PredictionResult> = [
    {
      title: localizedText('股票', 'Stock'), key: 'stock', fixed: 'left', width: 150,
      render: (_, record) => <Space direction="vertical" size={0}><Text>{record.stockCode}</Text><Text type="secondary" className="text-xs">{record.stockName || '-'}</Text></Space>,
    },
    { title: localizedText('基准日', 'Base Date'), dataIndex: 'baseTradeDate', key: 'baseTradeDate', width: 110 },
    { title: localizedText('目标日', 'Target Date'), dataIndex: 'targetTradeDate', key: 'targetTradeDate', width: 110 },
    {
      title: localizedText('日历来源', 'Calendar Source'), dataIndex: 'calendarFallback', key: 'calendarFallback', width: 120,
      render: (value?: boolean) => value === undefined || value === null
        ? <Tag>{localizedText('未知', 'Unknown')}</Tag>
        : <Tag color={value ? 'warning' : 'green'}>{value ? localizedText('工作日推算', 'Weekday Estimate') : localizedText('正式日历', 'Official Calendar')}</Tag>,
    },
    { title: localizedText('基准收盘', 'Base Close'), dataIndex: 'baseClosePrice', key: 'baseClosePrice', width: 110, render: (value?: number) => formatNumber(value, 3) },
    { title: localizedText('预测收盘', 'Predicted Close'), dataIndex: 'predictedClosePrice', key: 'predictedClosePrice', width: 110, render: (value?: number) => formatNumber(value, 3) },
    { title: localizedText('实际收盘', 'Actual Close'), dataIndex: 'actualClosePrice', key: 'actualClosePrice', width: 110, render: (value?: number) => formatNumber(value, 3) },
    { title: localizedText('预测收益', 'Predicted Return'), dataIndex: 'predictedReturn', key: 'predictedReturn', width: 110, render: (value?: number) => formatPercent(value) },
    { title: localizedText('收益分位', 'Return Percentile'), dataIndex: 'predictedReturnPercentile', key: 'predictedReturnPercentile', width: 100, render: (value?: number) => formatPercent(value) },
    { title: localizedText('实际收益', 'Actual Return'), dataIndex: 'actualReturn', key: 'actualReturn', width: 110, render: (value?: number) => formatPercent(value) },
    { title: localizedText('绝对误差', 'Absolute Error'), dataIndex: 'absoluteError', key: 'absoluteError', width: 110, render: (value?: number) => formatPercent(value) },
    { title: localizedText('上涨概率', 'Upside Probability'), dataIndex: 'directionProbability', key: 'directionProbability', width: 110, render: (value?: number) => formatPercent(value) },
    { title: localizedText('概率分位', 'Probability Percentile'), dataIndex: 'directionProbabilityPercentile', key: 'directionProbabilityPercentile', width: 100, render: (value?: number) => formatPercent(value) },
    { title: localizedText('下行风险', 'Downside Risk'), dataIndex: 'downsideRisk', key: 'downsideRisk', width: 110, render: (value?: number) => formatPercent(value) },
    {
      title: localizedText('方向命中', 'Direction Correct'), dataIndex: 'directionCorrect', key: 'directionCorrect', width: 100,
      render: (value?: boolean) => value === undefined || value === null ? '-' : <Tag color={value ? 'green' : 'red'}>{value ? localizedText('命中', 'Hit') : localizedText('未命中', 'Miss')}</Tag>,
    },
    {
      title: localizedText('评估状态', 'Evaluation Status'), dataIndex: 'evaluationStatus', key: 'evaluationStatus', width: 130,
      render: (value: string) => <Tag color={evaluationStatusColors[value] ?? 'default'}>{value}</Tag>,
    },
    { title: localizedText('预测生成时间', 'Prediction Time'), dataIndex: 'predictedAt', key: 'predictedAt', width: 180, render: formatDateTime },
  ];

  usePageHeader(t('modelOperations.subtitle'));

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

  return (
    <div className="app-page">
      <div className="flex flex-wrap items-center justify-between gap-3">
        {overview && (
          <Tag color={overview.trainingAllowed ? 'green' : 'orange'}>
            {overview.trainingAllowed ? t('modelOperations.trainingGatePassed') : t('modelOperations.trainingGateBlocked')}
          </Tag>
        )}
        <Space wrap className="ml-auto">
          <Input
            className="w-44"
            value={operatorName}
            maxLength={64}
            placeholder={localizedText('操作人', 'Operator')}
            onChange={(event) => setOperatorName(event.target.value)}
          />
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
            {localizedText('触发训练', 'Start Training')}
          </Button>
        </Space>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card className="surface-card compact-card" loading={loading}>
            <Statistic
              title={t('modelOperations.productionVersion')}
              value={overview?.activeVersion?.modelVersion || localizedText('未激活', 'Not Active')}
              prefix={<CloudServerOutlined />}
            />
            <div className="mt-3">
              <Tag color={overview?.activeModelHealthy ? 'green' : 'red'}>
                {overview?.activeModelHealthy ? localizedText('校验正常', 'Healthy') : localizedText('未通过校验', 'Validation Failed')}
              </Tag>
            </div>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="surface-card compact-card" loading={loading}>
            <Statistic
              title={localizedText('上一版本', 'Previous Version')}
              value={overview?.previousVersion?.modelVersion || localizedText('无', 'None')}
              prefix={<RollbackOutlined />}
            />
            <div className="mt-3">
              <Popconfirm
                title={localizedText('回滚到上一版本？', 'Rollback to previous version?')}
                description={localizedText('当前生产版本和上一版本指针将交换。', 'The current production pointer and previous version pointer will be exchanged.')}
                okText={localizedText('确认回滚', 'Confirm Rollback')}
                cancelText={localizedText('取消', 'Cancel')}
                onConfirm={() => void rollback()}
              >
                <Button
                  danger
                  size="small"
                  icon={<RollbackOutlined />}
                >
                  {localizedText('执行回滚', 'Rollback')}
                </Button>
              </Popconfirm>
            </div>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="surface-card compact-card" loading={loading}>
            <Statistic
              title={t('modelOperations.trainingCapability')}
              value={overview?.trainingEnabled ? localizedText('已开启', 'Enabled') : localizedText('已关闭', 'Disabled')}
              prefix={<SafetyCertificateOutlined />}
            />
            <div className="mt-3">
              <Tag color={overview?.tradingTime ? 'red' : 'green'}>
                {overview?.tradingTime ? localizedText('交易时段', 'Trading Hours') : localizedText('非交易时段', 'Outside Trading Hours')}
              </Tag>
            </div>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="surface-card compact-card" loading={loading}>
            <Statistic
              title={t('modelOperations.latestTraining')}
              value={overview?.latestTrainingRun?.status || localizedText('无记录', 'No Record')}
              prefix={<CheckCircleOutlined />}
            />
            <div className="mt-3 text-xs text-gray-400">
              {formatDateTime(overview?.latestTrainingRun?.createdAt)}
            </div>
          </Card>
        </Col>
      </Row>

      {latestTraining && (
      <Card className="surface-card" title={t('modelOperations.trainingProgress')}>
          <Space direction="vertical" size="middle" className="w-full">
            <div className="flex flex-wrap items-center gap-2">
              <Tag color={statusColors[latestTraining.status] ?? 'default'}>
                {latestTraining.status}
              </Tag>
              <Tag color="blue">
                {legacyProgress
                  ? localizedText('数据处理中', 'Processing Data')
                  : (localizedText(progressStageLabels[latestTraining.progressStage ?? ''] ?? '', progressStageLabelsEn[latestTraining.progressStage ?? ''] ?? '')
                    || latestTraining.progressStage)
                    ?? localizedText('等待进度', 'Waiting for Progress')}
              </Tag>
              <Text>
                {legacyProgress
                  ? localizedText('当前任务由旧版后端启动，暂未上报轮次和损失；已运行时长仍实时更新。', 'This run was started by a legacy backend and has not reported epochs or loss; elapsed time is still updated.')
                  : latestTraining.progressMessage || localizedText('等待训练进度更新', 'Waiting for training progress')}
              </Text>
              {(latestTraining.status === 'QUEUED' || latestTraining.status === 'RUNNING' || latestTraining.status === 'STOPPING') && (
                <Button
                  danger
                  size="small"
                  icon={<StopOutlined />}
                  onClick={() => void stopTraining(latestTraining.id)}
                >
                  {localizedText('停止训练', 'Stop Training')}
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


      <Card
        className="surface-card"
        title={t('modelOperations.predictionAnalysis')}
        extra={(
          <Space wrap>
            <Select
              className="w-56"
              value={selectedPredictionBatchId}
              placeholder={localizedText('选择预测批次', 'Select Prediction Batch')}
              options={predictionBatches.map((item) => ({
                value: item.id,
                label: `${item.baseTradeDate || '-'} · ${item.status}`,
              }))}
              onChange={(value) => {
                setSelectedPredictionBatchId(value);
                setPredictionResultPage(1);
                void loadPredictionResults(value, 1, predictionStatus);
              }}
            />
            <Button icon={<ReloadOutlined />} loading={predictionLoading} onClick={() => void loadPredictionData()}>{localizedText('刷新数据', 'Refresh Data')}</Button>
            <Button icon={<SyncOutlined />} loading={predictionOperating} onClick={() => void evaluatePrediction()}>{localizedText('刷新实际结果', 'Refresh Actual Results')}</Button>
            <Button type="primary" icon={<LineChartOutlined />} loading={predictionOperating} onClick={() => void runPrediction()}>{localizedText('执行全市场预测', 'Run Full-market Prediction')}</Button>
          </Space>
        )}
      >
        {modelAbility ? (
          <Space direction="vertical" size="large" className="w-full">
            {predictionBatches.find((item) => item.id === selectedPredictionBatchId)?.calendarFallback && (
              <Alert
                type="warning"
                showIcon
                message={localizedText('交易日历缺失或覆盖不足', 'Trading calendar missing or incomplete')}
                description={localizedText('当前目标日由工作日规则推算。行情基准日仍是模型实际使用的最后交易日，请在正式使用前补齐证券交易日历。', 'The target date was estimated from weekdays. The market base date remains the last trading date used by the model; complete the official trading calendar before production use.')}
              />
            )}
            <Alert
              type={modelAbility.tradingEligibility === 'DISABLED' || modelAbility.abilityLevel === 'WEAK' ? 'warning' : modelAbility.tradingEligibility === 'TRADE_ELIGIBLE' ? 'success' : 'info'}
              showIcon
              message={`${localizedText(abilityLabels[modelAbility.abilityLevel || ''] || '', abilityLabelsEn[modelAbility.abilityLevel || ''] || '') || modelAbility.abilityLevel || localizedText('待评估', 'Pending Evaluation')} · ${modelAbility.tradingEligibility || 'DISABLED'}`}
              description={`${modelAbility.abilityMessage || '等待能力评估'}${modelAbility.tradingEligibilityReason ? `；${modelAbility.tradingEligibilityReason}` : ''}`}
            />
            <Row gutter={[16, 16]}>
              <Col xs={12} md={6} xl={3}><Statistic title={localizedText('预测覆盖率', 'Prediction Coverage')} value={formatPercent(modelAbility.coverageRate)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title={localizedText('评估完成率', 'Evaluation Completion')} value={formatPercent(modelAbility.evaluationRate)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title={localizedText('方向准确率', 'Direction Accuracy')} value={formatPercent(modelAbility.directionAccuracy)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="常数基线准确率" value={formatPercent(modelAbility.constantBaselineAccuracy)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="方向 AUC" value={formatNumber(modelAbility.directionAuc)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="Brier Score" value={formatNumber(modelAbility.brierScore)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="Brier 基线" value={formatNumber(modelAbility.brierBaseline)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="ECE" value={formatPercent(modelAbility.expectedCalibrationError)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="校准样本" value={modelAbility.calibrationSampleCount ?? 0} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="上涨概率标准差" value={formatPercent(modelAbility.directionProbabilityStd)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="预测收益标准差" value={formatPercent(modelAbility.predictedReturnStd)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="滚动评估批次" value={modelAbility.rollingWindowBatchCount ?? 0} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="滚动有效样本" value={modelAbility.rollingWindowSampleCount ?? 0} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="滚动 Spearman IC" value={formatNumber(modelAbility.rollingAverageSpearmanRankIc)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="滚动 Top 10% 收益" value={formatPercent(modelAbility.rollingAverageTopDecileReturn)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="收益 MAE" value={formatPercent(modelAbility.returnMae)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="收益 RMSE" value={formatPercent(modelAbility.returnRmse)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="Pearson IC" value={formatNumber(modelAbility.pearsonIc)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="Spearman IC" value={formatNumber(modelAbility.spearmanRankIc)} /></Col>
              <Col xs={12} md={6} xl={3}><Statistic title="Top 10% 实际收益" value={formatPercent(modelAbility.topDecileAverageReturn)} /></Col>
            </Row>
          </Space>
        ) : <Empty description={localizedText('暂无预测批次', 'No prediction batches')} />}
      </Card>

      <Card className="surface-card" title={`预测批次（共 ${predictionBatchTotal} 个）`}>
        <Table<PredictionBatch>
          rowKey="id"
          columns={predictionBatchColumns}
          dataSource={predictionBatches}
          loading={predictionLoading}
          pagination={false}
          scroll={{ x: 1710 }}
          rowClassName={(record) => record.id === selectedPredictionBatchId ? 'bg-blue-500/5' : ''}
        />
      </Card>

      <Card
        className="surface-card"
        title="预测与实际结果对比"
        extra={(
          <Select
            allowClear
            className="w-40"
            value={predictionStatus}
            placeholder="全部评估状态"
            options={[
              { value: 'PENDING', label: '待评估' },
              { value: 'EVALUATED', label: '已评估' },
              { value: 'NO_MARKET_DATA', label: '缺行情' },
            ]}
            onChange={(value) => {
              setPredictionStatus(value);
              setPredictionResultPage(1);
              if (selectedPredictionBatchId) {
                void loadPredictionResults(selectedPredictionBatchId, 1, value);
              }
            }}
          />
        )}
      >
        <Table<PredictionResult>
          rowKey="id"
          columns={predictionResultColumns}
          dataSource={predictionResults}
          loading={predictionLoading}
          locale={{ emptyText: <Empty description={t('modelOperations.predictionEmpty')} /> }}
          scroll={{ x: 1940 }}
          pagination={{
            current: predictionResultPage,
            pageSize: 20,
            total: predictionResultTotal,
            showSizeChanger: false,
            showTotal: (total) => `共 ${total} 条预测`,
            onChange: (current) => {
              setPredictionResultPage(current);
              if (selectedPredictionBatchId) {
                void loadPredictionResults(selectedPredictionBatchId, current, predictionStatus);
              }
            },
          }}
        />
      </Card>

      <Card className="surface-card" title={t('modelOperations.modelVersions')}>
        <Table<ModelVersion>
          rowKey="id"
          columns={versionColumns}
          dataSource={versions}
          loading={loading}
          locale={{ emptyText: <Empty description={t('modelOperations.versionsEmpty')} /> }}
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

      <Card className="surface-card" title={t('modelOperations.trainingRecords')}>
        <Table<TrainingRun>
          rowKey="id"
          columns={runColumns}
          dataSource={trainingRuns}
          loading={loading}
          locale={{ emptyText: <Empty description={t('modelOperations.runsEmpty')} /> }}
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
// AI_GENERATE_END ---------------------
