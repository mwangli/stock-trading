// AI_GENERATE_START -------------------------------------------
import React, { useCallback, useEffect, useRef, useState } from 'react';
import dayjs from 'dayjs';
import { formatDateTime } from '../../utils/dateTime';
import {
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
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
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CheckCircleOutlined,
  CloudServerOutlined,
  ExperimentOutlined,
  LineChartOutlined,
  RollbackOutlined,
  SafetyCertificateOutlined,
  StopOutlined,
} from '@ant-design/icons';
import request from '../../utils/request';
import { getApiErrorMessage, readApiData, type ApiResponse } from '../../utils/api';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import { useTranslation } from 'react-i18next';
import { localizedText } from '../../utils/localizedText';

const { Title, Text } = Typography;

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
}

interface OperationResponse {
  operationType: string;
  operationId: string;
  message: string;
}

interface PredictionOperationResponse {
  batchId: string;
  status: string;
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

interface WideTableModalProps {
  title: React.ReactNode;
  open: boolean;
  onCancel: () => void;
  children: React.ReactNode;
}

const WideTableModal: React.FC<WideTableModalProps> = ({ title, open, onCancel, children }) => (
  <Modal
    title={title}
    open={open}
    width="min(1800px, calc(100vw - 32px))"
    centered
    footer={null}
    destroyOnHidden
    onCancel={onCancel}
  >
    {children}
  </Modal>
);

interface DetailModalProps {
  title: React.ReactNode;
  open: boolean;
  onCancel: () => void;
  children: React.ReactNode;
}

const DetailModal: React.FC<DetailModalProps> = ({ title, open, onCancel, children }) => (
  <Modal
    className="detail-modal"
    title={title}
    open={open}
    width="min(960px, calc(100vw - 32px))"
    centered
    footer={null}
    destroyOnHidden
    onCancel={onCancel}
  >
    <div className="detail-modal-content custom-scrollbar">{children}</div>
  </Modal>
);

interface DetailSectionProps {
  title: string;
  children: React.ReactNode;
  bodyClassName?: string;
}

const DetailSection: React.FC<DetailSectionProps> = ({ title, children, bodyClassName }) => (
  <section className="detail-section">
    <div className="detail-section-title">{title}</div>
    <div className={`detail-section-body${bodyClassName ? ` ${bodyClassName}` : ''}`}>{children}</div>
  </section>
);

interface DetailItemProps {
  label: string;
  children: React.ReactNode;
  wide?: boolean;
}

const DetailItem: React.FC<DetailItemProps> = ({ label, children, wide = false }) => (
  <div className={`detail-item${wide ? ' detail-item-wide' : ''}`}>
    <span className="detail-item-label">{label}</span>
    <div className="detail-item-value">{children}</div>
  </div>
);

/**
 * 模型版本、训练和生产切换运维页面。
 */
const ModelOperations: React.FC = () => {
  const { t } = useTranslation();
  const [form] = Form.useForm<StartTrainingRequest>();
  const [overview, setOverview] = useState<Overview | null>(null);
  const [versions, setVersions] = useState<ModelVersion[]>([]);
  const [versionTotal, setVersionTotal] = useState(0);
  const [versionPage, setVersionPage] = useState(1);
  const [pageSize] = useState(10);
  const [operatorName, setOperatorName] = useState('web-console');
  const [predictionTargetDate, setPredictionTargetDate] = useState(() => dayjs().format('YYYY-MM-DD'));
  const [loading, setLoading] = useState(false);
  const [operating, setOperating] = useState(false);
  const [trainingModalOpen, setTrainingModalOpen] = useState(false);
  const [trainingRecordsOpen, setTrainingRecordsOpen] = useState(false);
  const [trainingRecordsLoading, setTrainingRecordsLoading] = useState(false);
  const [selectedTrainingVersion, setSelectedTrainingVersion] = useState<ModelVersion | null>(null);
  const [selectedVersionTrainingRuns, setSelectedVersionTrainingRuns] = useState<TrainingRun[]>([]);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [selectedVersion, setSelectedVersion] = useState<ModelVersion | null>(null);
  const [progressClock, setProgressClock] = useState(() => Date.now());
  const [predictionLoading, setPredictionLoading] = useState(false);
  const [predictionBatches, setPredictionBatches] = useState<PredictionBatch[]>([]);
  const [predictionBatchTotal, setPredictionBatchTotal] = useState(0);
  const [selectedPredictionBatchId, setSelectedPredictionBatchId] = useState<string>();
  const [predictionResults, setPredictionResults] = useState<PredictionResult[]>([]);
  const [predictionResultTotal, setPredictionResultTotal] = useState(0);
  const [predictionResultPage, setPredictionResultPage] = useState(1);
  const [predictionResultModalOpen, setPredictionResultModalOpen] = useState(false);
  const [predictionAnalysisModalOpen, setPredictionAnalysisModalOpen] = useState(false);
  const [predictionAnalysisLoading, setPredictionAnalysisLoading] = useState(false);
  const [predictionDetailsOpen, setPredictionDetailsOpen] = useState(false);
  const [modelAbility, setModelAbility] = useState<ModelAbility | null>(null);
  const [predictionPollingPaused, setPredictionPollingPaused] = useState(false);
  const predictionDataLoadingRef = useRef(false);

  const loadOverview = useCallback(async () => {
    const response = await request.get('/modelOperations/overview') as ApiResponse<Overview>;
    setOverview(readApiData(response, localizedText('读取模型运维概览失败', 'Failed to load model operations overview')));
  }, []);

  const loadVersions = useCallback(async (current: number) => {
    const response = await request.get('/modelVersions', {
      params: { current, pageSize },
    }) as ApiResponse<PageResponse<ModelVersion>>;
    const page = readApiData(response, localizedText('读取模型版本失败', 'Failed to load model versions'));
    setVersions(page.items ?? []);
    setVersionTotal(page.total);
    setVersionPage(page.current);
  }, [pageSize]);

  const loadPredictionResults = useCallback(async (
    batchId: string,
    current = predictionResultPage,
  ) => {
    const [resultResponse, abilityResponse] = await Promise.all([
      request.get('/modelPredictions/results', {
        params: { batchId, current, pageSize: 20 },
      }) as Promise<ApiResponse<PageResponse<PredictionResult>>>,
      request.get('/modelPredictions/ability', { params: { batchId } }) as Promise<ApiResponse<ModelAbility>>,
    ]);
      const resultPage = readApiData(await resultResponse, localizedText('读取预测结果失败', 'Failed to load prediction results'));
    setPredictionResults(resultPage.items ?? []);
    setPredictionResultTotal(resultPage.total);
    setPredictionResultPage(resultPage.current);
      setModelAbility(readApiData(await abilityResponse, localizedText('读取模型能力失败', 'Failed to load model capability')));
  }, [predictionResultPage]);

  const loadPredictionData = useCallback(async () => {
    if (predictionDataLoadingRef.current) {
      return;
    }
    predictionDataLoadingRef.current = true;
    setPredictionLoading(true);
    try {
      const response = await request.get('/modelPredictions/batches', {
        params: { current: 1, pageSize: 100 },
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
    } finally {
      predictionDataLoadingRef.current = false;
      setPredictionLoading(false);
    }
  }, [loadPredictionResults, selectedPredictionBatchId]);

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      await Promise.all([
        loadOverview(),
        loadVersions(versionPage),
        loadPredictionData(),
      ]);
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('读取模型运维数据失败', 'Failed to load model operations data')));
    } finally {
      setLoading(false);
    }
  }, [loadOverview, loadPredictionData, loadVersions, versionPage]);

  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  useEffect(() => {
    if (!overview?.trainingRunning) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void loadOverview().catch((cause) => {
        message.error(getApiErrorMessage(cause, localizedText('刷新训练进度失败', 'Failed to refresh training progress')));
      });
    }, 3000);
    return () => window.clearInterval(timer);
  }, [loadOverview, overview?.trainingRunning]);

  useEffect(() => {
    if (!overview?.trainingRunning) {
      return undefined;
    }
    const timer = window.setInterval(() => setProgressClock(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [overview?.trainingRunning]);


  useEffect(() => {
    const running = predictionBatches.some((item) => item.status === 'QUEUED' || item.status === 'RUNNING');
    if (!running || predictionPollingPaused) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void loadPredictionData().catch((cause) => {
        setPredictionPollingPaused(true);
        message.error(getApiErrorMessage(cause, localizedText('刷新预测进度失败，已暂停自动刷新', 'Failed to refresh prediction progress; automatic refresh paused')));
      });
    }, 5000);
    return () => window.clearInterval(timer);
  }, [loadPredictionData, predictionBatches, predictionPollingPaused]);

  const refreshAfterOperation = async () => {
    await Promise.all([loadOverview(), loadVersions(1)]);
  };

  const submitTraining = async () => {
    const values = await form.validateFields();
    setOperating(true);
    try {
      const response = await request.post('/modelTrainingRuns/start', {
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
      const response = await request.post(`/modelTrainingRuns/stop/${runId}`, undefined, {
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
      const response = await request.post(`/modelVersions/activate/${versionId}`, undefined, {
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
        `/modelVersions/rollback/${encodeURIComponent(overview.modelName)}`,
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
      const response = (await request.get(`/modelVersions/details/${versionId}`)) as unknown as ApiResponse<ModelVersion>;
      setSelectedVersion(readApiData(response, localizedText('读取模型版本详情失败', 'Failed to load model version details')));
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('读取模型版本详情失败', 'Failed to load model version details')));
      setDetailsOpen(false);
    } finally {
      setDetailsLoading(false);
    }
  };

  const showTrainingRecords = async (version: ModelVersion) => {
    setSelectedTrainingVersion(version);
    setSelectedVersionTrainingRuns([]);
    setTrainingRecordsOpen(true);
    setTrainingRecordsLoading(true);
    try {
      const response = await request.get('/modelTrainingRuns', {
        params: { current: 1, pageSize: 100 },
      }) as ApiResponse<PageResponse<TrainingRun>>;
      const page = readApiData(response, localizedText('读取训练记录失败', 'Failed to load training records'));
      setSelectedVersionTrainingRuns((page.items ?? []).filter((run) => run.candidateModelVersionId === version.id));
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('读取训练记录失败', 'Failed to load training records')));
    } finally {
      setTrainingRecordsLoading(false);
    }
  };

  const openPredictionResults = (batchId: string) => {
    setSelectedPredictionBatchId(batchId);
    setPredictionResultPage(1);
    setPredictionResults([]);
    setPredictionResultTotal(0);
    setPredictionResultModalOpen(true);
    void loadPredictionResults(batchId, 1);
  };

  const openPredictionAnalysis = (batchId: string) => {
    setSelectedPredictionBatchId(batchId);
    setModelAbility(null);
    setPredictionAnalysisLoading(true);
    setPredictionAnalysisModalOpen(true);
    void loadPredictionResults(batchId, 1)
      .catch((cause) => {
        message.error(getApiErrorMessage(cause, localizedText('读取预测分析失败', 'Failed to load prediction analysis')));
      })
      .finally(() => setPredictionAnalysisLoading(false));
  };

  const rerunPrediction = async (batchId: string) => {
    setOperating(true);
    setPredictionPollingPaused(false);
    try {
      const response = await request.post('/modelPredictions/rerun', {
        batchId,
        operatorName: operatorName.trim() || 'web-console',
      }) as ApiResponse<PredictionOperationResponse>;
      const result = readApiData(response, localizedText('提交历史预测重算失败', 'Failed to submit historical prediction rerun'));
      message.success(result.message);
      setSelectedPredictionBatchId(result.batchId);
      await loadPredictionData();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('提交历史预测重算失败', 'Failed to submit historical prediction rerun')));
    } finally {
      setOperating(false);
    }
  };

  const runPrediction = async () => {
    setOperating(true);
    setPredictionPollingPaused(false);
    try {
      const response = await request.post('/modelPredictions/run', {
        operatorName: operatorName.trim() || 'web-console',
        targetTradeDate: predictionTargetDate || undefined,
      }) as ApiResponse<PredictionOperationResponse>;
      const result = readApiData(response, localizedText('提交指定日期预测失败', 'Failed to submit prediction for the selected date'));
      message.success(result.message);
      setSelectedPredictionBatchId(result.batchId);
      await loadPredictionData();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('提交指定日期预测失败', 'Failed to submit prediction for the selected date')));
    } finally {
      setOperating(false);
    }
  };

  const deletePredictionBatch = async (batchId: string) => {
    setOperating(true);
    try {
      const response = await request.delete(`/modelPredictions/batches/${encodeURIComponent(batchId)}`) as ApiResponse<PredictionOperationResponse>;
      const result = readApiData(response, localizedText('删除预测批次失败', 'Failed to delete prediction batch'));
      message.success(result.message);
      if (selectedPredictionBatchId === batchId) {
        setSelectedPredictionBatchId(undefined);
        setPredictionDetailsOpen(false);
        setPredictionResultModalOpen(false);
        setPredictionAnalysisModalOpen(false);
      }
      await loadPredictionData();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('删除预测批次失败', 'Failed to delete prediction batch')));
    } finally {
      setOperating(false);
    }
  };
  const versionColumns: ColumnsType<ModelVersion> = [
    {
      title: localizedText('版本', 'Version'),
      dataIndex: 'modelVersion',
      key: 'modelVersion',
      width: 220,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 190,
      ellipsis: true,
      render: (value: string, record) => (
        <Tooltip title={value}>
          <Button type="link" className="!px-0" onClick={() => void showVersionDetails(record.id)}>
            <span className="max-w-40 truncate align-middle">{value}</span>
          </Button>
        </Tooltip>
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
      width: 180,
      render: (_, record) => (
        <Space size="small" wrap>
          <Button type="link" size="small" className="!px-0" onClick={() => void showTrainingRecords(record)}>
            {localizedText('训练记录', 'Training Records')}
          </Button>
          {record.status === 'READY' && (
            <Popconfirm
              title={localizedText('激活该模型版本？', 'Activate this model version?')}
              description={localizedText('生产推理指针将切换到该版本，当前版本会成为回滚版本。', 'The production inference pointer will switch to this version, and the current version will become the rollback version.')}
              okText={localizedText('确认激活', 'Activate')}
              cancelText={localizedText('取消', 'Cancel')}
              onConfirm={() => void activateVersion(record.id)}
            >
              <Button type="primary" size="small">{localizedText('激活', 'Activate')}</Button>
            </Popconfirm>
          )}
        </Space>
      ),
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
      title: localizedText('批次', 'Batch'), dataIndex: 'id', key: 'id', width: 150,
      render: (value: string, record) => (
        <Tooltip title={value}>
          <Button type="link" className="!px-0" onClick={() => {
            setSelectedPredictionBatchId(record.id);
            setPredictionDetailsOpen(true);
          }}>
            <span className="max-w-32 truncate align-middle">{value}</span>
          </Button>
        </Tooltip>
      ),
    },
    { title: localizedText('模型版本', 'Model Version'), dataIndex: 'modelVersion', key: 'modelVersion', width: 180, ellipsis: true },
    {
      title: localizedText('预测区间', 'Prediction Window'), key: 'predictionWindow', width: 180,
      render: (_, record) => `${record.baseTradeDate || '-'} 至 ${record.targetTradeDate || '-'}`,
    },
    {
      title: localizedText('日历来源', 'Calendar Source'), dataIndex: 'calendarFallback', key: 'calendarFallback', width: 120,
      render: (value?: boolean) => value === undefined || value === null
        ? <Tag>{localizedText('未知', 'Unknown')}</Tag>
        : <Tag color={value ? 'warning' : 'green'}>{value ? localizedText('工作日推算', 'Weekday Estimate') : localizedText('正式日历', 'Official Calendar')}</Tag>,
    },
    { title: localizedText('状态', 'Status'), dataIndex: 'status', key: 'status', width: 110, render: (value: string) => <Tag color={statusColors[value] ?? 'default'}>{value}</Tag> },
    {
      title: localizedText('执行情况', 'Execution'), key: 'execution', width: 260,
      render: (_, record) => (
        <Space direction="vertical" size={0} className="w-full">
          <Progress percent={record.totalStocks ? Math.round(record.processedStocks * 100 / record.totalStocks) : 0} size="small" />
          <Text type="secondary" className="text-xs">
            成功 {record.successCount} / 失败 {record.failedCount} · 覆盖率 {formatPercent(record.coverageRate)}
          </Text>
          {record.progressMessage && <Text type="secondary" className="text-xs">{record.progressMessage}</Text>}
        </Space>
      ),
    },
    { title: localizedText('交易门禁', 'Trading Eligibility'), dataIndex: 'tradingEligibility', key: 'tradingEligibility', width: 130, render: (value?: string) => <Tag color={value === 'TRADE_ELIGIBLE' ? 'green' : value === 'DISABLED' ? 'red' : 'orange'}>{value || 'DISABLED'}</Tag> },
    {
      title: localizedText('操作', 'Actions'), key: 'actions', width: 260,
      render: (_, record) => {
        const canDelete = record.status === 'FAILED' || record.status === 'CANCELLED'
          || (!['QUEUED', 'RUNNING'].includes(record.status) && record.successCount <= 0);
        return (
          <Space size="small">
            <Popconfirm
              title={localizedText('重新生成该历史批次？', 'Rerun this historical batch?')}
              description={localizedText('将使用原模型版本和基准日生成新批次，不覆盖原批次结果。', 'A new batch will use the original model version and base date without overwriting the source batch.')}
              okText={localizedText('确认重算', 'Rerun')}
              cancelText={localizedText('取消', 'Cancel')}
              onConfirm={() => void rerunPrediction(record.id)}
            >
              <Button
                type="link"
                size="small"
                className="!px-0"
                disabled={operating || record.status === 'QUEUED' || record.status === 'RUNNING'}
              >
                {localizedText('预测', 'Predict')}
              </Button>
            </Popconfirm>
            <Button type="link" size="small" className="!px-0" onClick={() => openPredictionResults(record.id)}>
              {localizedText('结果', 'Results')}
            </Button>
            <Button type="link" size="small" className="!px-0" onClick={() => openPredictionAnalysis(record.id)}>
              {localizedText('评估', 'Evaluation')}
            </Button>
            <Popconfirm
              disabled={!canDelete}
              title={localizedText('删除该预测批次及全部关联数据？', 'Delete this batch and all related data?')}
              description={localizedText('仅用于清理失败或无效批次，删除后不可恢复。', 'Only failed or invalid batches should be removed. This cannot be undone.')}
              okText={localizedText('确认删除', 'Delete')}
              cancelText={localizedText('取消', 'Cancel')}
              onConfirm={() => void deletePredictionBatch(record.id)}
            >
              <Button type="link" size="small" className="!px-0" disabled={operating || !canDelete}>
                {localizedText('删除', 'Delete')}
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
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
  const selectedPredictionBatch = predictionBatches.find((item) => item.id === selectedPredictionBatchId);
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
          <DatePicker
            className="w-40 min-w-40"
            value={predictionTargetDate ? dayjs(predictionTargetDate) : null}
            format="YYYY-MM-DD"
            allowClear
            disabledDate={(date) => date.isAfter(dayjs(), 'day')}
            placeholder={localizedText('预测目标日期', 'Prediction Date')}
            aria-label={localizedText('预测目标交易日', 'Prediction target trading date')}
            onChange={(date) => setPredictionTargetDate(date ? date.format('YYYY-MM-DD') : '')}
          />
          <Popconfirm
            title={localizedText('执行指定日期全市场预测？', 'Run full-market prediction for the selected date?')}
            description={predictionTargetDate
              ? `${localizedText('预测目标交易日', 'Target trading date')}: ${predictionTargetDate}`
              : localizedText('未选择日期，将使用最新完整日线推导目标交易日。', 'No date selected; the target date will be derived from the latest complete daily data.')}
            okText={localizedText('执行预测', 'Run Prediction')}
            cancelText={localizedText('取消', 'Cancel')}
            onConfirm={() => void runPrediction()}
          >
            <Button
              icon={<LineChartOutlined />}
              loading={operating}
              disabled={operating}
            >
              {localizedText('执行预测', 'Run Prediction')}
            </Button>
          </Popconfirm>
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

      <Row className="model-overview-cards" gutter={[16, 16]} align="stretch">
        <Col className="model-overview-col" xs={24} md={12} xl={6}>
          <Card className="surface-card compact-card model-overview-card" loading={loading}>
            <Statistic
              title={t('modelOperations.productionVersion')}
              value={overview?.activeVersion?.modelVersion || localizedText('未激活', 'Not Active')}
              prefix={<CloudServerOutlined />}
            />
            <div className="model-overview-card-footer">
              <Tag color={overview?.activeModelHealthy ? 'green' : 'red'}>
                {overview?.activeModelHealthy ? localizedText('校验正常', 'Healthy') : localizedText('未通过校验', 'Validation Failed')}
              </Tag>
            </div>
          </Card>
        </Col>
        <Col className="model-overview-col" xs={24} md={12} xl={6}>
          <Card className="surface-card compact-card model-overview-card" loading={loading}>
            <Statistic
              title={localizedText('上一版本', 'Previous Version')}
              value={overview?.previousVersion?.modelVersion || localizedText('无', 'None')}
              prefix={<RollbackOutlined />}
            />
            <div className="model-overview-card-footer">
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
        <Col className="model-overview-col" xs={24} md={12} xl={6}>
          <Card className="surface-card compact-card model-overview-card" loading={loading}>
            <Statistic
              title={t('modelOperations.trainingCapability')}
              value={overview?.trainingEnabled ? localizedText('已开启', 'Enabled') : localizedText('已关闭', 'Disabled')}
              prefix={<SafetyCertificateOutlined />}
            />
            <div className="model-overview-card-footer">
              <Tag color={overview?.tradingTime ? 'red' : 'green'}>
                {overview?.tradingTime ? localizedText('交易时段', 'Trading Hours') : localizedText('非交易时段', 'Outside Trading Hours')}
              </Tag>
            </div>
          </Card>
        </Col>
        <Col className="model-overview-col" xs={24} md={12} xl={6}>
          <Card className="surface-card compact-card model-overview-card" loading={loading}>
            <Statistic
              title={t('modelOperations.latestTraining')}
              value={overview?.latestTrainingRun?.status || localizedText('无记录', 'No Record')}
              prefix={<CheckCircleOutlined />}
            />
            <div className="model-overview-card-footer text-xs text-gray-400">
              {formatDateTime(overview?.latestTrainingRun?.createdAt)}
            </div>
          </Card>
        </Col>
      </Row>

      {latestTraining && (
      <Card className="surface-card" title={t('modelOperations.trainingProgress')}>
          <Space direction="vertical" size="large" className="w-full">
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
        title={`预测批次（共 ${predictionBatchTotal} 个）`}
      >
        <Table<PredictionBatch>
          rowKey="id"
          columns={predictionBatchColumns}
          dataSource={predictionBatches}
          loading={predictionLoading}
          pagination={false}
          scroll={{ x: 1400 }}
          rowClassName={(record) => record.id === selectedPredictionBatchId ? 'bg-blue-500/5' : ''}
        />
      </Card>

      <DetailModal
        title={
          <Space size="small" wrap>
            <span>预测批次详情</span>
            {selectedPredictionBatch && <Tag color={statusColors[selectedPredictionBatch.status] ?? 'default'}>{selectedPredictionBatch.status}</Tag>}
          </Space>
        }
        open={predictionDetailsOpen}
        onCancel={() => setPredictionDetailsOpen(false)}
      >
        {selectedPredictionBatch && (
          <Space direction="vertical" size="middle" className="w-full">
            <div className="prediction-detail-hero">
              <div className="prediction-detail-hero-main">
                <Text type="secondary">批次 ID</Text>
                <Text copyable className="prediction-detail-id">{selectedPredictionBatch.id}</Text>
                <Text type="secondary" className="prediction-detail-version">{selectedPredictionBatch.modelVersion || '未关联模型版本'}</Text>
              </div>
              <Space wrap size={[6, 6]}>
                <Tag color={selectedPredictionBatch.tradingEligibility === 'TRADE_ELIGIBLE' ? 'green' : 'orange'}>
                  {selectedPredictionBatch.tradingEligibility || 'DISABLED'}
                </Tag>
                <Tag color={selectedPredictionBatch.calendarFallback ? 'warning' : 'green'}>
                  {selectedPredictionBatch.calendarFallback ? '工作日推算' : '正式日历'}
                </Tag>
              </Space>
            </div>

            <div className="prediction-detail-metrics">
              <div><span>总股票数</span><strong>{selectedPredictionBatch.totalStocks}</strong></div>
              <div><span>已处理</span><strong>{selectedPredictionBatch.processedStocks}</strong></div>
              <div><span>成功 / 失败</span><strong>{selectedPredictionBatch.successCount} / {selectedPredictionBatch.failedCount}</strong></div>
              <div><span>覆盖率</span><strong>{formatPercent(selectedPredictionBatch.coverageRate)}</strong></div>
              <div><span>评估完成率</span><strong>{formatPercent(selectedPredictionBatch.evaluationRate)}</strong></div>
            </div>

            <DetailSection title="预测范围">
              <DetailItem label="模型名称">{selectedPredictionBatch.modelName || '-'}</DetailItem>
              <DetailItem label="模型 ID"><Text copyable className="detail-break">{selectedPredictionBatch.modelId || '-'}</Text></DetailItem>
              <DetailItem label="基准日">{selectedPredictionBatch.baseTradeDate || '-'}</DetailItem>
              <DetailItem label="目标日">{selectedPredictionBatch.targetTradeDate || '-'}</DetailItem>
              <DetailItem label="信息截止">{formatDateTime(selectedPredictionBatch.informationCutoffAt)}</DetailItem>
              <DetailItem label="触发方式">{selectedPredictionBatch.triggeredBy || '-'}</DetailItem>
            </DetailSection>

            <DetailSection title="执行时间">
              <DetailItem label="创建时间">{formatDateTime(selectedPredictionBatch.createdAt)}</DetailItem>
              <DetailItem label="开始时间">{formatDateTime(selectedPredictionBatch.startedAt)}</DetailItem>
              <DetailItem label="完成时间">{formatDateTime(selectedPredictionBatch.finishedAt)}</DetailItem>
              <DetailItem label="更新时间">{formatDateTime(selectedPredictionBatch.updatedAt)}</DetailItem>
            </DetailSection>

            <DetailSection title="执行状态">
              <DetailItem label="当前状态"><Tag color={statusColors[selectedPredictionBatch.status] ?? 'default'}>{selectedPredictionBatch.status}</Tag></DetailItem>
              <DetailItem label="交易门禁"><Tag color={selectedPredictionBatch.tradingEligibility === 'TRADE_ELIGIBLE' ? 'green' : 'orange'}>{selectedPredictionBatch.tradingEligibility || 'DISABLED'}</Tag></DetailItem>
              <DetailItem label="评估状态">{selectedPredictionBatch.abilityLevel || '-'}</DetailItem>
              <DetailItem label="未获取行情">{selectedPredictionBatch.noMarketDataCount}</DetailItem>
              <DetailItem label="已评估数量">{selectedPredictionBatch.evaluatedCount}</DetailItem>
              <DetailItem label="进度信息" wide><span className="detail-break">{selectedPredictionBatch.progressMessage || '-'}</span></DetailItem>
            </DetailSection>

            {(selectedPredictionBatch.tradingEligibilityReason || selectedPredictionBatch.errorMessage || selectedPredictionBatch.abilityMessage) && (
              <DetailSection title="诊断信息">
                <DetailItem label="门禁原因" wide><span className="detail-message">{selectedPredictionBatch.tradingEligibilityReason || '-'}</span></DetailItem>
                <DetailItem label="能力说明" wide><span className="detail-message">{selectedPredictionBatch.abilityMessage || '-'}</span></DetailItem>
                <DetailItem label="错误信息" wide><span className="detail-message detail-error">{selectedPredictionBatch.errorMessage || '-'}</span></DetailItem>
              </DetailSection>
            )}
          </Space>
        )}
      </DetailModal>
      <WideTableModal
        title={`评估：${selectedPredictionBatch?.targetTradeDate || selectedPredictionBatch?.id || '-'}`}
        open={predictionAnalysisModalOpen}
        onCancel={() => setPredictionAnalysisModalOpen(false)}
      >
        {modelAbility ? (
          <Space direction="vertical" size="large" className="w-full">
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
        ) : <Empty description={predictionAnalysisLoading ? localizedText('正在加载预测分析', 'Loading prediction analysis') : localizedText('暂无预测分析', 'No prediction analysis')} />}
      </WideTableModal>
      <WideTableModal
        title={`预测效果：${selectedPredictionBatch?.targetTradeDate || selectedPredictionBatch?.id || '-'}`}
        open={predictionResultModalOpen}
        onCancel={() => setPredictionResultModalOpen(false)}
      >
        <Table<PredictionResult>
          rowKey="id"
          columns={predictionResultColumns}
          dataSource={predictionResults}
          loading={predictionLoading}
          locale={{ emptyText: <Empty description={t('modelOperations.predictionEmpty')} /> }}
          scroll={{ x: 1940, y: 520 }}
          pagination={{
            current: predictionResultPage,
            pageSize: 20,
            total: predictionResultTotal,
            showSizeChanger: false,
            showTotal: (total) => `共 ${total} 条预测`,
            onChange: (current) => {
              setPredictionResultPage(current);
              if (selectedPredictionBatchId) {
                void loadPredictionResults(selectedPredictionBatchId, current);
              }
            },
          }}
        />
      </WideTableModal>

      <Card className="surface-card" title={t('modelOperations.modelVersions')}>
        <Table<ModelVersion>
          rowKey="id"
          columns={versionColumns}
          dataSource={versions}
          loading={loading}
          locale={{ emptyText: <Empty description={t('modelOperations.versionsEmpty')} /> }}
          scroll={{ x: 1480 }}
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

      <WideTableModal
        title={`训练记录：${selectedTrainingVersion?.modelVersion || '-'}`}
        open={trainingRecordsOpen}
        onCancel={() => setTrainingRecordsOpen(false)}
      >
        <Table<TrainingRun>
          rowKey="id"
          columns={runColumns}
          dataSource={selectedVersionTrainingRuns}
          loading={trainingRecordsLoading}
          locale={{ emptyText: <Empty description={localizedText('该版本暂无训练记录', 'No training records for this version')} /> }}
          scroll={{ x: 1980, y: 520 }}
          pagination={false}
        />
      </WideTableModal>

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

      <DetailModal
        title="模型版本详情"
        open={detailsOpen}
        onCancel={() => setDetailsOpen(false)}
      >
        {selectedVersion && (
          <Space direction="vertical" size="large" className="w-full">
            {detailsLoading && <Progress percent={50} status="active" showInfo={false} />}
            <div className="detail-modal-hero">
              <div>
                <Text type="secondary">模型版本</Text>
                <Title level={4} className="!mb-1">{selectedVersion.modelVersion}</Title>
                <Text type="secondary">{selectedVersion.modelName}</Text>
              </div>
              <Tag color={statusColors[selectedVersion.status]}>{selectedVersion.status}</Tag>
            </div>
            <DetailSection title="版本信息">
              <DetailItem label="模型名称">{selectedVersion.modelName}</DetailItem>
              <DetailItem label="创建时间">{formatDateTime(selectedVersion.createdAt)}</DetailItem>
              <DetailItem label="执行引擎">{selectedVersion.engineName || '-'}</DetailItem>
              <DetailItem label="引擎版本">{selectedVersion.engineVersion || '-'}</DetailItem>
              <DetailItem label="DJL 版本">{selectedVersion.djlVersion || '-'}</DetailItem>
              <DetailItem label="参数大小">{formatBytes(selectedVersion.parameterSize)}</DetailItem>
              <DetailItem label="训练损失">{formatLoss(selectedVersion.trainLoss)}</DetailItem>
              <DetailItem label="验证损失">{formatLoss(selectedVersion.valLoss)}</DetailItem>
              <DetailItem label="参数摘要" wide><Text copyable className="detail-break">{selectedVersion.parameterSha256 || '-'}</Text></DetailItem>
            </DetailSection>
            {[
              ['训练配置快照', selectedVersion.trainingConfigJson],
              ['输入契约快照', selectedVersion.inputContractJson],
              ['模型指标快照', selectedVersion.metricsJson],
            ].map((snapshot) => {
              const label = snapshot[0] || '快照';
              const value = snapshot[1];
              return (
              <DetailSection key={label} title={label} bodyClassName="detail-section-body-full">
                <div className="detail-json-block">
                  <pre>
                    {value || '无'}
                  </pre>
                </div>
              </DetailSection>
              );
            })}
          </Space>
        )}
      </DetailModal>
    </div>
  );

};

export default ModelOperations;
// AI_GENERATE_END -------------------------------------------
