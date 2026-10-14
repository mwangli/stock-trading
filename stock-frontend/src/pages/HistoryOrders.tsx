// AI_GENERATE_START -----------
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Empty, Input, Pagination, Select, Space, Table, Tag } from 'antd';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import request from '../utils/request';
import type { ApiResponse } from '../utils/api';
import { formatDateTime } from '../utils/dateTime';
import { usePageHeader } from '../layouts/PageHeaderContext';

interface BrokerOrder {
  orderId: string;
  stockCode: string;
  stockName: string;
  direction: string;
  orderPrice: number;
  orderQuantity: number;
  filledQuantity: number;
  averageFillPrice?: number;
  status: string;
  orderTime: string;
}

interface BrokerPage<T> {
  items: T[];
  total: number;
}

interface OrderFilters {
  keyword: string;
  direction: string;
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

const buildDefaultFilters = (): OrderFilters => {
  const endDate = new Date();
  const startDate = new Date(endDate);
  startDate.setFullYear(startDate.getFullYear() - 5);
  return {
    keyword: '',
    direction: 'ALL',
    startDate: formatLocalDate(startDate),
    endDate: formatLocalDate(endDate),
  };
};

const money = (value?: number) => value == null ? '-' : `¥${Number(value).toFixed(2)}`;

const HistoryOrders: React.FC = () => {
  const defaultFilters = useMemo(buildDefaultFilters, []);
  const [draftFilters, setDraftFilters] = useState<OrderFilters>(defaultFilters);
  const [filters, setFilters] = useState<OrderFilters>(defaultFilters);
  const [orders, setOrders] = useState<BrokerOrder[]>([]);
  const [page, setPage] = useState<PageState>({ current: 1, pageSize: 20, total: 0 });
  const [refreshToken, setRefreshToken] = useState(0);
  const [loading, setLoading] = useState(false);
  const [, setError] = useState<string | null>(null);

  const loadOrders = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await request.get('/trade-records/orders', {
        params: {
          keyword: filters.keyword || undefined,
          direction: filters.direction === 'ALL' ? undefined : filters.direction,
          startDate: filters.startDate,
          endDate: filters.endDate,
          current: page.current,
          pageSize: page.pageSize,
        },
      }) as ApiResponse<BrokerPage<BrokerOrder>>;
      if (!response.success) {
        throw new Error(response.message || '历史订单读取失败');
      }
      setOrders(response.data?.items ?? []);
      setPage((current) => ({ ...current, total: response.data?.total ?? 0 }));
    } catch (cause) {
      setOrders([]);
      setPage((current) => ({ ...current, total: 0 }));
      setError(cause instanceof Error ? cause.message : '历史订单读取失败');
    } finally {
      setLoading(false);
    }
  }, [filters, page.current, page.pageSize, refreshToken]);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

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

  const directionTag = (value: string) => {
    const color = value === 'BUY' ? 'red' : value === 'SELL' ? 'green' : 'default';
    const label = value === 'BUY' ? '买入' : value === 'SELL' ? '卖出' : value;
    return <Tag color={color}>{label}</Tag>;
  };

  const columns = [
    { title: '委托时间', dataIndex: 'orderTime', key: 'orderTime', render: formatDateTime },
    { title: '股票', key: 'stock', render: (_: unknown, item: BrokerOrder) => `${item.stockCode} ${item.stockName ?? ''}` },
    { title: '方向', dataIndex: 'direction', key: 'direction', render: directionTag },
    { title: '委托价', dataIndex: 'orderPrice', key: 'orderPrice', render: money },
    { title: '委托数量', dataIndex: 'orderQuantity', key: 'orderQuantity' },
    { title: '成交数量', dataIndex: 'filledQuantity', key: 'filledQuantity' },
    { title: '成交均价', dataIndex: 'averageFillPrice', key: 'averageFillPrice', render: money },
    { title: '状态', dataIndex: 'status', key: 'status', render: () => '已成交' },
    { title: '委托编号', dataIndex: 'orderId', key: 'orderId' },
  ];

  const headerActions = useMemo(() => (
    <Button icon={<ReloadOutlined />} loading={loading} onClick={() => setRefreshToken((value) => value + 1)}>刷新</Button>
  ), [loading]);

  usePageHeader('仅展示券商返回并已落库的全部成交委托', headerActions);

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
          <Select
            value={draftFilters.direction}
            onChange={(value) => setDraftFilters((current) => ({ ...current, direction: value }))}
            options={[
              { value: 'ALL', label: '全部方向' },
              { value: 'BUY', label: '买入' },
              { value: 'SELL', label: '卖出' },
            ]}
          />
          <Input type="date" value={draftFilters.startDate} onChange={(event) => setDraftFilters((current) => ({ ...current, startDate: event.target.value }))} />
          <Input type="date" value={draftFilters.endDate} onChange={(event) => setDraftFilters((current) => ({ ...current, endDate: event.target.value }))} />
          <Button type="primary" icon={<SearchOutlined />} onClick={applyFilters}>查询</Button>
        </Space>
        <Table
          rowKey={(item) => `${item.orderId}-${item.orderTime}`}
          columns={columns}
          dataSource={orders}
          loading={loading}
          locale={{ emptyText: <Empty description="暂无已同步历史订单" /> }}
          scroll={{ x: 1200, y: 'calc(100vh - 390px)' }}
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

export default HistoryOrders;
// AI_GENERATE_END -----------
