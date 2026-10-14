// AI_GENERATE_START -------
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Empty, Row, Statistic, Table, Tag, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import request from '../utils/request';
import type { ApiResponse } from '../utils/api';
import { formatDateTime } from '../utils/dateTime';
import { usePageHeader } from '../layouts/PageHeaderContext';

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
  status: string;
  netProfit: number;
  returnRate: number;
  dailyReturnRate: number;
  holdingDays?: number;
  sellTime?: string;
}

interface PerformancePeriodItem {
  period: string;
  netProfit: number;
  returnRate: number;
  tradeCount: number;
  winningTradeCount: number;
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
  dailyReturns: PerformancePeriodItem[];
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
const profitColor = (value?: number): 'success' | 'danger' => value != null && value >= 0 ? 'success' : 'danger';

const Dashboard: React.FC = () => {
  const [account, setAccount] = useState<BrokerAccount | null>(null);
  const [positions, setPositions] = useState<BrokerPosition[]>([]);
  const [performance, setPerformance] = useState<PerformanceOverview | null>(null);
  const [loading, setLoading] = useState(false);

  const loadBrokerData = useCallback(async () => {
    setLoading(true);
    const [accountResult, positionResult, performanceResult] = await Promise.allSettled([
      request.get('/broker-query/account') as Promise<ApiResult<BrokerAccount>>,
      request.get('/broker-query/positions') as Promise<ApiResult<BrokerPositionList>>,
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

    if (positionData) {
      setPositions(positionData.items ?? []);
    }

    if (performanceData) {
      setPerformance(performanceData);
    }

    setLoading(false);
  }, []);

  useEffect(() => {
    void loadBrokerData();
  }, [loadBrokerData]);

  const positionColumns = [
    { title: '股票代码', dataIndex: 'stockCode', key: 'stockCode' },
    { title: '股票名称', dataIndex: 'stockName', key: 'stockName' },
    { title: '持仓数量', dataIndex: 'totalQuantity', key: 'totalQuantity' },
    { title: '可卖数量', dataIndex: 'availableQuantity', key: 'availableQuantity' },
    { title: '成本价', dataIndex: 'averageCost', key: 'averageCost', render: (value: number) => money(value) },
    { title: '现价', dataIndex: 'currentPrice', key: 'currentPrice', render: (value: number) => money(value) },
    { title: '市值', dataIndex: 'marketValue', key: 'marketValue', render: (value: number) => money(value) },
  ];

  const rankingColumns = [
    { title: '股票', key: 'stock', render: (_value: unknown, item: PerformanceRankingItem) => `${item.stockCode} ${item.stockName ?? ''}` },
    { title: '净收益', dataIndex: 'netProfit', key: 'netProfit', render: (value: number) => <Text type={profitColor(value)}>{money(value)}</Text> },
    { title: '收益率', dataIndex: 'returnRate', key: 'returnRate', render: (value: number) => <Text type={profitColor(value)}>{percent(value)}</Text> },
    { title: '日收益率', dataIndex: 'dailyReturnRate', key: 'dailyReturnRate', render: percent },
    { title: '持仓天数', dataIndex: 'holdingDays', key: 'holdingDays', render: (value?: number) => value == null ? '-' : `${Number(value).toFixed(2)} 天` },
    { title: '状态', dataIndex: 'status', key: 'status', render: (value: string) => <Tag color={value === 'CLOSED' ? 'green' : 'blue'}>{value === 'CLOSED' ? '已完成' : '部分卖出'}</Tag> },
  ];

  const periodColumns = [
    { title: '周期', dataIndex: 'period', key: 'period' },
    { title: '净收益', dataIndex: 'netProfit', key: 'netProfit', render: (value: number) => <Text type={profitColor(value)}>{money(value)}</Text> },
    { title: '收益率', dataIndex: 'returnRate', key: 'returnRate', render: (value: number) => <Text type={profitColor(value)}>{percent(value)}</Text> },
    { title: '交易笔数', dataIndex: 'tradeCount', key: 'tradeCount' },
    { title: '盈利笔数', dataIndex: 'winningTradeCount', key: 'winningTradeCount' },
  ];

  const headerActions = useMemo(() => (
    <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadBrokerData()}>刷新</Button>
  ), [loadBrokerData, loading]);

  usePageHeader(
    `账户资产、已实现收益和交易统计报表${account?.capturedAt ? `，最近同步：${formatDateTime(account.capturedAt)}` : ''}`,
    headerActions,
  );

  return (
    <div className="app-page">
      <Row gutter={[16, 16]}>
        <Col xs={24} md={6}><Card className="surface-card compact-card" loading={loading}><Statistic title="总资产" value={account?.totalAssets} precision={2} prefix="¥" /></Card></Col>
        <Col xs={24} md={6}><Card className="surface-card compact-card" loading={loading}><Statistic title="可用资金" value={account?.availableCash} precision={2} prefix="¥" /></Card></Col>
        <Col xs={24} md={6}><Card className="surface-card compact-card" loading={loading}><Statistic title="持仓市值" value={account?.totalPosition} precision={2} prefix="¥" /></Card></Col>
        <Col xs={24} md={6}><Card className="surface-card compact-card" loading={loading}><Statistic title="冻结资金" value={account?.frozenAmount} precision={2} prefix="¥" /></Card></Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}><Card className="surface-card compact-card" loading={loading}><Statistic title="累计已实现收益" value={performance?.cumulativeProfit} precision={2} prefix="¥" /><Text type={profitColor(performance?.cumulativeProfit)}>累计收益率 {percent(performance?.cumulativeReturnRate)}</Text></Card></Col>
        <Col xs={24} md={8}><Card className="surface-card compact-card" loading={loading}><Statistic title="今日已实现收益" value={performance?.dailyProfit} precision={2} prefix="¥" /><Text type={profitColor(performance?.dailyProfit)}>日收益率 {percent(performance?.dailyReturnRate)}</Text></Card></Col>
        <Col xs={24} md={8}><Card className="surface-card compact-card" loading={loading}><Statistic title="本月已实现收益" value={performance?.monthlyProfit} precision={2} prefix="¥" /><Text type={profitColor(performance?.monthlyProfit)}>月收益率 {percent(performance?.monthlyReturnRate)}</Text></Card></Col>
      </Row>

      <Card className="surface-card" title="收益率统计">
        <Row gutter={[16, 16]}>
          <Col xs={12} md={6}><Statistic title="已完成交易" value={performance?.closedTradeCount ?? 0} suffix={`/ ${performance?.totalTradeCount ?? 0}`} /></Col>
          <Col xs={12} md={6}><Statistic title="胜率" value={performance ? Number((performance.winRate * 100).toFixed(2)) : 0} suffix="%" /></Col>
          <Col xs={12} md={6}><Statistic title="平均收益率" value={performance ? Number((performance.averageReturnRate * 100).toFixed(2)) : 0} suffix="%" /></Col>
          <Col xs={12} md={6}><Statistic title="盈利 / 亏损" value={`${performance?.winningTradeCount ?? 0} / ${performance?.losingTradeCount ?? 0}`} /></Col>
        </Row>
        <div className="mt-5 flex flex-wrap gap-3 text-sm">
          <Tag color="green">最高收益率 {percent(performance?.bestReturnRate)}</Tag>
          <Tag color="red">最低收益率 {percent(performance?.worstReturnRate)}</Tag>
          <Text type="secondary">收益指标基于已实现 FIFO 交易记录计算</Text>
        </div>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={12}>
          <Card className="surface-card" title="收益率排行">
            <Table rowKey={(item) => `${item.stockCode}-${item.sellTime ?? item.status}`} columns={rankingColumns} dataSource={performance?.ranking ?? []} loading={loading} pagination={false} locale={{ emptyText: <Empty description="暂无已实现交易收益排行" /> }} />
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card className="surface-card" title="日收益率">
            <Table rowKey="period" columns={periodColumns} dataSource={performance?.dailyReturns ?? []} loading={loading} pagination={false} locale={{ emptyText: <Empty description="暂无日收益数据" /> }} />
          </Card>
        </Col>
      </Row>

      <Card className="surface-card" title="月收益率">
        <Table rowKey="period" columns={periodColumns} dataSource={performance?.monthlyReturns ?? []} loading={loading} pagination={false} locale={{ emptyText: <Empty description="暂无月收益数据" /> }} />
      </Card>

      <Card className="surface-card" title="当前持仓">
        <Table
          rowKey={(record) => record.stockCode}
          columns={positionColumns}
          dataSource={positions}
          loading={loading}
          locale={{ emptyText: <Empty description="暂无真实持仓数据" /> }}
          pagination={false}
        />
      </Card>
    </div>
  );
};

export default Dashboard;
// AI_GENERATE_END -------
