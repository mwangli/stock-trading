// AI_GENERATE_START -----------
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Empty, Input, Pagination, Space, Table, Tag, Typography } from 'antd';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import request from '../utils/request';
import type { ApiResponse } from '../utils/api';
import { formatDateTime } from '../utils/dateTime';
import { usePageHeader } from '../layouts/PageHeaderContext';

const { Text } = Typography;

interface TradeRecord {
  stockCode: string;
  stockName: string;
  status: 'OPEN' | 'PARTIAL' | 'CLOSED';
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
  grossProfit: number;
  netProfit: number;
  grossReturnRate: number;
  returnRate: number;
  holdingDays?: number;
  dailyReturnRate: number;
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
      const response = await request.get('/trade-records/trades', {
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

  const statusTag = (value: TradeRecord['status']) => {
    const config = {
      OPEN: { color: 'default', label: '未卖出' },
      PARTIAL: { color: 'processing', label: '部分卖出' },
      CLOSED: { color: 'success', label: '已完成' },
    }[value];
    return <Tag color={config.color}>{config.label}</Tag>;
  };

  const profit = (value?: number) => value == null
    ? '-'
    : <Text type={value >= 0 ? 'success' : 'danger'}>{money(value)}</Text>;

  const rate = (value?: number) => value == null
    ? '-'
    : <Text type={value >= 0 ? 'success' : 'danger'}>{percent(value)}</Text>;

  const columns = [
    { title: '状态', dataIndex: 'status', key: 'status', fixed: 'left' as const, render: statusTag },
    { title: '股票', key: 'stock', fixed: 'left' as const, render: (_: unknown, item: TradeRecord) => `${item.stockCode} ${item.stockName ?? ''}` },
    { title: '买入时间', dataIndex: 'buyTime', key: 'buyTime', render: formatDateTime },
    { title: '首次卖出时间', dataIndex: 'firstSellTime', key: 'firstSellTime', render: formatDateTime },
    { title: '最后卖出时间', dataIndex: 'lastSellTime', key: 'lastSellTime', render: formatDateTime },
    { title: '买入数量', dataIndex: 'buyQuantity', key: 'buyQuantity' },
    { title: '已卖数量', dataIndex: 'soldQuantity', key: 'soldQuantity' },
    { title: '剩余数量', dataIndex: 'remainingQuantity', key: 'remainingQuantity' },
    { title: '买入均价', dataIndex: 'buyPrice', key: 'buyPrice', render: money },
    { title: '卖出均价', dataIndex: 'averageSellPrice', key: 'averageSellPrice', render: money },
    { title: '买入金额', dataIndex: 'buyAmount', key: 'buyAmount', render: money },
    { title: '卖出金额', dataIndex: 'sellAmount', key: 'sellAmount', render: money },
    { title: '毛收益', dataIndex: 'grossProfit', key: 'grossProfit', render: (value: number, item: TradeRecord) => item.soldQuantity > 0 ? profit(value) : '-' },
    { title: '净收益', dataIndex: 'netProfit', key: 'netProfit', render: (value: number, item: TradeRecord) => item.soldQuantity > 0 ? profit(value) : '-' },
    { title: '毛收益率', dataIndex: 'grossReturnRate', key: 'grossReturnRate', render: (value: number, item: TradeRecord) => item.soldQuantity > 0 ? rate(value) : '-' },
    { title: '收益率', dataIndex: 'returnRate', key: 'returnRate', render: (value: number, item: TradeRecord) => item.soldQuantity > 0 ? rate(value) : '-' },
    { title: '持仓时间', dataIndex: 'holdingDays', key: 'holdingDays', render: days },
    { title: '日收益率', dataIndex: 'dailyReturnRate', key: 'dailyReturnRate', render: (value: number, item: TradeRecord) => item.soldQuantity > 0 ? rate(value) : '-' },
  ];

  const headerActions = useMemo(() => (
    <Button icon={<ReloadOutlined />} loading={loading} onClick={() => setRefreshToken((value) => value + 1)}>刷新</Button>
  ), [loading]);

  usePageHeader('按 FIFO 规则将已成交历史订单汇总为买入批次交易记录', headerActions);

  return (
    <div className="app-page">
      <Card className="surface-card min-w-0">
        <Space wrap className="mb-4">
          <Input
            prefix={<SearchOutlined />}
            placeholder="股票代码、名称或委托编号"
            value={draftFilters.keyword}
            onChange={(event) => setDraftFilters((current) => ({ ...current, keyword: event.target.value }))}
            onPressEnter={applyFilters}
            allowClear
          />
          <Input type="date" value={draftFilters.startDate} onChange={(event) => setDraftFilters((current) => ({ ...current, startDate: event.target.value }))} />
          <Input type="date" value={draftFilters.endDate} onChange={(event) => setDraftFilters((current) => ({ ...current, endDate: event.target.value }))} />
          <Button type="primary" icon={<SearchOutlined />} onClick={applyFilters}>查询</Button>
        </Space>
        <Table
          rowKey={(item, index) => `${item.stockCode}-${item.buyTime}-${index ?? 0}`}
          columns={columns}
          dataSource={records}
          loading={loading}
          locale={{ emptyText: <Empty description="暂无已组装交易记录" /> }}
          scroll={{ x: 2400, y: 'calc(100vh - 390px)' }}
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
// AI_GENERATE_END -----------
