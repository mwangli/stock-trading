// AI_GENERATE_START -
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Empty, Input, Select, Space, Table, Tabs, Tag, Typography } from 'antd';
import { HistoryOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import request from '../utils/request';

const { Title, Text } = Typography;

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

interface BrokerOrder {
  orderId: string;
  stockCode: string;
  stockName: string;
  direction: string;
  orderPrice: number;
  orderQuantity: number;
  filledQuantity: number;
  averageFillPrice: number;
  status: string;
  orderTime: string;
}

interface BrokerFill {
  fillId?: string;
  orderId: string;
  stockCode: string;
  stockName: string;
  direction: string;
  fillPrice: number;
  fillQuantity: number;
  fillAmount: number;
  fillTime: string;
}

interface BrokerList<T> {
  items: T[];
}

const Transactions: React.FC = () => {
  const [orders, setOrders] = useState<BrokerOrder[]>([]);
  const [fills, setFills] = useState<BrokerFill[]>([]);
  const [searchText, setSearchText] = useState('');
  const [direction, setDirection] = useState('ALL');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadTransactions = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [orderResponse, fillResponse] = await Promise.all([
        request.get('/broker-query/today-orders') as Promise<ApiResponse<BrokerList<BrokerOrder>>>,
        request.get('/broker-query/today-fills') as Promise<ApiResponse<BrokerList<BrokerFill>>>,
      ]);
      if (!orderResponse.success || !fillResponse.success) {
        throw new Error(orderResponse.message || fillResponse.message || '真实交易数据读取失败');
      }
      setOrders(orderResponse.data?.items ?? []);
      setFills(fillResponse.data?.items ?? []);
    } catch (cause) {
      setOrders([]);
      setFills([]);
      setError(cause instanceof Error ? cause.message : '真实交易数据读取失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadTransactions();
  }, [loadTransactions]);

  const filteredOrders = useMemo(() => orders.filter((item) => {
    const keywordMatched = `${item.stockCode}${item.stockName}`.toLowerCase().includes(searchText.toLowerCase());
    return keywordMatched && (direction === 'ALL' || item.direction === direction);
  }), [orders, searchText, direction]);

  const directionTag = (value: string) => <Tag color={value === 'BUY' ? 'red' : 'green'}>{value}</Tag>;
  const money = (value: number) => `¥${Number(value ?? 0).toFixed(2)}`;

  const orderColumns = [
    { title: '委托时间', dataIndex: 'orderTime', key: 'orderTime' },
    { title: '股票', key: 'stock', render: (_: unknown, item: BrokerOrder) => `${item.stockCode} ${item.stockName ?? ''}` },
    { title: '方向', dataIndex: 'direction', key: 'direction', render: directionTag },
    { title: '委托价', dataIndex: 'orderPrice', key: 'orderPrice', render: money },
    { title: '委托数量', dataIndex: 'orderQuantity', key: 'orderQuantity' },
    { title: '成交数量', dataIndex: 'filledQuantity', key: 'filledQuantity' },
    { title: '成交均价', dataIndex: 'averageFillPrice', key: 'averageFillPrice', render: money },
    { title: '状态', dataIndex: 'status', key: 'status' },
  ];

  const fillColumns = [
    { title: '成交时间', dataIndex: 'fillTime', key: 'fillTime' },
    { title: '股票', key: 'stock', render: (_: unknown, item: BrokerFill) => `${item.stockCode} ${item.stockName ?? ''}` },
    { title: '方向', dataIndex: 'direction', key: 'direction', render: directionTag },
    { title: '成交价', dataIndex: 'fillPrice', key: 'fillPrice', render: money },
    { title: '成交数量', dataIndex: 'fillQuantity', key: 'fillQuantity' },
    { title: '成交金额', dataIndex: 'fillAmount', key: 'fillAmount', render: money },
    { title: '委托编号', dataIndex: 'orderId', key: 'orderId' },
  ];

  const empty = <Empty description="暂无真实交易数据" />;
  const items = [
    { key: 'orders', label: '当日委托', children: <Table rowKey="orderId" columns={orderColumns} dataSource={filteredOrders} loading={loading} locale={{ emptyText: empty }} /> },
    { key: 'fills', label: '当日成交', children: <Table rowKey={(item) => item.fillId || `${item.orderId}-${item.fillTime}`} columns={fillColumns} dataSource={fills} loading={loading} locale={{ emptyText: empty }} /> },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Title level={2} className="!mb-1 !text-white"><HistoryOutlined className="mr-2" />真实交易记录</Title>
          <Text className="text-gray-400">仅展示中信证券返回的委托和成交</Text>
        </div>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadTransactions()}>刷新</Button>
      </div>
      {error && <Alert type="error" showIcon message={error} />}
      <Card>
        <Space className="mb-4">
          <Input prefix={<SearchOutlined />} placeholder="股票代码或名称" value={searchText} onChange={(event) => setSearchText(event.target.value)} allowClear />
          <Select value={direction} onChange={setDirection} options={[{ value: 'ALL', label: '全部方向' }, { value: 'BUY', label: '买入' }, { value: 'SELL', label: '卖出' }]} />
        </Space>
        <Tabs items={items} />
      </Card>
    </div>
  );
};

export default Transactions;
// AI_GENERATE_END -
