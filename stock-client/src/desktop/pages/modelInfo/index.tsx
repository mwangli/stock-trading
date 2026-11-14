// AI_GENERATE_START -----------
import './styles.css';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Descriptions, Drawer, Empty, Statistic, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Activity, FlaskConical, Gauge, Rows3, Sparkles } from 'lucide-react';
import request from '@shared/utils/request';
import type { ApiResponse } from '@shared/utils/api';
import { formatDateTime } from '@shared/utils/dateTime';
import { usePageHeader } from '../../layouts/PageHeaderContext';

const { Text } = Typography;

interface ModelVersion {
  id: number;
  model_name: string;
  feature_set: string;
  status: string;
  artifact_hash?: string;
  artifact_path?: string;
  metrics_json: string;
  created_at: string;
  prediction_count: number;
  realized_trade_count: number;
  gross_profit: number;
  net_profit: number;
  net_return?: number;
  last_sell_time?: string;
}

interface ModelEvaluation {
  business_key: string;
  business_date: string;
  observations: number;
  window_days: number;
  total_net_return?: number;
  total_gross_return?: number;
  max_drawdown?: number;
  decision: string;
  reason: string;
  model_name?: string;
}

interface ModelOverview {
  versions: ModelVersion[];
  evaluations: ModelEvaluation[];
}

interface MatrixDefinition {
  modelId: string;
  level: 'L1' | 'L2';
  algorithm: 'LightGBM' | 'XGBoost';
  strategyId: string;
  dataLabel: string;
}

const MATRIX: MatrixDefinition[] = [
  { modelId: 'L1_daily_LightGBM', level: 'L1', algorithm: 'LightGBM', strategyId: 'T1_ALL_SELL_ALL_BUY', dataLabel: '日线' },
  { modelId: 'L1_daily_XGBoost', level: 'L1', algorithm: 'XGBoost', strategyId: 'T1_ALL_SELL_ALL_BUY', dataLabel: '日线' },
  { modelId: 'L2_minute5_LightGBM', level: 'L2', algorithm: 'LightGBM', strategyId: 'T1_AM_SELL_PM_BUY', dataLabel: '5 分钟线' },
  { modelId: 'L2_minute5_XGBoost', level: 'L2', algorithm: 'XGBoost', strategyId: 'T1_AM_SELL_PM_BUY', dataLabel: '5 分钟线' },
];

const DEMO_VARIANTS = [
  { id: 'research', label: '研究控制台', hint: '矩阵对照', icon: <Rows3 size={15} strokeWidth={1.8} /> },
  { id: 'cockpit', label: '策略驾驶舱', hint: '运行优先', icon: <Gauge size={15} strokeWidth={1.8} /> },
  { id: 'lab', label: '实验室时间线', hint: '评估优先', icon: <FlaskConical size={15} strokeWidth={1.8} /> },
  { id: 'terminal', label: '紧凑终端', hint: '高密扫描', icon: <Activity size={15} strokeWidth={1.8} /> },
] as const;

const parseMetrics = (raw?: string): Record<string, unknown> => {
  if (!raw) return {};
  try {
    const value = JSON.parse(raw) as unknown;
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {};
    const record = value as Record<string, unknown>;
    const nested = record.metrics;
    const merged = nested && typeof nested === 'object' && !Array.isArray(nested)
      ? { ...record, ...(nested as Record<string, unknown>) }
      : record;
    const backtest = merged.backtest;
    return backtest && typeof backtest === 'object' && !Array.isArray(backtest)
      ? { ...merged, ...(backtest as Record<string, unknown>) }
      : merged;
  } catch {
    return {};
  }
};

const metricNumber = (metrics: Record<string, unknown>, ...keys: string[]) => {
  for (const key of keys) {
    const value = Number(metrics[key]);
    if (Number.isFinite(value)) return value;
  }
  return undefined;
};

const percent = (value?: number) => value == null ? '-' : (value * 100).toFixed(2) + '%';
const decimal = (value?: number) => value == null ? '-' : value.toFixed(4);
const money = (value?: number) => 'CNY ' + Number(value ?? 0).toFixed(2);
const isReadyStatus = (status?: string) => ['READY', 'ACTIVE'].includes(String(status ?? '').toUpperCase());
const statusColor = (status?: string) => String(status ?? '').toUpperCase() === 'ACTIVE' ? 'green' : isReadyStatus(status) ? 'blue' : 'default';

