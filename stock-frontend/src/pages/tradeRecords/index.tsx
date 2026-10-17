// AI_GENERATE_START ----------------
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Empty, Input, Pagination, Space, Table, Tooltip, Typography } from 'antd';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import request from '../../utils/request';
import type { ApiResponse } from '../../utils/api';
import { formatDateTime } from '../../utils/dateTime';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import { useTranslation } from 'react-i18next';

const { Text } = Typography;

interface TradeRecord {
  stockCode: string;
  stockName: string;
  buyTime: string;
  firstSellTime?: string;
  lastSellTime?: string;
  buyQuantity: number;
  soldQuantity: number;
  remainingQuantity: number;
  buyPrice: number;
  averageSellPrice?: number;
  buyAmount: number;
  sellAmount: number;
  totalFee: number;
  grossProfit: number;
  netProfit: number;
  grossReturnRate: number;
  returnRate: number;
  holdingDays?: number;
  dailyReturnRate: number;
  grossDailyReturnRate: number;
}

interface BrokerPage<T> {
  items: T[];
  total: number;
}

interface TradeFilters {
  keyword: string;
  startDate: string;
  endDate: string;
}

interface PageState {
  current: number;
  pageSize: number;
  total: number;
}

const formatLocalDate = (date: Date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const buildDefaultFilters = (): TradeFilters => {
  const endDate = new Date();
  const startDate = new Date(endDate);
  startDate.setFullYear(startDate.getFullYear() - 5);
  return {
    keyword: '',
    startDate: formatLocalDate(startDate),
    endDate: formatLocalDate(endDate),
  };
};

const money = (value?: number) => value == null ? '-' : `¥${Number(value).toFixed(2)}`;
const percent = (value?: number) => value == null ? '-' : `${(Number(value) * 100).toFixed(2)}%`;
const days = (value?: number) => value == null ? '-' : `${Number(value).toFixed(2)} 天`;

const TradeRecords: React.FC = () => {
  const { t } = useTranslation();
  const defaultFilters = useMemo(buildDefaultFilters, []);
  const [draftFilters, setDraftFilters] = useState<TradeFilters>(defaultFilters);
  const [filters, setFilters] = useState<TradeFilters>(defaultFilters);
  const [records, setRecords] = useState<TradeRecord[]>([]);
  const [page, setPage] = useState<PageState>({ current: 1, pageSize: 20, total: 0 });
  const [refreshToken, setRefreshToken] = useState(0);
  const [loading, setLoading] = useState(false);
  const [, setError] = useState<string | null>(null);

  const loadRecords = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await request.get('/tradeRecords/trades', {
        params: {
          keyword: filters.keyword || undefined,
          startDate: filters.startDate,
          endDate: filters.endDate,
          current: page.current,
          pageSize: page.pageSize,
        },
      }) as ApiResponse<BrokerPage<TradeRecord>>;
      if (!response.success) {
        throw new Error(response.message || '交易记录读取失败');
      }
      setRecords(response.data?.items ?? []);
      setPage((current) => ({ ...current, total: response.data?.total ?? 0 }));
    } catch (cause) {
      setRecords([]);
      setPage((current) => ({ ...current, total: 0 }));
      setError(cause instanceof Error ? cause.message : '交易记录读取失败');
    } finally {
      setLoading(false);
    }
  }, [filters, page.current, page.pageSize, refreshToken]);

  useEffect(() => {
    void loadRecords();
  }, [loadRecords]);

  const applyFilters = () => {
    if (draftFilters.startDate && draftFilters.endDate && draftFilters.startDate > draftFilters.endDate) {
      setError('开始日期不能晚于结束日期');
      return;
    }
    setPage((current) => ({ ...current, current: 1 }));
    setFilters({ ...draftFilters, keyword: draftFilters.keyword.trim() });
  };

  const changePage = (current: number, pageSize: number) => {
    setPage((previous) => ({
      ...previous,
      current: previous.pageSize === pageSize ? current : 1,
      pageSize,
    }));
  };

  const profit = (value?: number) => value == null
    ? '-'
    : <Text type={value >= 0 ? 'success' : 'danger'}>{money(value)}</Text>;

  const rate = (value?: number) => value == null
    ? '-'
    : <Text type={value >= 0 ? 'success' : 'danger'}>{percent(value)}</Text>;

  const stacked = (items: React.ReactNode[]) => (
    <div className="trade-record-split-cell">
      {items.map((item, index) => (
        <span key={index} className="trade-record-split-item">{item}</span>
      ))}
    </div>
  );

  const completedValue = (item: TradeRecord, render: () => React.ReactNode) => item.soldQuantity > 0 ? render() : '-';

  const tradeRange = (item: TradeRecord) => stacked([
    formatDateTime(item.buyTime),
    formatDateTime(item.lastSellTime),
  ]);

  const tradeQuantity = (item: TradeRecord) => stacked([
    item.buyQuantity,
    item.soldQuantity,
  ]);

  const tradePrices = (item: TradeRecord) => stacked([
    money(item.buyPrice),
    money(item.averageSellPrice),
  ]);

  const tradeAmounts = (item: TradeRecord) => stacked([
    money(item.buyAmount),
    money(item.sellAmount),
  ]);

  const tradeProfit = (item: TradeRecord) => completedValue(item, () => stacked([
    profit(item.grossProfit),
    profit(item.netProfit),
  ]));

  const tradeRates = (item: TradeRecord) => completedValue(item, () => stacked([
    rate(item.grossReturnRate),
    rate(item.returnRate),
  ]));

  const columnTitle = (label: string, description: string) => (
    <Tooltip title={description} placement="top">
      <span className="trade-record-column-title">{label}</span>
    </Tooltip>
  );
  const columns = [
    { title: '股票', key: 'stock', fixed: 'left' as const, render: (_: unknown, item: TradeRecord) => `${item.stockCode} ${item.stockName ?? ''}` },
    { title: columnTitle('交易区间', '第一行：买入时间；第二行：卖出时间'), key: 'tradeRange', width: 230, render: (_: unknown, item: TradeRecord) => tradeRange(item) },
    { title: columnTitle('交易数量', '第一行：买入数量；第二行：成交数量'), key: 'tradeQuantity', width: 130, render: (_: unknown, item: TradeRecord) => tradeQuantity(item) },
    { title: columnTitle('成交均价', '第一行：买入均价；第二行：卖出均价'), key: 'tradePrices', width: 140, render: (_: unknown, item: TradeRecord) => tradePrices(item) },
    { title: columnTitle('交易金额', '第一行：买入金额；第二行：卖出金额'), key: 'tradeAmounts', width: 150, render: (_: unknown, item: TradeRecord) => tradeAmounts(item) },
    { title: '手续费', dataIndex: 'totalFee', key: 'totalFee', render: (value: number, item: TradeRecord) => item.soldQuantity > 0 ? money(value) : '-' },
    { title: columnTitle('收益', '第一行：毛收益；第二行：净收益'), key: 'tradeProfit', width: 140, render: (_: unknown, item: TradeRecord) => tradeProfit(item) },
    { title: '持仓时间', dataIndex: 'holdingDays', key: 'holdingDays', render: days },
    { title: columnTitle('收益率', '第一行：毛收益率；第二行：净收益率'), key: 'tradeRates', width: 140, render: (_: unknown, item: TradeRecord) => tradeRates(item) },
    { title: '日收益率', dataIndex: 'dailyReturnRate', key: 'dailyReturnRate', render: (value: number, item: TradeRecord) => item.soldQuantity > 0 ? rate(value) : '-' },
  ];

  usePageHeader(t('tradeRecords.subtitle'));

  return (
    <div className="app-page">
      <Card className="surface-card min-w-0">
        <Space wrap className="mb-4">
          <Input
            prefix={<SearchOutlined />}
            placeholder={t('tradeRecords.searchPlaceholder')}
            value={draftFilters.keyword}
            onChange={(event) => setDraftFilters((current) => ({ ...current, keyword: event.target.value }))}
            onPressEnter={applyFilters}
            allowClear
          />
          <Input type="date" value={draftFilters.startDate} onChange={(event) => setDraftFilters((current) => ({ ...current, startDate: event.target.value }))} />
          <Input type="date" value={draftFilters.endDate} onChange={(event) => setDraftFilters((current) => ({ ...current, endDate: event.target.value }))} />
          <Button type="primary" icon={<SearchOutlined />} onClick={applyFilters}>{t('common.search')}</Button>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => setRefreshToken((value) => value + 1)}>{t('common.refresh')}</Button>
        </Space>
        <Table
          rowKey={(item, index) => `${item.stockCode}-${item.buyTime}-${index ?? 0}`}
          columns={columns}
          dataSource={records}
          loading={loading}
          locale={{ emptyText: <Empty description={t('tradeRecords.empty')} /> }}
          className="trade-records-table"
          tableLayout="fixed"
          scroll={{ x: 1350, y: 'calc(100vh - 390px)' }}
          pagination={false}
        />
        <div className="mt-4 overflow-x-auto pb-1">
          <div className="flex min-w-max justify-end">
            <Pagination
              current={page.current}
              pageSize={page.pageSize}
              total={page.total}
              showSizeChanger
              showQuickJumper
              responsive
              pageSizeOptions={[10, 20, 50, 100]}
              showTotal={(total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`}
              onChange={changePage}
            />
          </div>
        </div>
      </Card>
    </div>
  );
};

export default TradeRecords;
// AI_GENERATE_END ----------------
