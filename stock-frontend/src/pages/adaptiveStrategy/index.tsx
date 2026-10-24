// AI_GENERATE_START----
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Descriptions,
  Drawer,
  Input,
  Modal,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  AlertOutlined,
  ApartmentOutlined,
  AuditOutlined,
  EyeOutlined,
  FileSearchOutlined,
  PauseCircleOutlined,
  ReloadOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import { getApiErrorMessage, readApiData, type ApiResponse } from '../../utils/api';
import request from '../../utils/request';

const { Text } = Typography;

interface HealthItem {
  key: string;
  label: string;
  status: 'HEALTHY' | 'WARNING' | 'BLOCKED' | 'UNAVAILABLE';
  detail: string;
}

interface StrategyVersion {
  role: 'ACTIVE' | 'PREVIOUS' | 'CANDIDATE';
  versionId: string;
  modelVersion?: string;
  status?: string;
  architectureType?: string;
  featureVersion?: string;
  meanRankIc?: number;
  rankIcIr?: number;
  topDecileNetReturn?: number;
  outputCollapsed?: boolean;
  createdAt?: string;
}

interface EvolutionEvent {
  type: string;
  title: string;
  status: string;
  detail: string;
  occurredAt?: string;
}

interface PerformanceSummary {
  capturedAt?: string;
  cumulativeReturnRate?: number;
  winRate?: number;
  averageReturnRate?: number;
  worstReturnRate?: number;
  closedTradeCount: number;
  drawdownAvailable: boolean;
  turnoverAvailable: boolean;
}

interface AdaptiveStrategyOverview {
  lifecycleStage: string;
  lifecycleDescription: string;
  strategyVersion?: string;
  currentMode?: string;
  strategyEnabled: boolean;
  targetTradeDate?: string;
  lastEvolutionAt?: string;
  automaticTradingPaused: boolean;
  emergencyStopReason?: string;
  emergencyStoppedAt?: string;
  emergencyStopOperator?: string;
  emergencyStopStrategyVersion?: string;
  emergencyStopResult?: string;
  healthItems: HealthItem[];
  versions: StrategyVersion[];
  evolutionEvents: EvolutionEvent[];
  performance?: PerformanceSummary;
  anomalies: string[];
}

interface StrategyConfigSnapshot {
  version?: string;
  mode: string;
  lstmWeight: number;
  sentimentWeight: number;
  topN: number;
  minScore: number;
  minimumListingYears: number;
  trailingStopWeight: number;
  trailingStopTolerance: number;
  rsiWeight: number;
  rsiOverboughtThreshold: number;
  volumeWeight: number;
  volumeShrinkThreshold: number;
  bollingerWeight: number;
  bollingerBreakoutThreshold: number;
  highReturnThreshold: number;
  normalReturnThreshold: number;
  lowReturnThreshold: number;
  lossReturnThreshold: number;
  enabled: boolean;
  updateTime?: string;
}

interface TradingCandidate {
  stockCode: string;
  stockName: string;
  currentPrice?: number;
  lstmScore: number;
  newsImpactScore: number;
  totalScore: number;
  rank: number;
  reason: string;
  held: boolean;
}

interface TradingCandidateBatch {
  candidateBatchId: string;
  predictionBatchId: string;
  modelVersion: string;
  sentimentModelVersion: string;
  strategyConfigVersion: string;
  baseTradeDate: string;
  targetTradeDate: string;
  informationCutoffAt: string;
  positionSnapshotAvailable: boolean;
  items: TradingCandidate[];
}

interface AgentDecision {
  decisionId: string;
  stockCode: string;
  recommendation: string;
  status: string;
  summary?: string;
  riskFlags?: string[];
  confidenceBand?: string;
  uncertainty?: string;
}

interface AgentDecisionBatch {
  candidateBatchId: string;
  total: number;
  items: AgentDecision[];
}

interface DecisionEvaluationSummary {
  evaluatedCount: number;
  actionReductionRate?: number;
  baselineAverageReturnRate?: number;
  agentCounterfactualAverageReturnRate?: number;
  averageReturnDifferenceRate?: number;
  baselineWorstReturnRate?: number;
  agentCounterfactualWorstReturnRate?: number;
}

interface DecisionEvaluationBatch {
  candidateBatchId: string;
  total: number;
  summary?: DecisionEvaluationSummary;
}