const modelIdOf = (model: ModelVersion) => {
  const metrics = parseMetrics(model.metrics_json);
  const rawModelId = String(metrics.modelId ?? metrics.model_id ?? '');
  if (/^L[12]_(daily|minute5)_(LightGBM|XGBoost)$/i.test(rawModelId)) return rawModelId;
  const path = model.artifact_path?.replaceAll('\\', '/').replace(/\/$/, '');
  const pathModelId = path?.split('/').slice(-2, -1)[0];
  if (pathModelId && /^L[12]_(daily|minute5)_(LightGBM|XGBoost)$/i.test(pathModelId)) return pathModelId;
  const algorithm = String(metrics.algorithm ?? model.model_name ?? '').toLowerCase().includes('xgboost') ? 'XGBoost' : 'LightGBM';
  const featureText = `${rawModelId} ${pathModelId ?? ''} ${String(metrics.featureLevel ?? '')} ${String(metrics.dataFeature ?? '')} ${model.feature_set ?? ''}`.toLowerCase();
  const level = featureText.includes('minute5') || featureText.includes('minute') || featureText.includes('l2') ? 'L2' : 'L1';
  return rawModelId || pathModelId || model.model_name ? `${level}_${level === 'L2' ? 'minute5' : 'daily'}_${algorithm}` : '';
};

const artifactPathOf = (model: ModelVersion) => {
  if (model.artifact_path) return model.artifact_path;
  const modelId = modelIdOf(model);
  return modelId ? `data/models/trained/${modelId}` : '-';
};

const hasBacktest = (metrics: Record<string, unknown>) => [
  'grossReturn', 'gross_return', 'totalGrossReturn', 'maxDrawdown', 'max_drawdown', 'closedTrades', 'winRate', 'win_rate',
].some((key) => metricNumber(metrics, key) != null);

