// AI_GENERATE_START ---------------
import './styles.css';
import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Col, Empty, Row, Statistic, Table, Tag, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import request from '../../utils/request';
import type { ApiResponse } from '../../utils/api';
import { formatDateTime } from '../../utils/dateTime';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import { useTranslation } from 'react-i18next';

const { Text } = Typography;

interface BrokerAccount {
  totalAssets: number;
  availableCash: number;
  frozenAmount: number;
  totalPosition: number;
  capturedAt: string;
}

interface BrokerPosition {
  stockCode: string;
  stockName: string;
  totalQuantity: number;
  availableQuantity: number;
  averageCost: number;
  currentPrice: number;
  marketValue: number;
}

interface BrokerPositionList {
  items: BrokerPosition[];
}

interface PerformanceRankingItem {
  stockCode: string;
  stockName?: string;
  transactionAmount: number;
  buyAmount: number;
  sellAmount: number;
  tradeQuantity: number;
  netProfit: number;
  returnRate: number;
  dailyReturnRate: number;
  buyTime?: string;
  holdingDays?: number;
  sellTime?: string;
}

interface PerformancePeriodItem {
  period: string;
  netProfit: number;
  cumulativeProfit: number;
  returnRate: number;
  tradeCount: number;
  winningTradeCount: number;
}

interface PerformanceDailyReturnItem {
  period: string;
  stockCode: string;
  stockName?: string;
  netProfit: number;
  buyAmount: number;
  sellAmount: number;
  returnRate: number;
}

interface PerformanceOverview {
  capturedAt?: string;
  totalAssets: number;
  availableCash: number;
  marketValue: number;
  frozenCash: number;
  cumulativeProfit: number;
  cumulativeReturnRate: number;
  dailyProfit: number;
  dailyReturnRate: number;
  monthlyProfit: number;
  monthlyReturnRate: number;
  totalTradeCount: number;
  closedTradeCount: number;
  winningTradeCount: number;
  losingTradeCount: number;
  winRate: number;
  averageReturnRate: number;
  bestReturnRate: number;
  worstReturnRate: number;
  ranking: PerformanceRankingItem[];
  dailyReturns: PerformanceDailyReturnItem[];
  monthlyReturns: PerformancePeriodItem[];
}

type ApiResult<T> = ApiResponse<T> | T;

const readApiData = <T,>(result: ApiResult<T>): T | null => {
  if (result && typeof result === 'object' && 'success' in result && 'data' in result) {
    const response = result as ApiResponse<T>;
    return response.success ? response.data : null;
  }
  return result as T;
};

const money = (value?: number) => value == null ? '-' : `¥${Number(value).toFixed(2)}`;
const percent = (value?: number) => value == null ? '-' : `${(Number(value) * 100).toFixed(2)}%`;
const profitColor = (value?: number): 'success' | 'danger' => value != null && value >= 0 ? 'danger' : 'success';

