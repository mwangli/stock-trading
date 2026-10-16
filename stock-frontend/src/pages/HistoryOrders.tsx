// AI_GENERATE_START -----------
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Empty, Input, Pagination, Select, Space, Table, Tag } from 'antd';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import request from '../utils/request';
import type { ApiResponse } from '../utils/api';
import { formatDateTime } from '../utils/dateTime';
import { usePageHeader } from '../layouts/PageHeaderContext';
import { useTranslation } from 'react-i18next';

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
  const { t } = useTranslation();
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
        throw new Error(response.message || t('historyOrders.loadError'));
      }
      setOrders(response.data?.items ?? []);
      setPage((current) => ({ ...current, total: response.data?.total ?? 0 }));
    } catch (cause) {
      setOrders([]);
      setPage((current) => ({ ...current, total: 0 }));
      setError(cause instanceof Error ? cause.message : t('historyOrders.loadError'));
    } finally {
      setLoading(false);
    }
  }, [filters, page.current, page.pageSize, refreshToken]);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  const applyFilters = () => {
    if (draftFilters.startDate && draftFilters.endDate && draftFilters.startDate > draftFilters.endDate) {
      setError(t('historyOrders.dateError'));
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
    const label = value === 'BUY' ? t('historyOrders.buy') : value === 'SELL' ? t('historyOrders.sell') : value;
    return <Tag color={color}>{label}</Tag>;
  };

  const columns = [
    { title: t('historyOrders.columns.time'), dataIndex: 'orderTime', key: 'orderTime', render: formatDateTime },
    { title: t('historyOrders.columns.stock'), key: 'stock', render: (_: unknown, item: BrokerOrder) => `${item.stockCode} ${item.stockName ?? ''}` },
    { title: t('historyOrders.columns.direction'), dataIndex: 'direction', key: 'direction', render: directionTag },
    { title: t('historyOrders.columns.orderPrice'), dataIndex: 'orderPrice', key: 'orderPrice', render: money },
    { title: t('historyOrders.columns.orderQuantity'), dataIndex: 'orderQuantity', key: 'orderQuantity' },
    { title: t('historyOrders.columns.filledQuantity'), dataIndex: 'filledQuantity', key: 'filledQuantity' },
    { title: t('historyOrders.columns.averageFillPrice'), dataIndex: 'averageFillPrice', key: 'averageFillPrice', render: money },
    { title: t('historyOrders.columns.status'), dataIndex: 'status', key: 'status', render: () => t('historyOrders.filled') },
    { title: t('historyOrders.columns.orderId'), dataIndex: 'orderId', key: 'orderId' },
  ];

  usePageHeader(t('historyOrders.subtitle'));

  return (
    <div className="app-page">
      <Card className="surface-card min-w-0">
        <Space wrap className="mb-4">
          <Input
            prefix={<SearchOutlined />}
            placeholder={t('historyOrders.searchPlaceholder')}
            value={draftFilters.keyword}
            onChange={(event) => setDraftFilters((current) => ({ ...current, keyword: event.target.value }))}
            onPressEnter={applyFilters}
            allowClear
          />
          <Select
            value={draftFilters.direction}
            onChange={(value) => setDraftFilters((current) => ({ ...current, direction: value }))}
            options={[
              { value: 'ALL', label: t('historyOrders.allDirections') },
              { value: 'BUY', label: t('historyOrders.buy') },
              { value: 'SELL', label: t('historyOrders.sell') },
            ]}
          />
          <Input type="date" value={draftFilters.startDate} onChange={(event) => setDraftFilters((current) => ({ ...current, startDate: event.target.value }))} />
          <Input type="date" value={draftFilters.endDate} onChange={(event) => setDraftFilters((current) => ({ ...current, endDate: event.target.value }))} />
          <Button type="primary" icon={<SearchOutlined />} onClick={applyFilters}>{t('common.search')}</Button>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => setRefreshToken((value) => value + 1)}>{t('common.refresh')}</Button>
        </Space>
        <Table
          rowKey={(item) => `${item.orderId}-${item.orderTime}`}
          columns={columns}
          dataSource={orders}
          loading={loading}
          locale={{ emptyText: <Empty description={t('historyOrders.empty')} /> }}
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
              showTotal={(total, range) => t('common.pagination', { start: range[0], end: range[1], total })}
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
