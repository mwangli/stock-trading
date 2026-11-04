// AI_GENERATE_START-----------------------------------------
import './styles.css';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, Col, Empty, Row, Statistic, Table, Tag, Typography } from 'antd';
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

interface PerformanceRankingItem {
  tradeKey: string;
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
  investedAmount?: number;
}

interface PerformanceSummary {
  total_trade_count?: number;
  winning_trade_count?: number;
  losing_trade_count?: number;
  net_profit?: number;
  net_return?: number;
  average_net_return?: number;
  best_net_return?: number;
  worst_net_return?: number;
  daily_net_profit?: number;
  daily_net_return?: number;
  monthly_net_profit?: number;
  monthly_net_return?: number;
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

interface DashboardOverview {
  account: Record<string, unknown>;
  positions: Array<Record<string, unknown>>;
  performance: Array<Record<string, unknown>>;
  performanceSummary?: PerformanceSummary;
  monthlyPerformance?: Array<Record<string, unknown>>;
  ranking: Array<Record<string, unknown>>;
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
const numeric = (value: unknown) => value == null || value === '' ? 0 : Number(value);
const text = (value: unknown) => value == null ? '' : String(value);
const dailyReturnRate = (returnRate: number, holdingDays?: number) => (
  holdingDays != null && holdingDays > 1 && returnRate > -1
    ? Math.pow(1 + returnRate, 1 / holdingDays) - 1
    : returnRate
);

const aggregateReturnRate = (rows: Array<Record<string, unknown>>) => {
  const netProfit = rows.reduce((sum, item) => sum + numeric(item.net_pnl), 0);
  const investedAmount = rows.reduce((sum, item) => sum + numeric(item.cash) + numeric(item.buy_fee), 0);
  return investedAmount ? netProfit / investedAmount : 0;
};

const adaptDashboardOverview = (overview: DashboardOverview): PerformanceOverview => {
  const performanceRows = overview.performance ?? [];
  const latest = performanceRows[0] ?? {};
  const latestPeriod = text(latest.business_date);
  const latestRows = performanceRows.filter((item) => text(item.business_date) === latestPeriod);
  const summary = overview.performanceSummary ?? {};
  const account = overview.account ?? {};
  const netReturns = performanceRows.map((item) => numeric(item.net_return));
  const netProfits = performanceRows.map((item) => numeric(item.net_pnl));
  const winningTradeCount = netProfits.filter((value) => value > 0).length;
  const losingTradeCount = netProfits.filter((value) => value < 0).length;
  const monthly = new Map<string, PerformancePeriodItem>();

  performanceRows.forEach((item) => {
    const period = text(item.business_date).slice(0, 7) || '-';
    const current = monthly.get(period) ?? {
      period,
      netProfit: 0,
      cumulativeProfit: 0,
      returnRate: 0,
      tradeCount: 0,
      winningTradeCount: 0,
      investedAmount: 0,
    };
    const profit = numeric(item.net_pnl);
    current.netProfit += profit;
    current.investedAmount = numeric(current.investedAmount) + numeric(item.cash) + numeric(item.buy_fee);
    current.returnRate = current.investedAmount ? current.netProfit / current.investedAmount : 0;
    current.tradeCount += 1;
    current.winningTradeCount += profit > 0 ? 1 : 0;
    monthly.set(period, current);
  });

  let cumulativeProfit = 0;
  const monthlyRows = overview.monthlyPerformance?.length
    ? overview.monthlyPerformance.map((item): PerformancePeriodItem => ({
      period: text(item.period),
      netProfit: numeric(item.net_profit),
      cumulativeProfit: 0,
      returnRate: numeric(item.net_return),
      tradeCount: numeric(item.trade_count),
      winningTradeCount: numeric(item.winning_trade_count),
    }))
    : Array.from(monthly.values());
  const monthlyReturns = monthlyRows.slice().reverse().map((item) => {
    cumulativeProfit += item.netProfit;
    return { ...item, cumulativeProfit };
  }).reverse();

  const ranking = (overview.ranking ?? []).map((item): PerformanceRankingItem => {
    const returnRate = numeric(item.return_rate);
    const buyTime = text(item.buy_time);
    const sellTime = text(item.sell_time);
    const holdingDays = item.holding_days == null
      ? undefined
      : Math.max(0, Math.trunc(numeric(item.holding_days)));
    return {
      tradeKey: text(item.trade_key),
      stockCode: text(item.instrument),
      stockName: text(item.stock_name),
      transactionAmount: numeric(item.transaction_amount),
      buyAmount: numeric(item.buy_amount),
      sellAmount: numeric(item.sell_amount),
      tradeQuantity: numeric(item.trade_quantity),
      netProfit: numeric(item.net_profit),
      returnRate,
      dailyReturnRate: dailyReturnRate(returnRate, holdingDays),
      buyTime,
      holdingDays,
      sellTime,
    };
  });

  return {
    capturedAt: text(account.captured_at),
    totalAssets: numeric(account.total_assets),
    availableCash: numeric(account.available_cash),
    marketValue: numeric(account.total_position),
    frozenCash: numeric(account.frozen_amount),
    cumulativeProfit: summary.net_profit == null ? netProfits.reduce((sum, value) => sum + value, 0) : numeric(summary.net_profit),
    cumulativeReturnRate: summary.net_return == null ? aggregateReturnRate(performanceRows) : numeric(summary.net_return),
    dailyProfit: summary.daily_net_profit == null ? latestRows.reduce((sum, item) => sum + numeric(item.net_pnl), 0) : numeric(summary.daily_net_profit),
    dailyReturnRate: summary.daily_net_return == null ? aggregateReturnRate(latestRows) : numeric(summary.daily_net_return),
    monthlyProfit: summary.monthly_net_profit == null ? monthlyReturns[0]?.netProfit ?? 0 : numeric(summary.monthly_net_profit),
    monthlyReturnRate: summary.monthly_net_return == null ? monthlyReturns[0]?.returnRate ?? 0 : numeric(summary.monthly_net_return),
    totalTradeCount: summary.total_trade_count == null ? performanceRows.length : numeric(summary.total_trade_count),
    closedTradeCount: summary.total_trade_count == null ? performanceRows.length : numeric(summary.total_trade_count),
    winningTradeCount: summary.winning_trade_count == null ? winningTradeCount : numeric(summary.winning_trade_count),
    losingTradeCount: summary.losing_trade_count == null ? losingTradeCount : numeric(summary.losing_trade_count),
    winRate: numeric(summary.total_trade_count) > 0
      ? numeric(summary.winning_trade_count) / numeric(summary.total_trade_count)
      : performanceRows.length ? winningTradeCount / performanceRows.length : 0,
    averageReturnRate: summary.average_net_return == null
      ? netReturns.length ? netReturns.reduce((sum, value) => sum + value, 0) / netReturns.length : 0
      : numeric(summary.average_net_return),
    bestReturnRate: summary.best_net_return == null ? netReturns.length ? Math.max(...netReturns) : 0 : numeric(summary.best_net_return),
    worstReturnRate: summary.worst_net_return == null ? netReturns.length ? Math.min(...netReturns) : 0 : numeric(summary.worst_net_return),
    ranking,
    dailyReturns: performanceRows.map((item) => ({
      period: text(item.business_date),
      stockCode: text(item.scenario || item.model_version_id || 'LIVE'),
      stockName: text(item.scenario),
      netProfit: numeric(item.net_pnl),
      buyAmount: numeric(item.cash),
      sellAmount: numeric(item.equity),
      returnRate: numeric(item.net_return),
    })),
    monthlyReturns,
  };
};

const Dashboard: React.FC = () => {
  const { t } = useTranslation();
  const [account, setAccount] = useState<BrokerAccount | null>(null);
  const [performance, setPerformance] = useState<PerformanceOverview | null>(null);
  const [loading, setLoading] = useState(false);

  const loadBrokerData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await request.get('/dashboard/overview') as ApiResult<DashboardOverview>;
      const overview = readApiData(result);
      if (overview) {
        const performanceData = adaptDashboardOverview(overview);
        setPerformance(performanceData);
        setAccount({
          totalAssets: performanceData.totalAssets,
          availableCash: performanceData.availableCash,
          frozenAmount: performanceData.frozenCash,
          totalPosition: performanceData.marketValue,
          capturedAt: performanceData.capturedAt ?? '',
        });
      }
    } catch {
      setAccount(null);
      setPerformance(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadBrokerData();
  }, [loadBrokerData]);

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
    { title: t('dashboard.columns.holdingDays'), dataIndex: 'holdingDays', key: 'holdingDays', width: 110, render: (value?: number) => value == null ? '-' : `${Math.trunc(value)} ${t('dashboard.units.days')}` },
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
    <div className="app-page returns-overview-page">
      <section className="returns-overview-mobile-lead" aria-label={t('dashboard.title')}>
        <span>{t('dashboard.cards.positionValue')}</span>
        <strong>{money(account?.totalPosition)}</strong>
        <div>
          <span>{t('dashboard.cards.availableCash')} <b>{money(account?.availableCash)}</b></span>
          <Text type={profitColor(performance?.cumulativeProfit)}>
            {t('dashboard.cards.realizedRate')} <b>{percent(performance?.cumulativeReturnRate)}</b>
          </Text>
        </div>
      </section>