const Dashboard: React.FC = () => {
  const { t } = useTranslation();
  const [account, setAccount] = useState<BrokerAccount | null>(null);
  const [positions, setPositions] = useState<BrokerPosition[]>([]);
  const [performance, setPerformance] = useState<PerformanceOverview | null>(null);
  const [loading, setLoading] = useState(false);

  const loadBrokerData = useCallback(async () => {
    setLoading(true);
    const [accountResult, positionResult, performanceResult] = await Promise.allSettled([
      request.get('/brokerQuery/account') as Promise<ApiResult<BrokerAccount>>,
      request.get('/brokerQuery/positions') as Promise<ApiResult<BrokerPositionList>>,
      request.get('/performance/overview') as Promise<ApiResult<PerformanceOverview>>,
    ]);

    const accountData = accountResult.status === 'fulfilled' ? readApiData(accountResult.value) : null;
    const positionData = positionResult.status === 'fulfilled' ? readApiData(positionResult.value) : null;
    const performanceData = performanceResult.status === 'fulfilled' ? readApiData(performanceResult.value) : null;

    if (accountData) {
      setAccount(accountData);
    } else if (performanceData) {
      setAccount({
        totalAssets: performanceData.totalAssets,
        availableCash: performanceData.availableCash,
        frozenAmount: performanceData.frozenCash,
        totalPosition: performanceData.marketValue,
        capturedAt: performanceData.capturedAt ?? '',
      });
    }

    if (performanceData) {
      setPerformance(performanceData);
    }

    if (positionData) {
      setPositions(positionData.items ?? []);
    }

    setLoading(false);
  }, []);

  useEffect(() => {
    void loadBrokerData();
  }, [loadBrokerData]);

  const positionColumns = [
    { title: t('dashboard.columns.stockCode'), dataIndex: 'stockCode', key: 'stockCode' },
    { title: t('dashboard.columns.stockName'), dataIndex: 'stockName', key: 'stockName' },
    { title: t('dashboard.columns.totalQuantity'), dataIndex: 'totalQuantity', key: 'totalQuantity' },
    { title: t('dashboard.columns.availableQuantity'), dataIndex: 'availableQuantity', key: 'availableQuantity' },
    { title: t('dashboard.columns.averageCost'), dataIndex: 'averageCost', key: 'averageCost', render: (value: number) => money(value) },
    { title: t('dashboard.columns.currentPrice'), dataIndex: 'currentPrice', key: 'currentPrice', render: (value: number) => money(value) },
    { title: t('dashboard.columns.marketValue'), dataIndex: 'marketValue', key: 'marketValue', render: (value: number) => money(value) },
  ];

  const rankingColumns = [
    { title: t('dashboard.columns.stockCode'), dataIndex: 'stockCode', key: 'stockCode', width: 110 },
    { title: t('dashboard.columns.stockName'), dataIndex: 'stockName', key: 'stockName', ellipsis: true },
    { title: t('dashboard.columns.buyAmount'), dataIndex: 'buyAmount', key: 'buyAmount', width: 120, render: money },
    { title: t('dashboard.columns.sellAmount'), dataIndex: 'sellAmount', key: 'sellAmount', width: 120, render: money },
    { title: t('dashboard.columns.tradeQuantity'), dataIndex: 'tradeQuantity', key: 'tradeQuantity', width: 110 },
    { title: t('dashboard.columns.netProfit'), dataIndex: 'netProfit', key: 'netProfit', width: 120, render: (value: number) => <Text type={profitColor(value)}>{money(value)}</Text> },
    { title: t('dashboard.columns.returnRate'), dataIndex: 'returnRate', key: 'returnRate', width: 110, render: (value: number) => <Text type={profitColor(value)}>{percent(value)}</Text> },
    { title: t('dashboard.columns.dailyReturnRate'), dataIndex: 'dailyReturnRate', key: 'dailyReturnRate', width: 110, render: (value: number) => <Text type={profitColor(value)}>{percent(value)}</Text> },
    { title: t('dashboard.columns.buyTime'), dataIndex: 'buyTime', key: 'buyTime', width: 170, render: formatDateTime },
    { title: t('dashboard.columns.sellTime'), dataIndex: 'sellTime', key: 'sellTime', width: 170, render: formatDateTime },
    { title: t('dashboard.columns.holdingDays'), dataIndex: 'holdingDays', key: 'holdingDays', width: 110, render: (value?: number) => value == null ? '-' : `${Number(value).toFixed(2)} ${t('dashboard.units.days')}` },
  ];

  const periodColumns = [
    { title: t('dashboard.columns.period'), dataIndex: 'period', key: 'period' },
    { title: t('dashboard.columns.netProfit'), dataIndex: 'netProfit', key: 'netProfit', render: (value: number) => <Text type={profitColor(value)}>{money(value)}</Text> },
    { title: t('dashboard.columns.returnRate'), dataIndex: 'returnRate', key: 'returnRate', render: (value: number) => <Text type={profitColor(value)}>{percent(value)}</Text> },
    { title: t('dashboard.columns.tradeCount'), dataIndex: 'tradeCount', key: 'tradeCount' },
    { title: t('dashboard.columns.winningTradeCount'), dataIndex: 'winningTradeCount', key: 'winningTradeCount' },
  ];

  const dailyReturnColumns = [
    { title: t('dashboard.columns.period'), dataIndex: 'period', key: 'period' },
    { title: t('dashboard.columns.stock'), key: 'stock', render: (_value: unknown, item: PerformanceDailyReturnItem) => `${item.stockCode} ${item.stockName ?? ''}` },
    { title: t('dashboard.columns.buyAmount'), dataIndex: 'buyAmount', key: 'buyAmount', render: money },
    { title: t('dashboard.columns.sellAmount'), dataIndex: 'sellAmount', key: 'sellAmount', render: money },
    { title: t('dashboard.columns.netProfit'), dataIndex: 'netProfit', key: 'netProfit', render: (value: number) => <Text type={profitColor(value)}>{money(value)}</Text> },
    { title: t('dashboard.columns.returnRate'), dataIndex: 'returnRate', key: 'returnRate', render: (value: number) => <Text type={profitColor(value)}>{percent(value)}</Text> },
  ];

  usePageHeader(
    `${t('dashboard.subtitle')}${account?.capturedAt ? `, ${t('dashboard.lastSync')}: ${formatDateTime(account.capturedAt)}` : ''}`,
  );

  return (
    <div className="app-page">
      <div className="flex justify-end">
        <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadBrokerData()}>{t('common.refresh')}</Button>
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} md={6}><Card className="surface-card compact-card" loading={loading}><Statistic title={t('dashboard.cards.totalAssets')} value={account?.totalAssets} precision={2} prefix="¥" /></Card></Col>
        <Col xs={24} md={6}><Card className="surface-card compact-card" loading={loading}><Statistic title={t('dashboard.cards.availableCash')} value={account?.availableCash} precision={2} prefix="¥" /></Card></Col>
        <Col xs={24} md={6}><Card className="surface-card compact-card" loading={loading}><Statistic title={t('dashboard.cards.positionValue')} value={account?.totalPosition} precision={2} prefix="¥" /></Card></Col>
        <Col xs={24} md={6}><Card className="surface-card compact-card" loading={loading}><Statistic title={t('dashboard.cards.frozenCash')} value={account?.frozenAmount} precision={2} prefix="¥" /></Card></Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}><Card className="surface-card compact-card" loading={loading}><Statistic title={t('dashboard.cards.realizedTotal')} value={performance?.cumulativeProfit} precision={2} prefix="¥" /><Text type={profitColor(performance?.cumulativeProfit)}>{t('dashboard.cards.realizedRate')} {percent(performance?.cumulativeReturnRate)}</Text></Card></Col>
        <Col xs={24} md={8}><Card className="surface-card compact-card" loading={loading}><Statistic title={t('dashboard.cards.realizedToday')} value={performance?.dailyProfit} precision={2} prefix="¥" /><Text type={profitColor(performance?.dailyProfit)}>{t('dashboard.cards.dailyRate')} {percent(performance?.dailyReturnRate)}</Text></Card></Col>
        <Col xs={24} md={8}><Card className="surface-card compact-card" loading={loading}><Statistic title={t('dashboard.cards.realizedMonth')} value={performance?.monthlyProfit} precision={2} prefix="¥" /><Text type={profitColor(performance?.monthlyProfit)}>{t('dashboard.cards.monthlyRate')} {percent(performance?.monthlyReturnRate)}</Text></Card></Col>
      </Row>

      <Card className="surface-card" title={t('dashboard.sections.returnStats')}>
        <Row gutter={[16, 16]}>
          <Col xs={12} md={6}><Statistic title={t('dashboard.sections.completedTrades')} value={performance?.closedTradeCount ?? 0} suffix={`/ ${performance?.totalTradeCount ?? 0}`} /></Col>
          <Col xs={12} md={6}><Statistic title={t('dashboard.sections.winRate')} value={performance ? Number((performance.winRate * 100).toFixed(2)) : 0} suffix="%" /></Col>
          <Col xs={12} md={6}><Statistic title={t('dashboard.sections.averageReturn')} value={performance ? Number((performance.averageReturnRate * 100).toFixed(2)) : 0} suffix="%" /></Col>
          <Col xs={12} md={6}><Statistic title={t('dashboard.sections.profitLoss')} value={`${performance?.winningTradeCount ?? 0} / ${performance?.losingTradeCount ?? 0}`} /></Col>
        </Row>
        <div className="mt-5 flex flex-wrap gap-3 text-sm">
          <Tag color="red">{t('dashboard.sections.best')} {percent(performance?.bestReturnRate)}</Tag>
          <Tag color="green">{t('dashboard.sections.worst')} {percent(performance?.worstReturnRate)}</Tag>
          <Text type="secondary">{t('dashboard.sections.basis')}</Text>
        </div>
      </Card>

      <Row className="dashboard-table-row" gutter={[16, 16]}>
        <Col className="dashboard-table-col" xs={24} xl={12}>
          <Card className="surface-card dashboard-table-card" title={t('dashboard.sections.daily')}>
            <Table rowKey={(item) => `${item.period}-${item.stockCode}`} columns={dailyReturnColumns} dataSource={performance?.dailyReturns ?? []} loading={loading} pagination={{ pageSize: 10 }} locale={{ emptyText: <Empty description={t('dashboard.empty.daily')} /> }} />
          </Card>
        </Col>
        <Col className="dashboard-table-col" xs={24} xl={12}>
          <Card className="surface-card dashboard-table-card" title={t('dashboard.sections.monthly')}>
            <Table rowKey="period" columns={periodColumns} dataSource={performance?.monthlyReturns ?? []} loading={loading} pagination={{ pageSize: 10 }} locale={{ emptyText: <Empty description={t('dashboard.empty.monthly')} /> }} />
          </Card>
        </Col>
      </Row>

      <Card className="surface-card dashboard-table-card" title={t('dashboard.sections.ranking')}>
        <Table rowKey={(item) => `${item.stockCode}-${item.sellTime ?? item.buyTime}`} columns={rankingColumns} dataSource={performance?.ranking ?? []} loading={loading} scroll={{ x: 1450 }} pagination={{ pageSize: 10 }} locale={{ emptyText: <Empty description={t('dashboard.empty.ranking')} /> }} />
      </Card>

      <Card className="surface-card" title={t('dashboard.sections.positions')}>
        <Table
          rowKey={(record) => record.stockCode}
          columns={positionColumns}
          dataSource={positions}
          loading={loading}
          locale={{ emptyText: <Empty description={t('dashboard.empty.positions')} /> }}
          pagination={{ pageSize: 10 }}
        />
      </Card>

    </div>
  );
};

export default Dashboard;
// AI_GENERATE_END ---------------
