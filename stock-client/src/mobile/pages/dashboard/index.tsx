// AI_GENERATE_START --------
import './styles.css';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Card, Col, Empty, Row, Statistic, Table, Tag, Typography } from 'antd';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import request from '@shared/utils/request';
import type { ApiResponse } from '@shared/utils/api';
import { formatDateTime } from '@shared/utils/dateTime';
import { useAppTheme } from '@shared/theme/AppThemeProvider';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import { useTranslation } from 'react-i18next';

const { Text } = Typography;

interface BrokerAccount {
  totalAssets?: number;
  availableCash?: number;
  frozenAmount?: number;
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
  totalAssets?: number;
  availableCash?: number;
  marketValue: number;
  frozenCash?: number;
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

type TrendPeriod = 7 | 30 | 90;

interface TrendPoint {
  period: string;
  dailyProfit: number;
}

const TREND_PERIODS: TrendPeriod[] = [7, 30, 90];
const EMPTY_TREND_POINTS: TrendPoint[] = [
  { period: '', dailyProfit: 0 },
  { period: ' ', dailyProfit: 0 },
];

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

const number = (value: number) => Math.abs(value).toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});
const money = (value?: number) => value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const percent = (value?: number) => value == null ? '-' : `${(Number(value) * 100).toFixed(2)}%`;
const signedMoney = (value?: number) => value == null ? '-' : `${value >= 0 ? '+' : '-'}¥${number(value)}`;
const signedPercent = (value?: number) => value == null ? '-' : `${value >= 0 ? '+' : ''}${percent(value)}`;
const profitColor = (value?: number): 'success' | 'danger' => value != null && value >= 0 ? 'danger' : 'success';
const trendClass = (value?: number) => value == null ? '' : value < 0 ? 'is-down' : 'is-up';
const numeric = (value: unknown) => value == null || value === '' ? 0 : Number(value);
const optionalNumeric = (value: unknown) => value == null || value === '' ? undefined : Number(value);
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
    totalAssets: optionalNumeric(account.total_assets),
    availableCash: optionalNumeric(account.available_cash),
    marketValue: numeric(account.total_position),
    frozenCash: optionalNumeric(account.frozen_amount),
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
  const { mode } = useAppTheme();
  const [account, setAccount] = useState<BrokerAccount | null>(null);
  const [performance, setPerformance] = useState<PerformanceOverview | null>(null);
  const [loading, setLoading] = useState(false);
  const [trendPeriod, setTrendPeriod] = useState<TrendPeriod>(30);

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

  const trendPoints = useMemo<TrendPoint[]>(() => {
    const profitsByDate = new Map<string, number>();
    (performance?.dailyReturns ?? []).forEach((item) => {
      const period = item.period.slice(0, 10);
      if (!period) return;
      profitsByDate.set(period, (profitsByDate.get(period) ?? 0) + item.netProfit);
    });

    return Array.from(profitsByDate.entries())
      .sort(([left], [right]) => left.localeCompare(right))
      .slice(-trendPeriod)
      .map(([period, profit]) => ({ period, dailyProfit: profit }));
  }, [performance?.dailyReturns, trendPeriod]);

  const trendChartPoints = useMemo(
    () => trendPoints.length > 0 ? trendPoints : EMPTY_TREND_POINTS,
    [trendPoints],
  );
  const trendValues = useMemo(
    () => trendChartPoints.map((item) => item.dailyProfit),
    [trendChartPoints],
  );
  const trendMinimum = trendPoints.length > 0 ? Math.min(...trendValues) : undefined;
  const trendMaximum = trendPoints.length > 0 ? Math.max(...trendValues) : undefined;

  const trendOption = useMemo<EChartsOption>(() => ({
    animation: false,
    backgroundColor: 'transparent',
    grid: { left: 4, right: 4, top: 12, bottom: 24, containLabel: false },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: mode === 'dark' ? '#111827' : '#ffffff',
      borderColor: mode === 'dark' ? '#334155' : '#cbd5e1',
      textStyle: { color: mode === 'dark' ? '#f8fafc' : '#172033', fontSize: 11 },
      valueFormatter: (value) => signedMoney(Number(value)),
    },
    xAxis: {
      type: 'category',
      boundaryGap: true,
      data: trendChartPoints.map((item) => item.period),
      axisLine: { show: true, lineStyle: { color: mode === 'dark' ? '#26344a' : '#d5deea' } },
      axisTick: { show: false },
      axisLabel: {
        color: mode === 'dark' ? '#8190a8' : '#7a879b',
        fontSize: 9,
        interval: (index: number) => index === 0
          || index === Math.floor((trendChartPoints.length - 1) / 2)
          || index === trendChartPoints.length - 1,
        formatter: (value: string) => value.slice(5),
        hideOverlap: true,
      },
    },
    yAxis: {
      type: 'value',
      scale: false,
      show: false,
    },
    series: [{
      name: '每日收益',
      type: 'bar',
      data: trendValues,
      barMaxWidth: 18,
      barMinHeight: 1,
      itemStyle: {
        color: ({ value }) => Number(value) < 0
          ? mode === 'dark' ? '#34d399' : '#059669'
          : mode === 'dark' ? '#f87171' : '#dc2626',
      },
      emphasis: { disabled: true },
      markLine: {
        silent: true,
        symbol: 'none',
        label: { show: false },
        lineStyle: { color: mode === 'dark' ? '#334155' : '#d7dee9', type: 'dashed', width: 1 },
        data: [{ yAxis: 0 }],
      },
    }],
  }), [mode, trendChartPoints, trendValues]);

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

  usePageHeader('全额成本 · 账户与收益');

  return (
    <div className="app-page returns-overview-page">
      <section className="returns-overview-mobile-dashboard" aria-label="收益总览" aria-busy={loading}>
        <div className="returns-overview-mobile-hero">
          <span className="returns-overview-mobile-label">累计收益</span>
          <div className="returns-overview-mobile-hero-value">
            <strong className={trendClass(performance?.cumulativeProfit)}>{signedMoney(performance?.cumulativeProfit)}</strong>
            <span className={trendClass(performance?.cumulativeReturnRate)}>{signedPercent(performance?.cumulativeReturnRate)}</span>
          </div>
        </div>

        <div className="returns-overview-mobile-trend">
          <header>
            <strong>近 {trendPeriod} 日每日收益</strong>
            <div className="returns-overview-mobile-trend-periods" role="group" aria-label="收益曲线周期">
              {TREND_PERIODS.map((periodValue) => (
                <button
                  key={periodValue}
                  type="button"
                  className={trendPeriod === periodValue ? 'is-active' : ''}
                  aria-pressed={trendPeriod === periodValue}
                  onClick={() => setTrendPeriod(periodValue)}
                >
                  {periodValue}日
                </button>
              ))}
            </div>
          </header>
          <ReactECharts
            className="returns-overview-mobile-chart"
            option={trendOption}
            notMerge
            lazyUpdate
            opts={{ renderer: 'canvas' }}
            aria-label={`近 ${trendPeriod} 日每日收益柱状图`}
          />
          <footer>
            <span>单日最低 {signedMoney(trendMinimum)}</span>
            <span className={trendClass(trendMaximum)}>单日最高 {signedMoney(trendMaximum)}</span>
          </footer>
        </div>

        <div className="returns-overview-mobile-summary">
          <header>
            <strong>收益摘要</strong>
            <span>全额成本</span>
          </header>
          <div className="returns-overview-mobile-summary-row">
            <span>今日收益</span>
            <span className={trendClass(performance?.dailyProfit)}>
              <b>{signedMoney(performance?.dailyProfit)}</b>
              <small>{signedPercent(performance?.dailyReturnRate)}</small>
            </span>
          </div>
          <div className="returns-overview-mobile-summary-row">
            <span>本月收益</span>
            <span className={trendClass(performance?.monthlyProfit)}>
              <b>{signedMoney(performance?.monthlyProfit)}</b>
              <small>{signedPercent(performance?.monthlyReturnRate)}</small>
            </span>
          </div>
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
            <Statistic title={t('dashboard.cards.availableCash')} value={account?.availableCash} precision={3} prefix="¥" />
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
// AI_GENERATE_END --------
