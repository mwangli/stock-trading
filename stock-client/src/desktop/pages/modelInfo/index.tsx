// AI_GENERATE_START -----
import './styles.css';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Descriptions, Drawer, Empty, Statistic, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
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

const parseMetrics = (raw?: string): Record<string, unknown> => {
  if (!raw) return {};
  try {
    const value = JSON.parse(raw) as unknown;
    return value && typeof value === 'object' && !Array.isArray(value)
      ? value as Record<string, unknown>
      : {};
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
const money = (value?: number) => 'CNY ' + Number(value ?? 0).toFixed(2);

const modelIdOf = (model: ModelVersion) => {
  const metrics = parseMetrics(model.metrics_json);
  return String(metrics.modelId ?? metrics.model_id ?? '');
};

const ModelInfo: React.FC = () => {
  const [overview, setOverview] = useState<ModelOverview | null>(null);
  const [loading, setLoading] = useState(false);
  const [selectedModel, setSelectedModel] = useState<ModelVersion | null>(null);
  usePageHeader('横向比较算法，纵向比较数据等级与固定交易策略');

  const loadOverview = useCallback(async () => {
    setLoading(true);
    try {
      const response = await request.get('/models/overview') as ApiResponse<ModelOverview>;
      setOverview(response.success ? response.data : null);
    } catch {
      setOverview(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadOverview(); }, [loadOverview]);

  const versions = overview?.versions ?? [];
  const versionById = useMemo(() => new Map(
    versions.map((model) => [modelIdOf(model), model]).filter(([modelId]) => modelId),
  ), [versions]);
  const totals = useMemo(() => versions.reduce((result, item) => ({
    trades: result.trades + Number(item.realized_trade_count ?? 0),
    grossProfit: result.grossProfit + Number(item.gross_profit ?? 0),
  }), { trades: 0, grossProfit: 0 }), [versions]);

  const evaluationColumns: ColumnsType<ModelEvaluation> = [
    { title: '业务日期', dataIndex: 'business_date', key: 'business_date', width: 120 },
    { title: '模型', dataIndex: 'model_name', key: 'model_name', width: 190, render: (value?: string) => value || '未绑定模型' },
    { title: '样本', key: 'observations', width: 110, render: (_value, item) => item.observations + '/' + item.window_days },
    { title: '真实净收益率', dataIndex: 'total_net_return', key: 'total_net_return', width: 130, render: percent },
    { title: '最大回撤', dataIndex: 'max_drawdown', key: 'max_drawdown', width: 110, render: percent },
    { title: '结论', dataIndex: 'decision', key: 'decision', width: 120, render: (value: string) => <Tag>{value}</Tag> },
    { title: '原因', dataIndex: 'reason', key: 'reason', ellipsis: true },
  ];

  const selectedMetrics = parseMetrics(selectedModel?.metrics_json);

  return (
    <div className="app-page model-matrix-page">
      <section className="matrix-summary" aria-label="模型矩阵汇总">
        <Statistic title="运行模型" value={versions.filter((item) => item.status === 'ACTIVE').length} suffix="/ 4" />
        <Statistic title="已实现闭环交易" value={totals.trades} />
        <Statistic title="真实已实现毛收益" value={totals.grossProfit} precision={2} prefix="CNY" />
        <div className="matrix-roadmap"><span>L3 规划</span><strong>T1_PM_SELL_PM_BUY</strong><small>分钟级午间决策，待数据完整后启用</small></div>
      </section>

      <section className="model-matrix-section">
        <div className="matrix-heading">
          <div><h2>2 x 2 模型矩阵</h2><p>每个数据等级固定一个策略，所有策略 ID 均按先卖后买命名。</p></div>
          <Tag color="blue">entryRank 5 / retainRank 15</Tag>
        </div>
        <div className="model-matrix-grid">
          <div className="matrix-corner">数据等级</div>
          <div className="matrix-column">LightGBM</div>
          <div className="matrix-column">XGBoost</div>
          {(['L1', 'L2'] as const).map((level) => (
            <React.Fragment key={level}>
              <div className="matrix-row-label">
                <strong>{level}</strong>
                <span>{level === 'L1' ? '日线' : '5 分钟线'}</span>
                <small>{level === 'L1' ? 'T1_ALL_SELL_ALL_BUY' : 'T1_AM_SELL_PM_BUY'}</small>
              </div>
              {MATRIX.filter((item) => item.level === level).map((definition) => {
                const model = versionById.get(definition.modelId);
                const metrics = parseMetrics(model?.metrics_json);
                const grossReturn = metricNumber(metrics, 'grossReturn', 'gross_return', 'totalGrossReturn');
                const drawdown = metricNumber(metrics, 'maxDrawdown', 'max_drawdown');
                const winRate = metricNumber(metrics, 'winRate', 'win_rate');
                const turnover = metricNumber(metrics, 'turnover');
                return (
                  <button
                    type="button"
                    className={'matrix-cell' + (model ? ' is-ready' : '')}
                    key={definition.modelId}
                    onClick={() => model && setSelectedModel(model)}
                    disabled={!model}
                  >
                    <span className="matrix-cell-top"><Tag color={model?.status === 'ACTIVE' ? 'green' : 'default'}>{model?.status ?? '待训练'}</Tag><small>{definition.dataLabel}</small></span>
                    <strong>{definition.modelId}</strong>
                    <span className="matrix-strategy">{definition.strategyId}</span>
                    <dl>
                      <div><dt>回测毛收益率</dt><dd>{percent(grossReturn)}</dd></div>
                      <div><dt>最大回撤</dt><dd>{percent(drawdown)}</dd></div>
                      <div><dt>胜率</dt><dd>{percent(winRate)}</dd></div>
                      <div><dt>换手率</dt><dd>{percent(turnover)}</dd></div>
                    </dl>
                    <span className="matrix-realized">真实毛收益 {money(model?.gross_profit)}</span>
                  </button>
                );
              })}
            </React.Fragment>
          ))}
        </div>
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
              <Descriptions.Item label="策略 ID">{String(selectedMetrics.strategyId ?? '-')}</Descriptions.Item>
              <Descriptions.Item label="数据等级">{String(selectedMetrics.featureLevel ?? '-')}</Descriptions.Item>
              <Descriptions.Item label="算法">{String(selectedMetrics.algorithm ?? selectedModel.model_name)}</Descriptions.Item>
              <Descriptions.Item label="标签合同">{String(selectedMetrics.labelContract ?? '-')}</Descriptions.Item>
              <Descriptions.Item label="合同哈希"><span className="model-detail-code">{String(selectedMetrics.strategyContractHash ?? '-')}</span></Descriptions.Item>
            </Descriptions>
            <Descriptions title="回测与真实交易" column={1} bordered size="small">
              <Descriptions.Item label="回测毛收益率">{percent(metricNumber(selectedMetrics, 'grossReturn', 'gross_return', 'totalGrossReturn'))}</Descriptions.Item>
              <Descriptions.Item label="最大回撤">{percent(metricNumber(selectedMetrics, 'maxDrawdown', 'max_drawdown'))}</Descriptions.Item>
              <Descriptions.Item label="胜率">{percent(metricNumber(selectedMetrics, 'winRate', 'win_rate'))}</Descriptions.Item>
              <Descriptions.Item label="换手率">{percent(metricNumber(selectedMetrics, 'turnover'))}</Descriptions.Item>
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
// AI_GENERATE_END -----
