// AI_GENERATE_START-----------------------------------------------------------------------
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { formatDateTime } from '../../utils/dateTime';
import SentimentModelStatusPanel from './SentimentModelStatusPanel';
import {
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Form,
  InputNumber,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import {
  CloudServerOutlined,
  EyeOutlined,
  ExperimentOutlined,
  RollbackOutlined,
  StopOutlined,
} from '@ant-design/icons';
import request from '../../utils/request';
import { getApiErrorMessage, readApiData, type ApiResponse } from '../../utils/api';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import { useTranslation } from 'react-i18next';
import { localizedText } from '../../utils/localizedText';

const { Title, Text } = Typography;

const AUTO_REFRESH_INTERVAL_MS = 10_000;
const DEFAULT_OPERATOR_NAME = 'web-console';

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
  architectureType?: string;
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
  trainingRunning: boolean;
  activeModelHealthy: boolean;
  activeVersionScore?: ModelScoreSummary;
  temporalEnsemble?: TemporalModelEnsembleStatus;
}

interface TemporalModelEnsembleStatus {
  masterShadowEnabled: boolean;
  productionBlendConfigured: boolean;
  status: 'OBSERVING' | 'ACTIVE' | 'DEGRADED' | 'UNAVAILABLE';
  rankingLstmModelId?: string;
  masterModelId?: string;
  rankingLstmWeight: number;
  masterWeight: number;
  targetRankingLstmWeight: number;
  targetMasterWeight: number;
  currentCombinationQualityScore?: number;
  bestCombinationQualityScore?: number;
  weightVersion: number;
  effectiveTradeDate?: string;
  evaluatedTradingDays: number;
  evaluatedSamples: number;
  rankingLstmRankIc?: number;
  masterRankIc?: number;
  rankingLstmIcIr?: number;
  masterIcIr?: number;
  rankingLstmQualityScore?: number;
  masterQualityScore?: number;
  decisionReason?: string;
  updatedAt?: string;
}

interface ModelScoreSummary {
  batchId: string;
  modelId: string;
  modelVersion: string;
  targetTradeDate?: string;
  evaluatedCount: number;
  coverageRate?: number;
  directionAccuracy?: number;
  topDecileDirectionAccuracy?: number;
  highConfidenceAccuracy?: number;
  spearmanRankIc?: number;
  abilityLevel?: string;
}

interface ModelScoreItem {
  label: string;
  value: string;
  hint: string;
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
  architectureType?: 'RANKING_LSTM' | 'MASTER_TRANSFORMER';
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
  evaluationQueueStatus?: string;
  evaluationWaitReason?: string;
  evaluationQueuedAt?: string;
  evaluationStartedAt?: string;
  evaluationFinishedAt?: string;
  evaluationErrorMessage?: string;
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
  architectureType: 'RANKING_LSTM',
};

const statusColors: Record<string, string> = {
  ACTIVE: 'green',
  CANDIDATE: 'gold',
  RETIRED: 'default',
  QUEUED: 'blue',
  RUNNING: 'processing',
  SUCCEEDED: 'green',
  FAILED: 'red',
  STOPPING: 'orange',
  WAITING_DATA: 'gold',
  CANCELLED: 'default',
  COMPLETED: 'green',
  PARTIAL: 'orange',
  EVALUATED: 'cyan',
  WAITING: 'gold',
};

const evaluationStatusColors: Record<string, string> = {
  PENDING: 'blue',
  EVALUATED: 'green',
  NO_MARKET_DATA: 'orange',
};

const evaluationWaitReasonLabels: Record<string, [string, string]> = {
  PREDICTION_NOT_COMPLETED: ['预测尚未完成', 'Prediction has not completed'],
  TARGET_DATE_NOT_REACHED: ['目标交易日尚未到达', 'Target trading date has not arrived'],
  MARKET_DATA_COVERAGE_LOW: ['目标交易日行情覆盖率不足', 'Target-date market data coverage is insufficient'],
  PENDING_MARKET_DATA: ['仍有预测明细等待目标日行情', 'Some prediction details are still waiting for market data'],
};