      <Row className="returns-overview-metric-grid" gutter={[12, 12]}>
        <Col xs={12} md={6}>
          <Card className="surface-card compact-card returns-overview-metric-card" loading={loading}>
            <Statistic title={t('dashboard.cards.positionValue')} value={account?.totalPosition} precision={2} prefix="¥" />
            <span className="returns-overview-rate returns-overview-rate-placeholder" aria-hidden="true">-</span>
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card className="surface-card compact-card returns-overview-metric-card" loading={loading}>
            <Statistic title={t('dashboard.cards.availableCash')} value={account?.availableCash} precision={2} prefix="¥" />
            <span className="returns-overview-rate returns-overview-rate-placeholder" aria-hidden="true">-</span>
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card className="surface-card compact-card returns-overview-metric-card" loading={loading}>
            <Statistic title={t('dashboard.cards.realizedToday')} value={performance?.dailyProfit} precision={2} prefix="¥" />
            <Text className="returns-overview-rate" type={profitColor(performance?.dailyProfit)}>
              {t('dashboard.cards.dailyRate')} <strong>{percent(performance?.dailyReturnRate)}</strong>
            </Text>
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card className="surface-card compact-card returns-overview-metric-card" loading={loading}>
            <Statistic title={t('dashboard.cards.realizedMonth')} value={performance?.monthlyProfit} precision={2} prefix="¥" />
            <Text className="returns-overview-rate" type={profitColor(performance?.monthlyProfit)}>
              {t('dashboard.cards.monthlyRate')} <strong>{percent(performance?.monthlyReturnRate)}</strong>
            </Text>
          </Card>
        </Col>
      </Row>

