import React, { useState, useEffect, useCallback } from 'react';
import { Card, Table, Tag, Input, Select, Space, Typography, Tabs, Spin, message } from 'antd';
import { SearchOutlined, HistoryOutlined, ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import axios from 'axios';

const { Title, Text } = Typography;
const { Option } = Select;

interface HistoryOrder {
  id: number;
  orderDate: string;
  orderNo: string;
  marketType: string;
  stockAccount: string;
  stockCode: string;
  stockName: string;
  direction: string;
  price: number;
  quantity: number;
  amount: number;
  serialNo: string;
  orderTime: string;
  remark: string;
  fullName: string;
  orderSubmitTime: string;
  lastSyncTime: string;
}

interface PageResult {
  list: HistoryOrder[];
  total: number;
}

interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

const Transactions: React.FC = () => {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [orders, setOrders] = useState<HistoryOrder[]>([]);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [searchText, setSearchText] = useState('');
  const [sideFilter, setSideFilter] = useState<'ALL' | 'B' | 'S'>('ALL');

  const fetchOrders = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, any> = {
        page: pagination.current - 1,
        size: pagination.pageSize
      };

      if (searchText) {
        params.stockCode = searchText;
        params.stockName = searchText;
      }
      if (sideFilter !== 'ALL') {
        params.direction = sideFilter;
      }

      const response = await axios.get<ApiResponse<PageResult>>('/api/historyOrders/page', { params });
      const result = response.data;

      if (result.success && result.data) {
        setOrders(result.data.list || []);
        setPagination(prev => ({
          ...prev,
          total: result.data.total || 0
        }));
      } else {
        message.error(result.message || '获取订单数据失败');
      }
    } catch (error: any) {
      console.error('Failed to fetch orders:', error);
      if (error.response?.status === 401) {
        message.error('未授权，请先登录');
      } else if (error.code === 'ERR_NETWORK') {
        message.error('网络连接失败，请检查后端服务是否启动');
      } else {
        message.error('获取订单数据失败: ' + (error.message || '未知错误'));
      }
    } finally {
      setLoading(false);
    }
  }, [pagination.current, pagination.pageSize, searchText, sideFilter]);

  useEffect(() => {
    fetchOrders();
  }, [fetchOrders]);

  const handleTableChange = (pag: any) => {
    setPagination(prev => ({
      ...prev,
      current: pag.current,
      pageSize: pag.pageSize
    }));
  };

  const handleSearch = () => {
    setPagination(prev => ({ ...prev, current: 1 }));
    fetchOrders();
  };

  const formatDateTime = (dateStr: string | null) => {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  };

  const columns = [
    {
      title: t('transactions.table.time'),
      dataIndex: 'orderSubmitTime',
      key: 'orderSubmitTime',
      width: 160,
      render: (text: string, record: HistoryOrder) => (
        <span className="text-gray-400">{formatDateTime(text || record.orderSubmitTime)}</span>
      )
    },
    {
      title: t('transactions.table.symbol'),
      dataIndex: 'stockCode',
      key: 'stockCode',
      width: 100,
      render: (text: string) => <span className="text-[#00e396] font-bold">{text}</span>
    },
    {
      title: '名称',
      dataIndex: 'stockName',
      key: 'stockName',
      width: 100,
      render: (text: string) => <span className="text-white">{text}</span>
    },
    {
      title: t('transactions.table.side'),
      dataIndex: 'direction',
      key: 'direction',
      width: 80,
      render: (direction: string) => (
        <Tag color={direction === 'B' ? 'success' : 'error'}>
          {direction === 'B' ? t('transactions.filters.buy') : t('transactions.filters.sell')}
        </Tag>
      )
    },
    {
      title: t('transactions.table.price'),
      dataIndex: 'price',
      key: 'price',
      width: 120,
      render: (price: number) => <span className="text-white">¥{price?.toFixed(4) || '0.0000'}</span>
    },
    {
      title: t('transactions.table.quantity'),
      dataIndex: 'quantity',
      key: 'quantity',
      width: 80,
      render: (qty: number) => <span className="text-white">{qty}</span>
    },
    {
      title: t('transactions.table.totalValue'),
      dataIndex: 'amount',
      key: 'amount',
      width: 120,
      render: (val: number) => <span className="text-[#ffbd2e] font-mono">¥{val?.toFixed(2) || '0.00'}</span>
    },
    {
      title: '委托编号',
      dataIndex: 'orderNo',
      key: 'orderNo',
      width: 100,
      render: (text: string) => <span className="text-gray-400 text-xs">{text}</span>
    },
    {
      title: '市场',
      dataIndex: 'marketType',
      key: 'marketType',
      width: 80,
      render: (text: string) => <Tag>{text}</Tag>
    },
    {
      title: '同步时间',
      dataIndex: 'lastSyncTime',
      key: 'lastSyncTime',
      width: 160,
      render: (text: string) => <span className="text-gray-500 text-xs">{formatDateTime(text)}</span>
    }
  ];

  const items = [
    {
      key: 'orders',
      label: t('transactions.tabs.orders'),
      children: (
        <div className="space-y-4">
          <Space className="w-full justify-between flex-wrap">
            <Space>
              <Input
                placeholder="搜索股票代码或名称"
                prefix={<SearchOutlined className="text-gray-500" />}
                className="bg-black/20 border-gray-700 text-white w-64"
                value={searchText}
                onChange={e => setSearchText(e.target.value)}
                onPressEnter={handleSearch}
                allowClear
              />
              <Select
                value={sideFilter}
                className="w-40"
                onChange={(value: 'ALL' | 'B' | 'S') => {
                  setSideFilter(value);
                  setPagination(prev => ({ ...prev, current: 1 }));
                }}
                dropdownClassName="glass-dropdown"
              >
                <Option value="ALL">{t('transactions.filters.all')}</Option>
                <Option value="B">{t('transactions.filters.buy')}</Option>
                <Option value="S">{t('transactions.filters.sell')}</Option>
              </Select>
              <Input.Search
                className="bg-black/20"
                placeholder="搜索"
                prefix={<SearchOutlined />}
                onSearch={handleSearch}
              />
            </Space>
            <Space>
              <Text className="text-gray-400">共 {pagination.total} 条</Text>
              <Tag color="blue">真实数据</Tag>
              <Tag color="green">API</Tag>
            </Space>
          </Space>
          <Spin spinning={loading}>
            <Table
              columns={columns}
              dataSource={orders}
              loading={loading}
              rowKey="id"
              pagination={{
                ...pagination,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total: number) => `共 ${total} 条`,
                position: ['bottomRight']
              }}
              onChange={handleTableChange}
              rowClassName={() => 'bg-transparent text-gray-300 hover:bg-white/5'}
              locale={{ emptyText: loading ? '加载中...' : '暂无数据' }}
            />
          </Spin>
        </div>
      )
    },
    {
      key: 'trades',
      label: t('transactions.tabs.trades'),
      children: (
        <div className="text-center py-8">
          <Text className="text-gray-400">交易记录功能开发中...</Text>
          <br />
          <Text className="text-gray-500 text-sm">可从"历史订单"页面查看完整的委托记录</Text>
        </div>
      )
    }
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row justify-between items-center mb-6">
        <div>
          <Title level={2} className="!text-white flex items-center gap-2 mb-1">
            <HistoryOutlined className="text-[#00e396]" />
            {t('transactions.title')}
          </Title>
          <Text className="text-gray-400">{t('transactions.subtitle')}</Text>
        </div>
        <Space>
          <Text className="text-gray-400">
            {loading ? '加载中...' : `共 ${pagination.total} 条记录`}
          </Text>
          <Tag icon={<ReloadOutlined spin={loading} />} color="processing" onClick={fetchOrders} className="cursor-pointer">
            刷新
          </Tag>
        </Space>
      </div>

      <Card bordered={false} className="glass p-4">
        <Tabs defaultActiveKey="orders" items={items} />
      </Card>
    </div>
  );
};

export default Transactions;
