// AI_GENERATE_START -
import './styles.css';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Card, Col, Empty, Row, Statistic, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import request from '../../utils/request';
import type { ApiResponse } from '../../utils/api';
import { formatDateTime } from '../../utils/dateTime';
import { usePageHeader } from '../../layouts/PageHeaderContext';

const { Text } = Typography;

interface ModelVersion {
  id: number;
  model_name: string;
  feature_set: string;
  status: string;
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
  feature_set?: string;
}

interface ModelOverview {
  versions: ModelVersion[];
  predictions: Array<Record<string, unknown>>;
  evaluations: ModelEvaluation[];
  trainingRequests: Array<Record<string, unknown>>;
}

const money = (value?: number) => `¥${Number(value ?? 0).toFixed(2)}`;
const percent = (value?: number) => value == null ? '-' : `${(Number(value) * 100).toFixed(2)}%`;

const summarizeMetrics = (raw: string) => {
  try {
    const metrics = JSON.parse(raw) as Record<string, unknown>;
    return Object.entries(metrics)
      .filter(([, value]) => ['string', 'number', 'boolean'].includes(typeof value))
      .slice(0, 3)
      .map(([key, value]) => `${key}: ${String(value)}`)
      .join(' · ') || '-';
  } catch {
    return '-';
  }
};

const ModelInfo: React.FC = () => {
  const [overview, setOverview] = useState<ModelOverview | null>(null);
  const [loading, setLoading] = useState(false);
  usePageHeader('查看模型版本、预测覆盖、评估结论和真实交易效益');

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
  const totals = useMemo(() => versions.reduce((result, item) => ({
    trades: result.trades + Number(item.realized_trade_count ?? 0),
    netProfit: result.netProfit + Number(item.net_profit ?? 0),
  }), { trades: 0, netProfit: 0 }), [versions]);

  const versionColumns: ColumnsType<ModelVersion> = [
    { title: '模型', dataIndex: 'model_name', key: 'model_name', width: 160 },
    { title: '特征集', dataIndex: 'feature_set', key: 'feature_set', width: 150 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 110, render: (value: string) => <Tag color={value === 'ACTIVE' ? 'blue' : 'default'}>{value}</Tag> },
    { title: '预测数', dataIndex: 'prediction_count', key: 'prediction_count', width: 100 },
    { title: '闭环交易', dataIndex: 'realized_trade_count', key: 'realized_trade_count', width: 100 },
    { title: '净收益', dataIndex: 'net_profit', key: 'net_profit', width: 120, render: (value: number) => <Text type={value >= 0 ? 'danger' : 'success'}>{money(value)}</Text> },
    { title: '净收益率', dataIndex: 'net_return', key: 'net_return', width: 110, render: percent },
    { title: '训练指标', dataIndex: 'metrics_json', key: 'metrics_json', ellipsis: true, render: summarizeMetrics },
    { title: '创建时间', dataIndex: 'created_at', key: 'created_at', width: 180, render: formatDateTime },
  ];

  const evaluationColumns: ColumnsType<ModelEvaluation> = [
    { title: '业务日期', dataIndex: 'business_date', key: 'business_date', width: 120 },
    { title: '模型', dataIndex: 'model_name', key: 'model_name', width: 150, render: (value?: string) => value || '未绑定模型' },
    { title: '样本', key: 'observations', width: 110, render: (_value, item) => `${item.observations}/${item.window_days}` },
    { title: '净收益率', dataIndex: 'total_net_return', key: 'total_net_return', width: 110, render: percent },
    { title: '最大回撤', dataIndex: 'max_drawdown', key: 'max_drawdown', width: 110, render: percent },
    { title: '结论', dataIndex: 'decision', key: 'decision', width: 120, render: (value: string) => <Tag>{value}</Tag> },
    { title: '原因', dataIndex: 'reason', key: 'reason', ellipsis: true },
  ];

  return (
    <div className="app-page model-info-page">
      <Row gutter={[12, 12]} className="model-info-summary">
        <Col xs={12} lg={6}><Card className="surface-card"><Statistic title="模型版本" value={versions.length} /></Card></Col>
        <Col xs={12} lg={6}><Card className="surface-card"><Statistic title="活动模型" value={versions.filter((item) => item.status === 'ACTIVE').length} /></Card></Col>
        <Col xs={12} lg={6}><Card className="surface-card"><Statistic title="模型闭环交易" value={totals.trades} /></Card></Col>
        <Col xs={12} lg={6}><Card className="surface-card"><Statistic title="模型净收益" value={totals.netProfit} precision={2} prefix="¥" /></Card></Col>
      </Row>

      <section className="model-info-section">
        <h2>模型与效益</h2>
        <Table
          rowKey="id"
          columns={versionColumns}
          dataSource={versions}
          loading={loading}
          scroll={{ x: 1180 }}
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
          locale={{ emptyText: <Empty description="尚未导入模型，暂无模型效益数据" /> }}
        />
      </section>

      <section className="model-info-section">
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
    </div>
  );
};

export default ModelInfo;
// AI_GENERATE_END -