const ModelInfo: React.FC = () => {
  const [overview, setOverview] = useState<ModelOverview | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [selectedModel, setSelectedModel] = useState<ModelVersion | null>(null);
  const [variant, setVariant] = useState<(typeof DEMO_VARIANTS)[number]['id']>('terminal');
  usePageHeader('横向比较算法，纵向比较数据等级与固定交易策略');

  const loadOverview = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const response = await request.get('/models/overview') as unknown as ApiResponse<ModelOverview>;
      if (!response?.success || !response.data) {
        throw new Error(response?.message || '模型信息接口未返回有效数据');
      }
      setOverview(response.data);
    } catch (error) {
      setOverview(null);
      setLoadError(error instanceof Error ? error.message : '模型信息加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadOverview(); }, [loadOverview]);

  const versions = overview?.versions ?? [];
  const versionById = useMemo(() => versions.reduce((result, model) => {
    const modelId = modelIdOf(model);
    if (!modelId) return result;
    const previous = result.get(modelId);
    if (!previous || String(model.created_at ?? '') > String(previous.created_at ?? '')) {
      result.set(modelId, model);
    }
    return result;
  }, new Map<string, ModelVersion>()), [versions]);
  const backtestById = useMemo(() => versions.reduce((result, model) => {
    const modelId = modelIdOf(model);
    const metrics = parseMetrics(model.metrics_json);
    if (!modelId || !hasBacktest(metrics)) return result;
    const previous = result.get(modelId);
    if (!previous || String(model.created_at ?? '') > String(previous.created_at ?? '')) {
      result.set(modelId, model);
    }
    return result;
  }, new Map<string, ModelVersion>()), [versions]);
  const modelRows = useMemo(() => MATRIX.map((definition) => ({
    ...definition,
    model: versionById.get(definition.modelId),
  })), [versionById]);
  const totals = useMemo(() => versions.reduce((result, item) => ({
    trades: result.trades + Number(item.realized_trade_count ?? 0),
    grossProfit: result.grossProfit + Number(item.gross_profit ?? 0),
  }), { trades: 0, grossProfit: 0 }), [versions]);

  const evaluationColumns: ColumnsType<ModelEvaluation> = [
    { title: '业务日期', dataIndex: 'business_date', key: 'business_date', width: 120 },
    { title: '模型', dataIndex: 'model_name', key: 'model_name', width: 190, render: (value?: string) => value || '未绑定模型' },
    { title: '样本', key: 'observations', width: 110, render: (_value, item) => item.observations + '/' + item.window_days },
    { title: '回测毛收益率', dataIndex: 'total_gross_return', key: 'total_gross_return', width: 130, render: percent },
    { title: '真实净收益率', dataIndex: 'total_net_return', key: 'total_net_return', width: 130, render: percent },
    { title: '最大回撤', dataIndex: 'max_drawdown', key: 'max_drawdown', width: 110, render: percent },
    { title: '结论', dataIndex: 'decision', key: 'decision', width: 120, render: (value: string) => <Tag>{value}</Tag> },
    { title: '原因', dataIndex: 'reason', key: 'reason', ellipsis: true },
  ];

  const selectedMetrics = parseMetrics(selectedModel?.metrics_json);

  const activeVariant = DEMO_VARIANTS.find((item) => item.id === variant) ?? DEMO_VARIANTS[0];

  return (
    <div className={`app-page model-matrix-page model-demo-${variant}`}>
      <section className="model-demo-hero" aria-label="模型信息方案预览">
        <div className="model-demo-hero-copy">
          <div className="model-demo-kicker"><Sparkles size={14} /> UI 方案预览 · 4 套</div>
          <h1>模型信息</h1>
          <p>{activeVariant.hint}视图，沿用真实模型、策略合同与评估数据。</p>
        </div>
        <div className="demo-switcher" role="tablist" aria-label="选择模型信息 UI 方案">
          {DEMO_VARIANTS.map((item) => (
            <button
              type="button"
              role="tab"
              aria-selected={variant === item.id}
              className={variant === item.id ? 'is-selected' : ''}
              key={item.id}
              onClick={() => setVariant(item.id)}
            >
              {item.icon}<span>{item.label}</span><small>{item.hint}</small>
            </button>
          ))}
        </div>
      </section>
      <section className="matrix-summary" aria-label="模型矩阵汇总">
        <Statistic title="可用模型" value={modelRows.filter((item) => isReadyStatus(item.model?.status)).length} suffix="/ 4" />
        <Statistic title="已实现闭环交易" value={totals.trades} />
        <Statistic title="真实已实现毛收益" value={totals.grossProfit} precision={2} prefix="CNY" />
        <div className="matrix-roadmap"><span>下一阶段</span><strong>T1_PM_SELL_PM_BUY</strong><small>分钟级午间决策，待数据完整后启用</small></div>
      </section>

      <section className="model-matrix-section model-list-section">
        <div className="matrix-heading">
          <div><h2>{variant === 'terminal' ? '模型列表' : variant === 'cockpit' ? '当前策略组合' : variant === 'lab' ? '实验模型列表' : '模型列表'}</h2><p>按模型版本查看训练状态、策略合同和可用评估指标；数据来自最新模型版本。</p></div>
          <div className="matrix-heading-meta"><span>当前交易合同</span><strong>T1 · 先卖后买</strong></div>
        </div>
        {loadError && (
          <div className="model-list-error" role="alert">
            <span>{loadError}</span>
            <button type="button" onClick={() => void loadOverview}>重新加载</button>
          </div>
        )}
        <Table
          className="model-list-table"
          rowKey="modelId"
          columns={[
            { title: '数据等级', dataIndex: 'level', key: 'level', width: 100, render: (value: string, row) => <div><strong>{value}</strong><small>{row.dataLabel}</small></div> },
            { title: '状态', key: 'status', width: 120, render: (_value, row) => <Tag color={statusColor(row.model?.status)}>{row.model?.status ?? '待同步'}</Tag> },
            { title: '模型 ID', dataIndex: 'modelId', key: 'modelId', width: 210, render: (value: string) => <span className="model-list-id">{value}</span> },
            { title: '算法', dataIndex: 'algorithm', key: 'algorithm', width: 110 },
            { title: '策略合同', dataIndex: 'strategyId', key: 'strategyId', width: 190, render: (value: string) => <span className="model-list-code">{value}</span> },
            { title: 'IC 均值', key: 'icMean', width: 105, align: 'right', render: (_value, row) => decimal(metricNumber(parseMetrics(row.model?.metrics_json), 'icMean')) },
            { title: 'Rank IC', key: 'rankIcMean', width: 105, align: 'right', render: (_value, row) => decimal(metricNumber(parseMetrics(row.model?.metrics_json), 'rankIcMean')) },
            { title: '有效 IC 日', key: 'validIcDays', width: 105, align: 'right', render: (_value, row) => metricNumber(parseMetrics(row.model?.metrics_json), 'validIcDays') ?? '-' },
            { title: '训练样本', key: 'trainRows', width: 105, align: 'right', render: (_value, row) => metricNumber(parseMetrics(row.model?.metrics_json), 'trainRows') ?? '-' },
            { title: '回测毛收益率', key: 'grossReturn', width: 135, align: 'right', render: (_value, row) => percent(metricNumber(parseMetrics(backtestById.get(row.model ? modelIdOf(row.model) : row.modelId)?.metrics_json), 'grossReturn', 'gross_return', 'totalGrossReturn')) },
            { title: '最大回撤', key: 'maxDrawdown', width: 110, align: 'right', render: (_value, row) => percent(metricNumber(parseMetrics(backtestById.get(row.model ? modelIdOf(row.model) : row.modelId)?.metrics_json), 'maxDrawdown', 'max_drawdown')) },
            { title: '回测交易数', key: 'closedTrades', width: 105, align: 'right', render: (_value, row) => metricNumber(parseMetrics(backtestById.get(row.model ? modelIdOf(row.model) : row.modelId)?.metrics_json), 'closedTrades', 'closed_trades') ?? '-' },
            { title: '真实毛收益', key: 'grossProfit', width: 135, align: 'right', render: (_value, row) => row.model ? money(row.model.gross_profit) : '-' },
            { title: '更新时间', key: 'createdAt', width: 180, render: (_value, row) => row.model ? formatDateTime(row.model.created_at) : '-' },
            { title: '操作', key: 'action', width: 80, render: (_value, row) => <a className="model-list-action" onClick={() => row.model && setSelectedModel(row.model)}>详情</a> },
          ]}
          dataSource={modelRows}
          loading={loading}
          scroll={{ x: 1850 }}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无模型版本" /> }}
        />
      </section>

      <section className="model-evaluation-section">
        <h2>最近评估</h2>
        <Table
          rowKey="business_key"
          columns={evaluationColumns}
          dataSource={overview?.evaluations ?? []}
          loading={loading}
          scroll={{ x: 900 }}
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
          locale={{ emptyText: <Empty description="暂无模型评估记录" /> }}
        />
      </section>

      <Drawer title="模型详情" open={selectedModel !== null} width={600} className="model-detail-drawer" onClose={() => setSelectedModel(null)}>
        {selectedModel && (
          <div className="model-detail-content">
            <div className="model-detail-heading"><div><span>模型 ID</span><strong>{modelIdOf(selectedModel)}</strong></div><Tag color={selectedModel.status === 'ACTIVE' ? 'green' : 'default'}>{selectedModel.status}</Tag></div>
            <Descriptions title="策略合同" column={1} bordered size="small">
              <Descriptions.Item label="模型制品"><span className="model-detail-code">{artifactPathOf(selectedModel)}</span></Descriptions.Item>
              <Descriptions.Item label="策略 ID">{String(selectedMetrics.strategyId ?? '-')}</Descriptions.Item>
              <Descriptions.Item label="数据等级">{String(selectedMetrics.featureLevel ?? '-')}</Descriptions.Item>
              <Descriptions.Item label="算法">{String(selectedMetrics.algorithm ?? selectedModel.model_name)}</Descriptions.Item>
              <Descriptions.Item label="标签合同">{String(selectedMetrics.labelContract ?? '-')}</Descriptions.Item>
              <Descriptions.Item label="合同哈希"><span className="model-detail-code">{String(selectedMetrics.strategyContractHash ?? '-')}</span></Descriptions.Item>
            </Descriptions>
            <Descriptions title="训练质量与真实交易" column={1} bordered size="small">
              <Descriptions.Item label="IC 均值">{decimal(metricNumber(selectedMetrics, 'icMean'))}</Descriptions.Item>
              <Descriptions.Item label="Rank IC 均值">{decimal(metricNumber(selectedMetrics, 'rankIcMean'))}</Descriptions.Item>
              <Descriptions.Item label="有效 IC 日">{metricNumber(selectedMetrics, 'validIcDays') ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="训练样本">{metricNumber(selectedMetrics, 'trainRows') ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="回测毛收益率">{percent(metricNumber(parseMetrics(backtestById.get(modelIdOf(selectedModel))?.metrics_json), 'grossReturn', 'gross_return', 'totalGrossReturn'))}</Descriptions.Item>
              <Descriptions.Item label="最大回撤">{percent(metricNumber(parseMetrics(backtestById.get(modelIdOf(selectedModel))?.metrics_json), 'maxDrawdown', 'max_drawdown'))}</Descriptions.Item>
              <Descriptions.Item label="回测交易数">{metricNumber(parseMetrics(backtestById.get(modelIdOf(selectedModel))?.metrics_json), 'closedTrades', 'closed_trades') ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="真实闭环交易">{selectedModel.realized_trade_count}</Descriptions.Item>
              <Descriptions.Item label="真实已实现毛收益"><Text type={selectedModel.gross_profit >= 0 ? 'danger' : 'success'}>{money(selectedModel.gross_profit)}</Text></Descriptions.Item>
              <Descriptions.Item label="更新时间">{formatDateTime(selectedModel.created_at)}</Descriptions.Item>
            </Descriptions>
          </div>
        )}
      </Drawer>
    </div>
  );
};

export default ModelInfo;
// AI_GENERATE_END ----------