interface RuntimeStatus {
  enabled: boolean;
  provider?: string;
  embeddingReady: boolean;
  deepseekAvailable?: boolean;
  ollamaAvailable?: boolean;
}

interface CandidateRow extends TradingCandidate {
  agentDecision?: AgentDecision;
}

const formatDateTime = (value?: string) => value
  ? new Date(value).toLocaleString('zh-CN', { hour12: false })
  : '未形成记录';

const percent = (value?: number, digits = 2) => value === undefined || value === null
  ? '未形成指标'
  : `${(value * 100).toFixed(digits)}%`;

const metric = (value?: number, digits = 4) => value === undefined || value === null
  ? '未形成指标'
  : value.toFixed(digits);

const lifecycleColor = (stage?: string) => {
  if (stage === 'ACTIVE') return 'success';
  if (stage === 'SHADOW' || stage === 'OFFLINE_PASSED') return 'processing';
  if (stage === 'REJECTED' || stage === 'ROLLED_BACK') return 'error';
  return 'warning';
};

const healthColor = (status: HealthItem['status']) => {
  if (status === 'HEALTHY') return 'success';
  if (status === 'BLOCKED') return 'error';
  if (status === 'WARNING') return 'warning';
  return 'default';
};

const roleLabel: Record<StrategyVersion['role'], string> = {
  ACTIVE: '生产版本',
  PREVIOUS: '可回滚版本',
  CANDIDATE: '候选版本',
};