const formatEvaluationWaitReason = (reason?: string) => {
  if (!reason) {
    return undefined;
  }
  const labels = evaluationWaitReasonLabels[reason];
  return labels ? localizedText(labels[0], labels[1]) : reason;
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
  CHECKING_DATA: '检查数据',
  BACKFILLING_DATA: '回填数据',
  RECHECKING_DATA: '复检数据',
  PREPARING_SNAPSHOT: '提取快照',
  SNAPSHOT_READY: '快照已就绪',
  PREPARING_DATA: '准备数据',
  PROCESSING_SAMPLES: '构建样本',
  TRAINING: '模型训练',
  SAVING_MODEL: '保存模型',
  OFFLINE_EVALUATING: '离线评估',
  CANDIDATE_REGISTERED: '候选已登记',
  WAITING_DATA: '等待数据',
  COMPLETED: '已完成',
  FAILED: '已失败',
};

const progressStageLabelsEn: Record<string, string> = {
  QUEUED: 'Queued',
  CHECKING_DATA: 'Checking Data',
  BACKFILLING_DATA: 'Backfilling Data',
  RECHECKING_DATA: 'Rechecking Data',
  PREPARING_SNAPSHOT: 'Preparing Snapshot',
  SNAPSHOT_READY: 'Snapshot Ready',
  PREPARING_DATA: 'Preparing Data',
  PROCESSING_SAMPLES: 'Building Samples',
  TRAINING: 'Training Model',
  SAVING_MODEL: 'Saving Model',
  OFFLINE_EVALUATING: 'Offline Evaluation',
  CANDIDATE_REGISTERED: 'Candidate Registered',
  WAITING_DATA: 'Waiting for Data',
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
  const [overviewLoading, setOverviewLoading] = useState(true);
  const [versionsLoading, setVersionsLoading] = useState(true);
  const [operating, setOperating] = useState(false);
  const [trainingModalOpen, setTrainingModalOpen] = useState(false);
  const [trainingProgressOpen, setTrainingProgressOpen] = useState(false);
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
  const [predictionBatchPage, setPredictionBatchPage] = useState(1);
  const [predictionBatchPageSize, setPredictionBatchPageSize] = useState(10);
  const [selectedPredictionBatchId, setSelectedPredictionBatchId] = useState<string>();
  const [predictionResults, setPredictionResults] = useState<PredictionResult[]>([]);
  const [predictionResultTotal, setPredictionResultTotal] = useState(0);
  const [predictionResultPage, setPredictionResultPage] = useState(1);
  const [predictionResultModalOpen, setPredictionResultModalOpen] = useState(false);
  const [predictionDetailsOpen, setPredictionDetailsOpen] = useState(false);
  const [predictionDetailsLoading, setPredictionDetailsLoading] = useState(false);
  const [modelAbility, setModelAbility] = useState<ModelAbility | null>(null);
  const [predictionPollingPaused, setPredictionPollingPaused] = useState(false);
  const predictionDataRequestIdRef = useRef(0);
  const selectedPredictionBatchIdRef = useRef<string | undefined>(undefined);

  const updateSelectedPredictionBatchId = useCallback((batchId?: string) => {
    selectedPredictionBatchIdRef.current = batchId;
    setSelectedPredictionBatchId(batchId);
  }, []);

  const loadOverview = useCallback(async (showLoading = false) => {
    if (showLoading) {
      setOverviewLoading(true);
    }
    try {
      const response = await request.get('/modelOperations/overview') as ApiResponse<Overview>;
      setOverview(readApiData(response, localizedText('读取模型运维概览失败', 'Failed to load model operations overview')));
    } finally {
      if (showLoading) {
        setOverviewLoading(false);
      }
    }
  }, []);

  const loadVersions = useCallback(async (current: number) => {
    setVersionsLoading(true);
    try {
      const response = await request.get('/modelVersions', {
        params: { current, pageSize },
      }) as ApiResponse<PageResponse<ModelVersion>>;
      const page = readApiData(response, localizedText('读取模型版本失败', 'Failed to load model versions'));
      setVersions(page.items ?? []);
      setVersionTotal(page.total);
      setVersionPage(page.current);
    } finally {
      setVersionsLoading(false);
    }
  }, [pageSize]);

  const loadPredictionResults = useCallback(async (
    batchId: string,
    current = 1,
  ) => {
    const response = await request.get('/modelPredictions/results', {
      params: { batchId, current, pageSize: 20 },
    }) as ApiResponse<PageResponse<PredictionResult>>;
    const resultPage = readApiData(response, localizedText('读取预测结果失败', 'Failed to load prediction results'));
    setPredictionResults(resultPage.items ?? []);
    setPredictionResultTotal(resultPage.total);
    setPredictionResultPage(resultPage.current);
  }, []);

  const loadPredictionAbility = useCallback(async (batchId: string) => {
    const response = await request.get('/modelPredictions/ability', {
      params: { batchId },
    }) as ApiResponse<ModelAbility>;
    setModelAbility(readApiData(response, localizedText('读取批次评估信息失败', 'Failed to load batch evaluation')));
  }, []);

  const loadPredictionData = useCallback(async (
    current = 1,
    currentPageSize = 10,
    showLoading = true,
  ) => {
    const requestId = predictionDataRequestIdRef.current + 1;
    predictionDataRequestIdRef.current = requestId;
    if (showLoading) {
      setPredictionLoading(true);
    }
    try {
      const response = await request.get('/modelPredictions/batches', {
        params: { current, pageSize: currentPageSize },
      }) as ApiResponse<PageResponse<PredictionBatch>>;
      const page = readApiData(response, localizedText('读取预测批次失败', 'Failed to load prediction batches'));
      if (requestId !== predictionDataRequestIdRef.current) {
        return;
      }
      const batches = page.items ?? [];
      setPredictionBatches(batches);
      setPredictionBatchTotal(page.total);
      setPredictionBatchPage(page.current);
      setPredictionBatchPageSize(page.pageSize);
      const currentBatchId = selectedPredictionBatchIdRef.current;
      const batchId = currentBatchId && batches.some((item) => item.id === currentBatchId)
        ? currentBatchId
        : batches[0]?.id;
      updateSelectedPredictionBatchId(batchId);
      if (!batchId) {
        setPredictionResults([]);
        setPredictionResultTotal(0);
        setModelAbility(null);
      }
    } finally {
      if (showLoading) {
        setPredictionLoading(false);
      }
    }
  }, [updateSelectedPredictionBatchId]);

  useEffect(() => {
    void Promise.all([loadOverview(true), loadPredictionData(1, 10)]).catch((cause) => {
      message.error(getApiErrorMessage(cause, localizedText('读取模型运维数据失败', 'Failed to load model operations data')));
    });
  }, [loadOverview, loadPredictionData]);

  useEffect(() => {
    void loadVersions(versionPage).catch((cause) => {
      message.error(getApiErrorMessage(cause, localizedText('读取模型版本失败', 'Failed to load model versions')));
    });
  }, [loadVersions, versionPage]);

  useEffect(() => {
    if (!overview?.trainingRunning) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void loadOverview().catch((cause) => {
        message.error(getApiErrorMessage(cause, localizedText('刷新训练进度失败', 'Failed to refresh training progress')));
      });
    }, AUTO_REFRESH_INTERVAL_MS);
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
    const running = predictionBatches.some((item) => item.status === 'QUEUED'
      || item.status === 'RUNNING'
      || item.evaluationQueueStatus === 'QUEUED'
      || item.evaluationQueueStatus === 'RUNNING');
    if (!running || predictionPollingPaused) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void loadPredictionData(predictionBatchPage, predictionBatchPageSize, false).catch((cause) => {
        setPredictionPollingPaused(true);
        message.error(getApiErrorMessage(cause, localizedText('刷新预测进度失败，已暂停自动刷新', 'Failed to refresh prediction progress; automatic refresh paused')));
      });
    }, AUTO_REFRESH_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [loadPredictionData, predictionBatchPage, predictionBatchPageSize, predictionBatches, predictionPollingPaused]);

  const refreshAfterOperation = async () => {
    await Promise.all([loadOverview(), loadVersions(1)]);
  };

  const submitTraining = async () => {
    const values = await form.validateFields();
    setOperating(true);
    try {
      const response = await request.post('/modelTrainingRuns/start', {
        ...values,
        operatorName: DEFAULT_OPERATOR_NAME,
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
        params: { operatorName: DEFAULT_OPERATOR_NAME },
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
    updateSelectedPredictionBatchId(batchId);
    setPredictionResultPage(1);
    setPredictionResults([]);
    setPredictionResultTotal(0);
    setPredictionResultModalOpen(true);
    void loadPredictionResults(batchId, 1);
  };

  const openPredictionDetails = (batchId: string) => {
    updateSelectedPredictionBatchId(batchId);
    setModelAbility(null);
    setPredictionDetailsLoading(true);
    setPredictionDetailsOpen(true);
    void loadPredictionAbility(batchId)
      .catch((cause) => {
        message.error(getApiErrorMessage(cause, localizedText('读取批次评估信息失败', 'Failed to load batch evaluation')));
      })
      .finally(() => setPredictionDetailsLoading(false));
  };

  const retryPredictionBatch = async (batchId: string) => {
    setOperating(true);
    try {
      const response = await request.post(
        `/modelPredictions/retryPrediction/${encodeURIComponent(batchId)}`,
      ) as ApiResponse<PredictionOperationResponse>;
      const result = readApiData(response, localizedText('重新提交预测失败', 'Failed to retry prediction'));
      message.success(result.message);
      setPredictionPollingPaused(false);
      await loadPredictionData(predictionBatchPage, predictionBatchPageSize);
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('重新提交预测失败', 'Failed to retry prediction')));
    } finally {
      setOperating(false);
    }
  };

  const retryEvaluationBatch = async (batchId: string) => {
    setOperating(true);
    try {
      const response = await request.post(
        `/modelPredictions/retryEvaluation/${encodeURIComponent(batchId)}`,
      ) as ApiResponse<PredictionOperationResponse>;
      const result = readApiData(response, localizedText('重新提交评估失败', 'Failed to retry evaluation'));
      message.success(result.message);
      setPredictionPollingPaused(false);
      await loadPredictionData(predictionBatchPage, predictionBatchPageSize);
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('重新提交评估失败', 'Failed to retry evaluation')));
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
    { title: localizedText('架构', 'Architecture'), dataIndex: 'architectureType', key: 'architectureType', width: 190, render: (value?: string) => value || '-' },
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
      render: (_, record) => (
        <Space size="small" wrap>
          <Button type="link" size="small" className="!px-0" onClick={() => void showTrainingRecords(record)}>
            {localizedText('训练记录', 'Training Records')}
          </Button>
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
    { title: localizedText('数据快照', 'Dataset Snapshot'), dataIndex: 'datasetSnapshotId', key: 'datasetSnapshotId', width: 200, ellipsis: true, render: (value?: string) => value ?? '-' },
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
          <Button type="link" className="!px-0" onClick={() => openPredictionDetails(record.id)}>
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
      title: localizedText('评估队列', 'Evaluation Queue'), dataIndex: 'evaluationQueueStatus', key: 'evaluationQueueStatus', width: 120,
      render: (value: string | undefined, record) => (
        <Tooltip title={formatEvaluationWaitReason(record.evaluationWaitReason)}>
          <Tag color={statusColors[value ?? ''] ?? 'default'}>{value || 'WAITING'}</Tag>
        </Tooltip>
      ),
    },
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
      title: localizedText('操作', 'Actions'), key: 'actions', width: 300,
      render: (_, record) => {
        const predictionRetryEnabled = record.status === 'FAILED';
        const evaluationRetryEnabled = ['COMPLETED', 'PARTIAL', 'EVALUATED'].includes(record.status)
          && record.evaluationQueueStatus === 'FAILED';
        return (
          <Space size="small">
            <Button type="link" size="small" className="!px-0" onClick={() => openPredictionDetails(record.id)}>
              {localizedText('详情', 'Details')}
            </Button>
            <Button type="link" size="small" className="!px-0" onClick={() => openPredictionResults(record.id)}>
              {localizedText('结果', 'Results')}
            </Button>
            <Tooltip title={predictionRetryEnabled ? undefined : localizedText('仅预测失败的批次可重新预测', 'Only failed predictions can be retried')}>
              <span>
                <Button
                  type="link"
                  size="small"
                  className="!px-0"
                  disabled={operating || !predictionRetryEnabled}
                  onClick={() => void retryPredictionBatch(record.id)}
                >
                  {localizedText('重新预测', 'Retry Prediction')}
                </Button>
              </span>
            </Tooltip>
            <Tooltip title={evaluationRetryEnabled ? undefined : localizedText('仅评估失败且预测已完成的批次可重新评估', 'Only failed evaluations with completed predictions can be retried')}>
              <span>
                <Button
                  type="link"
                  size="small"
                  className="!px-0"
                  disabled={operating || !evaluationRetryEnabled}
                  onClick={() => void retryEvaluationBatch(record.id)}
                >
                  {localizedText('重新评估', 'Retry Evaluation')}
                </Button>
              </span>
            </Tooltip>
          </Space>
        );
      },
    },
  ];

  const handlePredictionBatchTableChange = (pagination: TablePaginationConfig) => {
    const nextPageSize = pagination.pageSize ?? 10;
    const nextPage = nextPageSize === predictionBatchPageSize ? pagination.current ?? 1 : 1;
    setPredictionBatchPage(nextPage);
    setPredictionBatchPageSize(nextPageSize);
    void loadPredictionData(nextPage, nextPageSize).catch((cause) => {
      message.error(getApiErrorMessage(cause, localizedText('读取预测批次失败', 'Failed to load prediction batches')));
    });
  };

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
  const trainingInProgress = Boolean(
    overview?.trainingRunning
      && latestTraining
      && ['QUEUED', 'RUNNING', 'STOPPING'].includes(latestTraining.status),
  );
  const selectedPredictionBatch = predictionBatches.find((item) => item.id === selectedPredictionBatchId);
  const displayedElapsedSeconds = latestTraining?.startedAt && latestTraining.status === 'RUNNING'
    ? Math.max(
        latestTraining.elapsedSeconds ?? 0,
        Math.floor((progressClock - new Date(latestTraining.startedAt).getTime()) / 1000),
      )
    : latestTraining?.elapsedSeconds;
  const activeModelScores: ModelScoreItem[] = [
    {
      label: localizedText('方向准确率', 'Direction Accuracy'),
      value: formatPercent(overview?.activeVersionScore?.directionAccuracy),
      hint: localizedText('全部已评估样本的涨跌方向判断准确率', 'Direction accuracy across all evaluated samples'),
    },
    {
      label: localizedText('Top 10% 命中率', 'Top 10% Hit Rate'),
      value: formatPercent(overview?.activeVersionScore?.topDecileDirectionAccuracy),
      hint: localizedText('预测收益排名前 10% 股票的上涨命中率', 'Upside hit rate among the top 10% predicted returns'),
    },
    {
      label: localizedText('高置信命中率', 'High-confidence Hit Rate'),
      value: formatPercent(overview?.activeVersionScore?.highConfidenceAccuracy),
      hint: localizedText('高置信度方向预测的实际命中率', 'Actual hit rate for high-confidence direction predictions'),
    },
    {
      label: localizedText('排序 IC', 'Rank IC'),
      value: formatNumber(overview?.activeVersionScore?.spearmanRankIc, 3),
      hint: localizedText('预测收益排序与实际收益排序的 Spearman 相关系数', 'Spearman correlation between predicted and actual return rankings'),
    },
  ];

  return (
    <div className="app-page">
      <Row className="model-overview-cards" gutter={[16, 16]} align="stretch">
        <Col className="model-overview-col" xs={24} xl={17}>
          <Card className="surface-card compact-card model-overview-card" loading={overviewLoading}>
            <div className="model-version-heading">
              <div className="model-version-identity">
                <span className="model-version-label">{t('modelOperations.productionVersion')}</span>
                <div className="model-version-name">
                  <CloudServerOutlined />
                  <Text ellipsis={{ tooltip: overview?.activeVersion?.modelVersion }}>
                    {overview?.activeVersion?.modelVersion || localizedText('未激活', 'Not Active')}
                  </Text>
                </div>
              </div>
              <Space wrap size={[6, 6]}>
                <Tag color={overview?.activeModelHealthy ? 'green' : 'red'}>
                  {overview?.activeModelHealthy ? localizedText('校验正常', 'Healthy') : localizedText('未通过校验', 'Validation Failed')}
                </Tag>
                {overview?.activeVersionScore?.abilityLevel && <Tag color="blue">{overview.activeVersionScore.abilityLevel}</Tag>}
              </Space>
            </div>
            <div className="model-score-grid">
              {activeModelScores.map((score) => (
                <Tooltip key={score.label} title={score.hint}>
                  <div className="model-score-item">
                    <span>{score.label}</span>
                    <strong>{score.value}</strong>
                  </div>
                </Tooltip>
              ))}
            </div>
            <div className="model-score-context">
              {overview?.activeVersionScore ? (
                <>
                  <span>{localizedText('评估样本', 'Evaluated Samples')} <strong>{overview.activeVersionScore.evaluatedCount}</strong></span>
                  <span>{localizedText('目标交易日', 'Target Date')} <strong>{overview.activeVersionScore.targetTradeDate || '-'}</strong></span>
                  <span>{localizedText('预测覆盖率', 'Coverage')} <strong>{formatPercent(overview.activeVersionScore.coverageRate)}</strong></span>
                </>
              ) : (
                <span className="model-score-empty">
                  {localizedText('当前生产版本暂无已完成的评分数据', 'No completed score data for the production version')}
                </span>
              )}
            </div>
            <Descriptions className="mt-4" bordered size="small" column={{ xs: 1, sm: 2, lg: 4 }}>
              <Descriptions.Item label={localizedText('MASTER 影子', 'MASTER Shadow')}>
                <Tag color={overview?.temporalEnsemble?.masterShadowEnabled ? 'processing' : 'default'}>
                  {overview?.temporalEnsemble?.masterShadowEnabled ? localizedText('已开启', 'Enabled') : localizedText('已关闭', 'Disabled')}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label={localizedText('自动选择状态', 'Auto Selection')}>
                <Tag color={overview?.temporalEnsemble?.status === 'ACTIVE'
                  ? 'green'
                  : overview?.temporalEnsemble?.status === 'DEGRADED'
                    ? 'orange'
                    : overview?.temporalEnsemble?.status === 'UNAVAILABLE'
                      ? 'red'
                      : 'processing'}>
                  {overview?.temporalEnsemble?.productionBlendConfigured
                    ? overview?.temporalEnsemble?.status || 'OBSERVING'
                    : localizedText('配置关闭', 'Disabled')}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label={localizedText('当前权重', 'Current Weights')}>
                LSTM {formatPercent(overview?.temporalEnsemble?.rankingLstmWeight)} / MASTER {formatPercent(overview?.temporalEnsemble?.masterWeight)}
              </Descriptions.Item>
              <Descriptions.Item label={localizedText('目标权重', 'Target Weights')}>
                LSTM {formatPercent(overview?.temporalEnsemble?.targetRankingLstmWeight)} / MASTER {formatPercent(overview?.temporalEnsemble?.targetMasterWeight)}
              </Descriptions.Item>
              <Descriptions.Item label={localizedText('评估窗口', 'Evaluation Window')}>
                {overview?.temporalEnsemble?.evaluatedTradingDays ?? 0} {localizedText('日', 'days')} / {overview?.temporalEnsemble?.evaluatedSamples ?? 0} {localizedText('条', 'samples')}
              </Descriptions.Item>
              <Descriptions.Item label={localizedText('组合评分', 'Combination Scores')} span={2}>
                {localizedText('当前', 'Current')} {formatNumber(overview?.temporalEnsemble?.currentCombinationQualityScore)} / {localizedText('最优', 'Best')} {formatNumber(overview?.temporalEnsemble?.bestCombinationQualityScore)}
              </Descriptions.Item>
              <Descriptions.Item label={localizedText('权重版本', 'Weight Version')}>
                v{overview?.temporalEnsemble?.weightVersion ?? 0}
              </Descriptions.Item>
              <Descriptions.Item label={localizedText('生效交易日', 'Effective Date')}>
                {overview?.temporalEnsemble?.effectiveTradeDate || '-'}
              </Descriptions.Item>
              <Descriptions.Item label={localizedText('自动决策', 'Automatic Decision')} span={2}>
                {overview?.temporalEnsemble?.decisionReason || '-'}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
        <Col className="model-overview-col" xs={24} xl={7}>
          <Card className="surface-card compact-card model-overview-card model-previous-card" loading={overviewLoading}>
            <div className="model-version-heading">
              <div className="model-version-identity">
                <span className="model-version-label">{localizedText('上一版本', 'Previous Version')}</span>
                <div className="model-version-name">
                  <RollbackOutlined />
                  <Text ellipsis={{ tooltip: overview?.previousVersion?.modelVersion }}>
                    {overview?.previousVersion?.modelVersion || localizedText('无可回滚版本', 'No Rollback Version')}
                  </Text>
                </div>
              </div>
              {overview?.previousVersion?.status && (
                <Tag color={statusColors[overview.previousVersion.status] ?? 'default'}>
                  {overview.previousVersion.status}
                </Tag>
              )}
            </div>
            <div className="model-previous-summary">
              <div>
                <span>{localizedText('模型架构', 'Architecture')}</span>
                <strong>{overview?.previousVersion?.architectureType || '-'}</strong>
              </div>
              <div>
                <span>{localizedText('验证损失', 'Validation Loss')}</span>
                <strong>{formatLoss(overview?.previousVersion?.valLoss)}</strong>
              </div>
              <div>
                <span>{localizedText('训练轮次', 'Epochs')}</span>
                <strong>{overview?.previousVersion?.epoch ?? '-'}</strong>
              </div>
              <div>
                <span>{localizedText('创建时间', 'Created At')}</span>
                <strong>{formatDateTime(overview?.previousVersion?.createdAt)}</strong>
              </div>
            </div>
            <div className="model-overview-actions">
              {latestTraining && (
                <Button icon={<EyeOutlined />} onClick={() => setTrainingProgressOpen(true)}>
                  {trainingInProgress
                    ? localizedText('查看训练进度', 'View Training Progress')
                    : localizedText('查看最近训练', 'View Latest Training')}
                </Button>
              )}
              <Button
                type="primary"
                icon={<ExperimentOutlined />}
                loading={operating}
                disabled={operating || trainingInProgress}
                onClick={() => {
                  form.setFieldsValue(defaultTrainingValues);
                  setTrainingModalOpen(true);
                }}
              >
                {localizedText('触发训练', 'Start Training')}
              </Button>
            </div>
          </Card>
        </Col>
      </Row>

      <SentimentModelStatusPanel />

      <DetailModal
        title={
          <Space size="small" wrap>
            <span>{t('modelOperations.trainingProgress')}</span>
            {latestTraining && <Tag color={statusColors[latestTraining.status] ?? 'default'}>{latestTraining.status}</Tag>}
          </Space>
        }
        open={trainingProgressOpen}
        onCancel={() => setTrainingProgressOpen(false)}
      >
        {latestTraining && (
          <Space direction="vertical" size="large" className="w-full">
            <div className="flex flex-wrap items-center gap-2">
              <Tag color={statusColors[latestTraining.status] ?? 'default'}>
                {latestTraining.status}
              </Tag>
              <Tag color="blue">
                {(localizedText(progressStageLabels[latestTraining.progressStage ?? ''] ?? '', progressStageLabelsEn[latestTraining.progressStage ?? ''] ?? '')
                    || latestTraining.progressStage)
                    ?? localizedText('等待进度', 'Waiting for Progress')}
              </Tag>
              <Text>
                {latestTraining.progressMessage || localizedText('等待训练进度更新', 'Waiting for training progress')}
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
              percent={latestTraining.progressPercent ?? (latestTraining.status === 'SUCCEEDED' ? 100 : 0)}
              status={latestTraining.status === 'FAILED'
                ? 'exception'
                : latestTraining.status === 'SUCCEEDED' ? 'success' : 'active'}
            />
            <Descriptions bordered size="small" column={{ xs: 1, sm: 2, lg: 4 }}>
              <Descriptions.Item label="训练轮次">
                {latestTraining.totalEpochs
                  ? `${latestTraining.currentEpoch ?? 0} / ${latestTraining.totalEpochs}`
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="已耗时">
                {formatDuration(displayedElapsedSeconds)}
              </Descriptions.Item>
              <Descriptions.Item label="预计剩余">
                {latestTraining.status === 'RUNNING' && !latestTraining.currentEpoch
                  ? '计算中'
                  : formatDuration(latestTraining.estimatedRemainingSeconds)}
              </Descriptions.Item>
              <Descriptions.Item label="最近更新">
                {formatDateTime(latestTraining.lastProgressAt || latestTraining.startedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="训练损失">
                {formatLoss(latestTraining.trainLoss)}
              </Descriptions.Item>
              <Descriptions.Item label="验证损失">
                {formatLoss(latestTraining.valLoss)}
              </Descriptions.Item>
              <Descriptions.Item label="有效股票">
                {latestTraining.trainedStocks ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="样本规模">
                {`${latestTraining.trainSamples ?? '-'} / ${latestTraining.valSamples ?? '-'}`}
              </Descriptions.Item>
              <Descriptions.Item label="数据快照">
                {latestTraining.datasetSnapshotId ?? '-'}
              </Descriptions.Item>
            </Descriptions>
          </Space>
        )}
      </DetailModal>


      <Card
        className="surface-card"
        title={`预测批次（共 ${predictionBatchTotal} 个）`}
      >
        <Table<PredictionBatch>
          rowKey="id"
          columns={predictionBatchColumns}
          dataSource={predictionBatches}
          loading={predictionLoading}
          pagination={{
            current: predictionBatchPage,
            pageSize: predictionBatchPageSize,
            total: predictionBatchTotal,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: (total) => localizedText(`共 ${total} 个批次`, `${total} batches`),
          }}
          onChange={handlePredictionBatchTableChange}
          scroll={{ x: 1520 }}
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

            <DetailSection title="批次信息">
              <DetailItem label="模型名称">{selectedPredictionBatch.modelName || '-'}</DetailItem>
              <DetailItem label="预测区间">{selectedPredictionBatch.baseTradeDate || '-'} 至 {selectedPredictionBatch.targetTradeDate || '-'}</DetailItem>
              <DetailItem label="触发方式">{selectedPredictionBatch.triggeredBy || '-'}</DetailItem>
              <DetailItem label="创建时间">{formatDateTime(selectedPredictionBatch.createdAt)}</DetailItem>
              <DetailItem label="完成时间">{formatDateTime(selectedPredictionBatch.finishedAt)}</DetailItem>
            </DetailSection>

            <DetailSection title="状态与评估">
              <DetailItem label="当前状态"><Tag color={statusColors[selectedPredictionBatch.status] ?? 'default'}>{selectedPredictionBatch.status}</Tag></DetailItem>
              <DetailItem label="评估队列"><Tag color={statusColors[selectedPredictionBatch.evaluationQueueStatus ?? ''] ?? 'default'}>{selectedPredictionBatch.evaluationQueueStatus || 'WAITING'}</Tag></DetailItem>
              <DetailItem label="已评估数量">{selectedPredictionBatch.evaluatedCount}</DetailItem>
              <DetailItem label="能力等级">{selectedPredictionBatch.abilityLevel || '-'}</DetailItem>
              {selectedPredictionBatch.evaluationWaitReason && (
                <DetailItem label="等待原因" wide><span className="detail-break">{formatEvaluationWaitReason(selectedPredictionBatch.evaluationWaitReason)}</span></DetailItem>
              )}
              <DetailItem label="进度信息" wide><span className="detail-break">{selectedPredictionBatch.progressMessage || '-'}</span></DetailItem>
            </DetailSection>

            {predictionDetailsLoading ? (
              <div className="flex justify-center py-6"><Spin /></div>
            ) : modelAbility && modelAbility.evaluatedCount > 0 ? (
              <DetailSection title="核心评估">
                <DetailItem label="方向准确率">{formatPercent(modelAbility.directionAccuracy)}</DetailItem>
                <DetailItem label="方向 AUC">{formatNumber(modelAbility.directionAuc)}</DetailItem>
                <DetailItem label="Brier Score">{formatNumber(modelAbility.brierScore)}</DetailItem>
                <DetailItem label="ECE">{formatPercent(modelAbility.expectedCalibrationError)}</DetailItem>
                <DetailItem label="收益 MAE">{formatPercent(modelAbility.returnMae)}</DetailItem>
                <DetailItem label="Spearman IC">{formatNumber(modelAbility.spearmanRankIc)}</DetailItem>
                <DetailItem label="Top 10% 实际收益">{formatPercent(modelAbility.topDecileAverageReturn)}</DetailItem>
                <DetailItem label="Top 10% 命中率">{formatPercent(modelAbility.topDecileDirectionAccuracy)}</DetailItem>
              </DetailSection>
            ) : null}

            {(selectedPredictionBatch.tradingEligibilityReason || selectedPredictionBatch.errorMessage
              || selectedPredictionBatch.evaluationErrorMessage || selectedPredictionBatch.abilityMessage) && (
              <DetailSection title="诊断信息">
                {selectedPredictionBatch.tradingEligibilityReason && (
                  <DetailItem label="门禁原因" wide><span className="detail-message">{selectedPredictionBatch.tradingEligibilityReason}</span></DetailItem>
                )}
                {selectedPredictionBatch.abilityMessage && (
                  <DetailItem label="能力说明" wide><span className="detail-message">{selectedPredictionBatch.abilityMessage}</span></DetailItem>
                )}
                {selectedPredictionBatch.errorMessage && (
                  <DetailItem label="错误信息" wide><span className="detail-message detail-error">{selectedPredictionBatch.errorMessage}</span></DetailItem>
                )}
                {selectedPredictionBatch.evaluationErrorMessage && (
                  <DetailItem label="评估错误" wide><span className="detail-message detail-error">{selectedPredictionBatch.evaluationErrorMessage}</span></DetailItem>
                )}
              </DetailSection>
            )}
          </Space>
        )}
      </DetailModal>
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
          loading={versionsLoading}
          locale={{ emptyText: <Empty description={t('modelOperations.versionsEmpty')} /> }}
          scroll={{ x: 1660 }}
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
          <Form.Item name="architectureType" label="候选模型架构" rules={[{ required: true, message: '请选择模型架构' }]}>
            <Select options={[
              { value: 'RANKING_LSTM', label: 'Ranking LSTM' },
              { value: 'MASTER_TRANSFORMER', label: 'MASTER Shadow Candidate' },
            ]} />
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
// AI_GENERATE_END-----------------------------------------------------------------------