      <Card
        className="surface-card returns-overview-summary-card"
        title={t('dashboard.sections.returnStats')}
        extra={(
          <div className="returns-overview-summary-extra">
            <Tag color="red">{t('dashboard.sections.best')} {percent(performance?.bestReturnRate)}</Tag>
            <Tag color="green">{t('dashboard.sections.worst')} {percent(performance?.worstReturnRate)}</Tag>
          </div>
        )}
      >
        <Row gutter={[12, 12]}>
          <Col xs={12} md={6}><Statistic title={t('dashboard.sections.completedTrades')} value={performance?.closedTradeCount ?? 0} suffix={`/ ${performance?.totalTradeCount ?? 0}`} /></Col>
          <Col xs={12} md={6}><Statistic title={t('dashboard.sections.winRate')} value={performance ? Number((performance.winRate * 100).toFixed(2)) : 0} suffix="%" /></Col>
          <Col xs={12} md={6}><Statistic title={t('dashboard.sections.averageReturn')} value={performance ? Number((performance.averageReturnRate * 100).toFixed(2)) : 0} suffix="%" /></Col>
          <Col xs={12} md={6}><Statistic title={t('dashboard.sections.profitLoss')} value={`${performance?.winningTradeCount ?? 0} / ${performance?.losingTradeCount ?? 0}`} /></Col>
        </Row>
      </Card>

      <Row className="dashboard-table-row" gutter={[12, 12]}>
        <Col className="dashboard-table-col" xs={24} xl={12}>
          <Card className="surface-card dashboard-table-card" title={t('dashboard.sections.daily')}>
            <Table rowKey={(item) => `${item.period}-${item.stockCode}`} columns={dailyReturnColumns} dataSource={performance?.dailyReturns ?? []} loading={loading} scroll={{ x: 720 }} pagination={{ pageSize: 10 }} locale={{ emptyText: <Empty description={t('dashboard.empty.daily')} /> }} />
          </Card>
        </Col>
        <Col className="dashboard-table-col" xs={24} xl={12}>
          <Card className="surface-card dashboard-table-card" title={t('dashboard.sections.monthly')}>
            <Table rowKey="period" columns={periodColumns} dataSource={performance?.monthlyReturns ?? []} loading={loading} scroll={{ x: 620 }} pagination={{ pageSize: 10 }} locale={{ emptyText: <Empty description={t('dashboard.empty.monthly')} /> }} />
          </Card>
        </Col>
      </Row>

      <Card className="surface-card dashboard-table-card dashboard-ranking-card" title={t('dashboard.sections.ranking')}>
        <Table rowKey={(item) => item.tradeKey || `${item.stockCode}-${item.sellTime ?? item.buyTime}`} columns={rankingColumns} dataSource={performance?.ranking ?? []} loading={loading} scroll={{ x: 1450 }} pagination={{ pageSize: 10 }} locale={{ emptyText: <Empty description={t('dashboard.empty.ranking')} /> }} />
      </Card>

    </div>
  );
};

export default Dashboard;
// AI_GENERATE_END-----------------------------------------