const AdaptiveStrategy: React.FC = () => {
  usePageHeader('自适应策略');
  const [overview, setOverview] = useState<AdaptiveStrategyOverview | null>(null);
  const [config, setConfig] = useState<StrategyConfigSnapshot | null>(null);
  const [candidateBatch, setCandidateBatch] = useState<TradingCandidateBatch | null>(null);
  const [agentDecisions, setAgentDecisions] = useState<AgentDecision[]>([]);
  const [evaluation, setEvaluation] = useState<DecisionEvaluationBatch | null>(null);
  const [runtime, setRuntime] = useState<RuntimeStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [policyOpen, setPolicyOpen] = useState(false);
  const [stopOpen, setStopOpen] = useState(false);
  const [stopReason, setStopReason] = useState('发现异常，人工紧急停止后续自动交易');
  const [stopping, setStopping] = useState(false);
  const [candidateError, setCandidateError] = useState<string | null>(null);
  const [agentError, setAgentError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setCandidateError(null);
    setAgentError(null);
    const [overviewResult, configResult, candidateResult, runtimeResult, healthResult] = await Promise.allSettled([
      request.get('/adaptiveStrategy/overview') as Promise<ApiResponse<AdaptiveStrategyOverview>>,
      request.get('/strategy/config') as Promise<ApiResponse<StrategyConfigSnapshot>>,
      request.get('/realTrading/candidates') as Promise<ApiResponse<TradingCandidateBatch>>,
      request.get('/agent/runtime/status') as Promise<ApiResponse<RuntimeStatus>>,
      request.get('/agent/runtime/health') as Promise<ApiResponse<Pick<RuntimeStatus, 'deepseekAvailable' | 'ollamaAvailable'>>>,
    ]);
    try {
      if (overviewResult.status !== 'fulfilled') throw overviewResult.reason;
      setOverview(readApiData(overviewResult.value, '读取自适应策略概览失败'));
      if (configResult.status === 'fulfilled' && configResult.value.success) setConfig(configResult.value.data);
      if (runtimeResult.status === 'fulfilled' && runtimeResult.value.success) {
        const health = healthResult.status === 'fulfilled' && healthResult.value.success ? healthResult.value.data : {};
        setRuntime({ ...runtimeResult.value.data, ...health });
      } else {
        setRuntime(null);
      }
      if (candidateResult.status === 'fulfilled' && candidateResult.value.success) {
        const batch = candidateResult.value.data;
        setCandidateBatch(batch);
        const [decisionsResult, evaluationResult] = await Promise.allSettled([
          request.get(`/agent/tradeDecisions/batches/${batch.candidateBatchId}`) as Promise<ApiResponse<AgentDecisionBatch>>,
          request.get('/agent/tradeDecisions/evaluations', { params: { candidateBatchId: batch.candidateBatchId } }) as Promise<ApiResponse<DecisionEvaluationBatch>>,
        ]);
        if (decisionsResult.status === 'fulfilled' && decisionsResult.value.success) {
          setAgentDecisions(decisionsResult.value.data.items ?? []);
        } else {
          setAgentDecisions([]);
          setAgentError('Agent 建议当前不可用，不影响确定性候选和 Java 风控。');
        }
        if (evaluationResult.status === 'fulfilled' && evaluationResult.value.success) {
          setEvaluation(evaluationResult.value.data);
        } else {
          setEvaluation(null);
        }
      } else {
        setCandidateBatch(null);
        setAgentDecisions([]);
        setEvaluation(null);
        setCandidateError('当前目标交易日尚无可追溯候选批次。');
      }
    } catch (cause) {
      setOverview(null);
      message.error(getApiErrorMessage(cause, '读取自适应策略概览失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const rows = useMemo<CandidateRow[]>(() => {
    const decisionMap = new Map(agentDecisions.map((item) => [item.stockCode, item]));
    return (candidateBatch?.items ?? []).map((item) => ({ ...item, agentDecision: decisionMap.get(item.stockCode) }));
  }, [agentDecisions, candidateBatch]);

  const agentAvailable = runtime?.enabled && (
    runtime.provider === 'deepseek' ? runtime.deepseekAvailable
      : runtime.provider === 'ollama' ? runtime.ollamaAvailable
        : runtime.deepseekAvailable || runtime.ollamaAvailable
  );

  const emergencyStop = async () => {
    if (!stopReason.trim()) {
      message.warning('请填写紧急停止原因');
      return;
    }
    setStopping(true);
    try {
      const response = await request.post('/realTrading/emergencyStop', { reason: stopReason.trim() }) as ApiResponse<unknown>;
      readApiData(response, '紧急停止自动交易失败');
      message.success('已阻止后续自动交易；已提交券商的订单不会被撤销');
      setStopOpen(false);
      await loadData();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '紧急停止自动交易失败'));
    } finally {
      setStopping(false);
    }
  };

  const healthItems = [
    ...(overview?.healthItems ?? []),
    {
      key: 'agent',
      label: 'Agent 建议',
      status: (agentAvailable ? 'HEALTHY' : runtime ? 'WARNING' : 'UNAVAILABLE') as HealthItem['status'],
      detail: agentAvailable ? 'Agent 可生成独立建议，但不控制真实交易' : 'Agent 不可用，确定性策略与交易安全边界保持独立',
    },
  ];

  return (
    <div className="app-page space-y-5">
      <section className="border border-slate-200 bg-white px-5 py-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex min-w-0 items-center gap-4">
            <div className="flex size-11 shrink-0 items-center justify-center bg-emerald-600 text-lg text-white"><ApartmentOutlined /></div>
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">系统自动演化与交易观察</h2>
                <Tag color={lifecycleColor(overview?.lifecycleStage)}>{overview?.lifecycleStage || '读取中'}</Tag>
                {overview?.automaticTradingPaused && <Tag color="error">自动交易已紧急停止</Tag>}
              </div>
              <p className="mt-1 text-sm text-slate-500">{overview?.lifecycleDescription || '正在读取生产版本、候选版本与运行证据。'}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Button icon={<EyeOutlined />} onClick={() => setPolicyOpen(true)}>查看策略快照</Button>
            <Tooltip title="刷新全部运行事实">
              <Button icon={<ReloadOutlined />} aria-label="刷新" loading={loading} onClick={() => void loadData()} />
            </Tooltip>
            <Button
              danger
              icon={<PauseCircleOutlined />}
              disabled={overview?.automaticTradingPaused}
              onClick={() => setStopOpen(true)}
            >
              紧急停止
            </Button>
          </div>
        </div>
        {overview?.automaticTradingPaused && (
          <div className="mt-4 border-l-2 border-red-500 pl-3 text-sm text-red-700 dark:text-red-300">
            {overview.emergencyStopReason || '未记录停止原因'} · {formatDateTime(overview.emergencyStoppedAt)}
            <div className="mt-1 text-xs">
              操作者：{overview.emergencyStopOperator || '未记录'} · 策略版本：{overview.emergencyStopStrategyVersion || '未记录'} · 结果：{overview.emergencyStopResult || '未记录'}
            </div>
          </div>
        )}
      </section>

      <section>
        <div className="mb-3 flex items-center justify-between">
          <h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">运行概览</h3>
          <Text type="secondary">只读事实，不提供人工调参</Text>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6">
          <div className="border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"><Statistic title="策略版本" value={overview?.strategyVersion || '未标记'} /></div>
          <div className="border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"><Statistic title="内部策略模板" value={overview?.currentMode || '未确认'} /></div>
          <div className="border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"><Statistic title="最近评估目标日" value={overview?.targetTradeDate || candidateBatch?.targetTradeDate || '未形成'} /></div>
          <div className="border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"><Statistic title="候选数量" value={candidateBatch?.items.length ?? 0} suffix="只" /></div>
          <div className="border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"><Statistic title="最近演化记录" value={formatDateTime(overview?.lastEvolutionAt)} /></div>
          <div className="border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"><Statistic title="下一次评估" value="未形成调度证据" /></div>
        </div>
      </section>

      <section>
        <h3 className="mb-3 text-base font-semibold text-slate-900 dark:text-slate-100">决策链健康度</h3>
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {healthItems.map((item) => (
            <div key={item.key} className="border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
              <div className="flex items-center justify-between gap-3">
                <strong className="text-sm text-slate-900 dark:text-slate-100">{item.label}</strong>
                <Tag color={healthColor(item.status)}>{item.status}</Tag>
              </div>
              <p className="mt-3 text-sm leading-6 text-slate-500">{item.detail}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="grid gap-5 xl:grid-cols-[minmax(0,1.25fr)_minmax(360px,0.75fr)]">
        <div>
          <h3 className="mb-3 text-base font-semibold text-slate-900 dark:text-slate-100">真实交易表现</h3>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            <div className="border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"><Statistic title="累计已实现收益率" value={percent(overview?.performance?.cumulativeReturnRate)} /></div>
            <div className="border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"><Statistic title="已完成交易胜率" value={percent(overview?.performance?.winRate)} /></div>
            <div className="border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"><Statistic title="平均已实现收益率" value={percent(overview?.performance?.averageReturnRate)} /></div>
            <div className="border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"><Statistic title="单笔最差收益率" value={percent(overview?.performance?.worstReturnRate)} /></div>
            <div className="border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"><Statistic title="已完成交易" value={overview?.performance?.closedTradeCount ?? 0} suffix="笔" /></div>
            <div className="border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
              <div className="text-sm text-slate-500">尚未形成的指标</div>
              <div className="mt-3 flex flex-wrap gap-2"><Tag>最大回撤</Tag><Tag>换手率</Tag><Tag>完整成本后基线对比</Tag></div>
            </div>
          </div>
          <div className="mt-3 text-xs text-slate-500">收益指标来自真实成交派生记录；未卖出持仓不计入已实现收益。最近事实时间：{formatDateTime(overview?.performance?.capturedAt)}</div>
        </div>

        <div>
          <h3 className="mb-3 text-base font-semibold text-slate-900 dark:text-slate-100">Agent 反事实评估</h3>
          <div className="border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
            {evaluation?.summary && evaluation.summary.evaluatedCount > 0 ? (
              <dl className="grid grid-cols-2 gap-4 text-sm">
                <div><dt className="text-slate-500">有效评估样本</dt><dd className="mt-1 font-semibold">{evaluation.summary.evaluatedCount}</dd></div>
                <div><dt className="text-slate-500">动作减少率</dt><dd className="mt-1 font-semibold">{percent(evaluation.summary.actionReductionRate)}</dd></div>
                <div><dt className="text-slate-500">基线平均收益贡献</dt><dd className="mt-1 font-semibold">{percent(evaluation.summary.baselineAverageReturnRate)}</dd></div>
                <div><dt className="text-slate-500">Agent 参与后贡献</dt><dd className="mt-1 font-semibold">{percent(evaluation.summary.agentCounterfactualAverageReturnRate)}</dd></div>
              </dl>
            ) : (
              <div className="py-6 text-center">
                <RobotOutlined className="text-2xl text-slate-400" />
                <div className="mt-2 text-sm font-medium text-slate-700 dark:text-slate-200">评估不足</div>
                <div className="mt-1 text-xs leading-5 text-slate-500">尚无足够目标日行情样本，不能把 Agent 建议描述为有效提升。</div>
              </div>
            )}
          </div>
        </div>
      </section>

      <section>
        <div className="mb-3 flex items-center gap-2"><AuditOutlined /><h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">版本比较</h3></div>
        <Table<StrategyVersion>
          rowKey={(row) => `${row.role}-${row.versionId}`}
          dataSource={overview?.versions ?? []}
          pagination={false}
          scroll={{ x: 'max-content' }}
          locale={{ emptyText: '尚无可比较的生产、回滚或候选版本' }}
          columns={[
            { title: '角色', dataIndex: 'role', render: (value: StrategyVersion['role']) => <Tag color={value === 'ACTIVE' ? 'success' : value === 'CANDIDATE' ? 'processing' : 'default'}>{roleLabel[value]}</Tag> },
            { title: '业务版本', dataIndex: 'modelVersion', render: (value?: string) => value || '未标记' },
            { title: '状态', dataIndex: 'status', render: (value?: string) => value || '未确认' },
            { title: '架构', dataIndex: 'architectureType', render: (value?: string) => value || '未记录' },
            { title: '特征版本', dataIndex: 'featureVersion', render: (value?: string) => value || '未记录' },
            { title: '平均 Rank IC', dataIndex: 'meanRankIc', render: (value?: number) => metric(value) },
            { title: 'Rank ICIR', dataIndex: 'rankIcIr', render: (value?: number) => metric(value) },
            { title: 'Top 10% 成本后收益', dataIndex: 'topDecileNetReturn', render: (value?: number) => percent(value) },
            { title: '输出塌缩', dataIndex: 'outputCollapsed', render: (value?: boolean) => value === undefined ? <Tag>未评估</Tag> : <Tag color={value ? 'error' : 'success'}>{value ? '是' : '否'}</Tag> },
            { title: '版本记录时间', dataIndex: 'createdAt', render: formatDateTime },
          ]}
        />
      </section>

      <section className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div>
          <div className="mb-3 flex items-center gap-2"><FileSearchOutlined /><h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">当前候选与独立建议</h3></div>
          <Table<CandidateRow>
            rowKey="stockCode"
            loading={loading}
            dataSource={rows}
            scroll={{ x: 'max-content' }}
            pagination={{ defaultPageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50] }}
            locale={{ emptyText: candidateError || '暂无交易候选' }}
            columns={[
              { title: '排名', dataIndex: 'rank', width: 72 },
              { title: '股票', render: (_: unknown, row: CandidateRow) => `${row.stockName} (${row.stockCode})` },
              { title: '模型得分', dataIndex: 'lstmScore', render: (value: number) => metric(value, 3) },
              { title: '新闻影响', dataIndex: 'newsImpactScore', render: (value: number) => metric(value, 3) },
              { title: '综合得分', dataIndex: 'totalScore', render: (value: number) => <Tag color={value >= 0.7 ? 'success' : value >= 0.5 ? 'processing' : 'default'}>{metric(value, 3)}</Tag> },
              { title: 'Agent 建议', render: (_: unknown, row: CandidateRow) => row.agentDecision ? <Tag color={row.agentDecision.recommendation === 'BUY' ? 'success' : row.agentDecision.recommendation === 'SKIP' ? 'warning' : 'default'}>{row.agentDecision.recommendation}</Tag> : <Tag>无建议</Tag> },
              { title: '风险标记', render: (_: unknown, row: CandidateRow) => row.agentDecision?.riskFlags?.length ? row.agentDecision.riskFlags.map((flag) => <Tag key={flag} color="warning">{flag}</Tag>) : '无独立风险标记' },
              { title: '最终状态', render: (_: unknown, row: CandidateRow) => <Tag color={row.held ? 'processing' : 'default'}>{row.held ? '已持有' : '等待 Java 风控'}</Tag> },
              { title: '确定性候选理由', dataIndex: 'reason', width: 260 },
            ]}
          />
          {agentError && <div className="mt-2 text-xs text-amber-700 dark:text-amber-300">{agentError}</div>}
        </div>

        <div>
          <div className="mb-3 flex items-center gap-2"><ApartmentOutlined /><h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">演化时间线</h3></div>
          <div className="border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
            {(overview?.evolutionEvents.length ?? 0) === 0 ? (
              <div className="py-6 text-center text-sm text-slate-500">尚无可追溯演化事件</div>
            ) : overview?.evolutionEvents.map((event, index) => (
              <div key={`${event.type}-${index}`} className="relative border-l border-slate-300 pb-5 pl-5 last:pb-0 dark:border-slate-600">
                <span className="absolute -left-1.5 top-1 size-3 bg-emerald-500" />
                <div className="flex flex-wrap items-center gap-2"><strong className="text-sm">{event.title}</strong><Tag>{event.status}</Tag></div>
                <div className="mt-1 text-xs text-slate-500">{formatDateTime(event.occurredAt)}</div>
                <p className="mt-2 text-xs leading-5 text-slate-600 dark:text-slate-300">{event.detail}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {(overview?.anomalies.length ?? 0) > 0 && (
        <section>
          <div className="mb-3 flex items-center gap-2"><AlertOutlined /><h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">异常与证据缺口</h3></div>
          <div className="divide-y divide-slate-200 border border-slate-200 bg-white dark:divide-slate-700 dark:border-slate-700 dark:bg-slate-900">
            {overview?.anomalies.map((item) => <div key={item} className="px-4 py-3 text-sm text-amber-700 dark:text-amber-300">{item}</div>)}
          </div>
        </section>
      )}

      <Drawer title="当前策略只读快照" width={640} open={policyOpen} onClose={() => setPolicyOpen(false)}>
        <div className="mb-4 border-l-2 border-emerald-500 pl-3 text-sm leading-6 text-slate-600">
          参数由系统版本管理。本页面仅用于追溯当前生效快照，不提供人工覆盖入口。
        </div>
        <Descriptions bordered size="small" column={2}>
          <Descriptions.Item label="策略版本">{config?.version || '未标记'}</Descriptions.Item>
          <Descriptions.Item label="内部模板">{config?.mode || '未确认'}</Descriptions.Item>
          <Descriptions.Item label="LSTM 权重">{metric(config?.lstmWeight, 3)}</Descriptions.Item>
          <Descriptions.Item label="情感权重">{metric(config?.sentimentWeight, 3)}</Descriptions.Item>
          <Descriptions.Item label="候选数量">{config?.topN ?? '未记录'}</Descriptions.Item>
          <Descriptions.Item label="最低综合得分">{metric(config?.minScore, 3)}</Descriptions.Item>
          <Descriptions.Item label="最小上市年限">{config?.minimumListingYears ?? '未记录'} 年</Descriptions.Item>
          <Descriptions.Item label="移动止损容忍度">{percent(config?.trailingStopTolerance)}</Descriptions.Item>
          <Descriptions.Item label="RSI 超买阈值">{config?.rsiOverboughtThreshold ?? '未记录'}</Descriptions.Item>
          <Descriptions.Item label="成交量萎缩阈值">{percent(config?.volumeShrinkThreshold)}</Descriptions.Item>
          <Descriptions.Item label="布林带突破系数">{metric(config?.bollingerBreakoutThreshold, 3)}</Descriptions.Item>
          <Descriptions.Item label="策略状态"><Tag color={config?.enabled ? 'success' : 'error'}>{config?.enabled ? '启用' : '停用'}</Tag></Descriptions.Item>
          <Descriptions.Item label="数据与特征版本" span={2}>{overview?.versions.find((item) => item.role === 'ACTIVE')?.featureVersion || '未形成可追溯特征版本'}</Descriptions.Item>
          <Descriptions.Item label="确定性决策链" span={2}>模型排名与新闻事实形成候选，委托前由 Java 重读行情、账户、持仓、交易日和硬风险事实。</Descriptions.Item>
          <Descriptions.Item label="退出逻辑" span={2}>系统内部综合移动止损、RSI、成交量、布林带和收益分层阈值；页面不提供人工覆盖。</Descriptions.Item>
          <Descriptions.Item label="退出阈值" span={2}>高 {config?.highReturnThreshold ?? '-'} / 正常 {config?.normalReturnThreshold ?? '-'} / 低 {config?.lowReturnThreshold ?? '-'} / 亏损 {config?.lossReturnThreshold ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="快照更新时间" span={2}>{formatDateTime(config?.updateTime)}</Descriptions.Item>
        </Descriptions>
      </Drawer>

      <Modal
        title={<span><SafetyCertificateOutlined className="mr-2 text-red-600" />紧急停止后续自动交易</span>}
        open={stopOpen}
        okText="确认停止"
        cancelText="取消"
        okButtonProps={{ danger: true, loading: stopping }}
        onOk={() => void emergencyStop()}
        onCancel={() => setStopOpen(false)}
      >
        <p className="mb-3 text-sm leading-6 text-slate-600">停止后，自动买入和自动退出会在 Java 执行入口被拒绝；已经提交到券商的订单不会自动撤销。</p>
        <Input.TextArea value={stopReason} onChange={(event) => setStopReason(event.target.value)} maxLength={500} showCount rows={4} placeholder="填写可审计的停止原因" />
      </Modal>
    </div>
  );
};

export default AdaptiveStrategy;
// AI_GENERATE_END----
