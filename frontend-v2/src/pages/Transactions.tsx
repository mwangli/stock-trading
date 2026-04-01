import React, { useState, useEffect, useCallback } from 'react';
import { Card, Table, Tag, Input, Select, Space, Typography, Tabs, Button, message } from 'antd';
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

interface TradeRecord {
  id: number;
  tradeId: string;
  stockCode: string;
  stockName: string;
  buyOrderNo: string;
  sellOrderNos: string;
  tradeDate: string;
  buyTime: string;
  lastSellTime: string;
  holdingSeconds: number;
  buyAmount: number;
  sellAmount: number;
  profitAmount: number;
  buyFee: number;
  sellFee: number;
  otherFee: number;
  totalFee: number;
  netProfitAmount: number;
  totalReturnRate: number;
  dailyReturnRate: number;
  status: string;
  remark: string;
  createTime: string;
  updateTime: string;
}

interface PageResult<T> {
  list: T[];
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
  const [loadingTrades, setLoadingTrades] = useState(false);
  const [orders, setOrders] = useState<HistoryOrder[]>([]);
  const [trades, setTrades] = useState<TradeRecord[]>([]);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [tradePagination, setTradePagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [searchText, setSearchText] = useState('');
  const [directionFilter, setDirectionFilter] = useState<string | undefined>(undefined);
  const [tradeSearchText, setTradeSearchText] = useState('');
  const [tradeStatusFilter, setTradeStatusFilter] = useState<string | undefined>(undefined);

  const fetchOrders = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, any> = {
        keyword: searchText || null,
        direction: directionFilter || null,
        startDate: null,
        endDate: null,
        page: pagination.current - 1,
        size: pagination.pageSize
      };

      const response = await axios.post<ApiResponse<PageResult<HistoryOrder>>>('/api/historyOrders/page', params);
      const result = response.data;

      if (result.success && result.data) {
        setOrders(result.data.list || []);
        setPagination(prev => ({
          ...prev,
          total: result.data.total || 0
        }));
      } else {
        message.error(result.message || '获取历史订单数据失败');
      }
    } catch (error: any) {
      console.error('Failed to fetch history orders:', error);
      if (error.response?.status === 401) {
        message.error('未授权，请先登录');
      } else if (error.code === 'ERR_NETWORK') {
        message.error('网络连接失败，请检查后端服务是否启动');
      } else {
        message.error('获取历史订单数据失败');
      }
    } finally {
      setLoading(false);
    }
  }, [pagination.current, pagination.pageSize, searchText, directionFilter]);

  const fetchTrades = useCallback(async () => {
    setLoadingTrades(true);
    try {
      const params: Record<string, any> = {
        stockCode: tradeSearchText || undefined,
        status: tradeStatusFilter || undefined,
        current: tradePagination.current,
        pageSize: tradePagination.pageSize
      };

      const response = await axios.get<ApiResponse<PageResult<TradeRecord>>>('/api/tradeRecords/page', { params });
      const result = response.data;

      if (result.success && result.data) {
        setTrades(result.data.list || []);
        setTradePagination(prev => ({
          ...prev,
          total: result.data.total || 0
        }));
      } else {
        message.error(result.message || '获取交易记录数据失败');
      }
    } catch (error: any) {
      console.error('Failed to fetch trade records:', error);
      if (error.response?.status === 401) {
        message.error('未授权，请先登录');
      } else if (error.code === 'ERR_NETWORK') {
        message.error('网络连接失败，请检查后端服务是否启动');
      } else {
        message.error('获取交易记录数据失败');
      }
    } finally {
      setLoadingTrades(false);
    }
  }, [tradePagination.current, tradePagination.pageSize, tradeSearchText, tradeStatusFilter]);

  useEffect(() => {
    fetchOrders();
  }, [fetchOrders]);

  useEffect(() => {
    fetchTrades();
  }, [fetchTrades]);

  const handleTableChange = (pag: any) => {
    setPagination(prev => ({
      ...prev,
      current: pag.current,
      pageSize: pag.pageSize
    }));
  };

  const handleTradeTableChange = (pag: any) => {
    setTradePagination(prev => ({
      ...prev,
      current: pag.current,
      pageSize: pag.pageSize
    }));
  };

  const handleSearch = () => {
    setPagination(prev => ({ ...prev, current: 1 }));
    fetchOrders();
  };

  const handleTradeSearch = () => {
    setTradePagination(prev => ({ ...prev, current: 1 }));
    fetchTrades();
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

  const formatOrderDate = (orderDate: string, orderTime: string) => {
    if (!orderDate) return '-';
    const date = orderDate.substring(0, 4) + '-' + orderDate.substring(4, 6) + '-' + orderDate.substring(6, 8);
    const time = orderTime ? orderTime.substring(0, 2) + ':' + orderTime.substring(2, 4) + ':' + orderTime.substring(4, 6) : '';
    return date + ' ' + time;
  };

  const columns = [
    {
      title: '委托日期',
      dataIndex: 'orderDate',
      key: 'orderDate',
      width: 120,
      render: (text: string, record: HistoryOrder) => (
        <span className="text-gray-300">{formatOrderDate(text, record.orderTime)}</span>
      )
    },
    {
      title: '股票代码',
      dataIndex: 'stockCode',
      key: 'stockCode',
      width: 100,
      render: (text: string) => <span className="text-[#00e396] font-bold">{text}</span>
    },
    {
      title: '股票名称',
      dataIndex: 'stockName',
      key: 'stockName',
      width: 120,
      render: (text: string) => <span className="text-white">{text}</span>
    },
    {
      title: '方向',
      dataIndex: 'direction',
      key: 'direction',
      width: 80,
      render: (direction: string) => (
        <Tag color={direction === 'B' ? 'success' : 'error'}>
          {direction === 'B' ? '买入' : '卖出'}
        </Tag>
      )
    },
    {
      title: '价格',
      dataIndex: 'price',
      key: 'price',
      width: 100,
      render: (price: number) => <span className="text-white font-mono">¥{price?.toFixed(4) || '0.0000'}</span>
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 80,
      render: (qty: number) => <span className="text-white">{qty}</span>
    },
    {
      title: '成交金额',
      dataIndex: 'amount',
      key: 'amount',
      width: 120,
      render: (amount: number) => <span className="text-[#ffbd2e] font-mono">¥{amount?.toFixed(2) || '0.00'}</span>
    },
    {
      title: '委托编号',
      dataIndex: 'orderNo',
      key: 'orderNo',
      width: 120,
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
      title: '股东账号',
      dataIndex: 'stockAccount',
      key: 'stockAccount',
      width: 120,
      render: (text: string) => <span className="text-gray-400 text-xs">{text}</span>
    },
    {
      title: '同步时间',
      dataIndex: 'lastSyncTime',
      key: 'lastSyncTime',
      width: 160,
      render: (text: string) => <span className="text-gray-500 text-xs">{formatDateTime(text)}</span>
    }
  ];

  const tradeColumns = [
    {
      title: '交易日期',
      dataIndex: 'tradeDate',
      key: 'tradeDate',
      width: 100,
      render: (text: string) => <span className="text-gray-300">{text}</span>
    },
    {
      title: '股票代码',
      dataIndex: 'stockCode',
      key: 'stockCode',
      width: 100,
      render: (text: string) => <span className="text-[#00e396] font-bold">{text}</span>
    },
    {
      title: '股票名称',
      dataIndex: 'stockName',
      key: 'stockName',
      width: 120,
      render: (text: string) => <span className="text-white">{text}</span>
    },
    {
      title: '买入时间',
      dataIndex: 'buyTime',
      key: 'buyTime',
      width: 160,
      render: (text: string) => <span className="text-gray-300 text-xs">{formatDateTime(text)}</span>
    },
    {
      title: '最后卖出时间',
      dataIndex: 'lastSellTime',
      key: 'lastSellTime',
      width: 160,
      render: (text: string) => <span className="text-gray-300 text-xs">{formatDateTime(text)}</span>
    },
    {
      title: '持仓时间',
      dataIndex: 'holdingSeconds',
      key: 'holdingSeconds',
      width: 100,
      render: (seconds: number) => {
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);
        const days = Math.floor(hours / 24);
        if (days > 0) {
          return <span className="text-white">{days}天{hours % 24}小时</span>;
        } else if (hours > 0) {
          return <span className="text-white">{hours}小时{minutes % 60}分钟</span>;
        } else {
          return <span className="text-white">{minutes}分钟</span>;
        }
      }
    },
    {
      title: '买入金额',
      dataIndex: 'buyAmount',
      key: 'buyAmount',
      width: 120,
      render: (amount: number) => <span className="text-white font-mono">¥{amount?.toFixed(2) || '0.00'}</span>
    },
    {
      title: '卖出金额',
      dataIndex: 'sellAmount',
      key: 'sellAmount',
      width: 120,
      render: (amount: number) => <span className="text-white font-mono">¥{amount?.toFixed(2) || '0.00'}</span>
    },
    {
      title: '净收益',
      dataIndex: 'netProfitAmount',
      key: 'netProfitAmount',
      width: 120,
      render: (amount: number) => (
        <span className={`font-mono ${amount >= 0 ? 'text-[#00e396]' : 'text-[#ff4560]'}`}>
          {amount >= 0 ? '+' : ''}¥{amount?.toFixed(2) || '0.00'}
        </span>
      )
    },
    {
      title: '总收益率',
      dataIndex: 'totalReturnRate',
      key: 'totalReturnRate',
      width: 100,
      render: (rate: number) => (
        <span className={`font-mono ${rate >= 0 ? 'text-[#00e396]' : 'text-[#ff4560]'}`}>
          {rate >= 0 ? '+' : ''}{rate?.toFixed(2) || '0.00'}%
        </span>
      )
    },
    {
      title: '日收益率',
      dataIndex: 'dailyReturnRate',
      key: 'dailyReturnRate',
      width: 100,
      render: (rate: number) => (
        <span className={`font-mono ${rate >= 0 ? 'text-[#00e396]' : 'text-[#ff4560]'}`}>
          {rate >= 0 ? '+' : ''}{rate?.toFixed(2) || '0.00'}%
        </span>
      )
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => {
        let color = 'default';
        let text = status;
        switch (status) {
          case 'COMPLETED':
            color = 'success';
            text = '已完成';
            break;
          case 'PARTIAL':
            color = 'warning';
            text = '部分完成';
            break;
          case 'PENDING':
            color = 'info';
            text = '待完成';
            break;
        }
        return <Tag color={color}>{text}</Tag>;
      }
    },
    {
      title: '交易编号',
      dataIndex: 'tradeId',
      key: 'tradeId',
      width: 120,
      render: (text: string) => <span className="text-gray-400 text-xs">{text}</span>
    }
  ];

  const items = [
    {
      key: 'orders',
      label: t('transactions.tabs.orders'),
      children: (
        <div className="space-y-4">
          <div className="flex flex-nowrap items-center gap-2 mb-4">
            <Input
              placeholder="搜索股票代码或名称"
              prefix={<SearchOutlined className="text-gray-500" />}
              className="bg-black/20 border-gray-700 text-white w-40"
              value={searchText}
              onChange={e => setSearchText(e.target.value)}
              onPressEnter={handleSearch}
              allowClear
            />
            <Select
              placeholder="方向"
              className="w-28"
              allowClear
              value={directionFilter}
              onChange={(value) => {
                setDirectionFilter(value);
                setPagination(prev => ({ ...prev, current: 1 }));
              }}
              dropdownClassName="glass-dropdown"
            >
              <Option value="B">买入</Option>
              <Option value="S">卖出</Option>
            </Select>
            <Button type="primary" onClick={handleSearch} className="px-6">
              查询
            </Button>
          </div>
          <Table
            columns={columns}
            dataSource={orders}
            loading={loading}
            rowKey="id"
            pagination={{
              ...pagination,
              showSizeChanger: true,
              showQuickJumper: true,
              showTotal: (total: number) => `共 ${total} 条`
            }}
            onChange={handleTableChange}
            scroll={{ x: 1200 }}
            rowClassName={() => 'bg-transparent text-gray-300 hover:bg-white/5'}
          />
        </div>
      )
    },
    {
      key: 'trades',
      label: t('transactions.tabs.trades'),
      children: (
        <div className="space-y-4">
          <div className="flex flex-nowrap items-center gap-2 mb-4">
            <Input
              placeholder="搜索股票代码"
              prefix={<SearchOutlined className="text-gray-500" />}
              className="bg-black/20 border-gray-700 text-white w-40"
              value={tradeSearchText}
              onChange={e => setTradeSearchText(e.target.value)}
              onPressEnter={handleTradeSearch}
              allowClear
            />
            <Select
              placeholder="交易状态"
              className="w-28"
              allowClear
              value={tradeStatusFilter}
              onChange={(value) => {
                setTradeStatusFilter(value);
                setTradePagination(prev => ({ ...prev, current: 1 }));
              }}
              dropdownClassName="glass-dropdown"
            >
              <Option value="COMPLETED">已完成</Option>
              <Option value="PARTIAL">部分完成</Option>
              <Option value="PENDING">待完成</Option>
            </Select>
            <Button type="primary" onClick={handleTradeSearch} className="px-6">
              查询
            </Button>
          </div>
          <Table
            columns={tradeColumns}
            dataSource={trades}
            loading={loadingTrades}
            rowKey="id"
            pagination={{
              ...tradePagination,
              showSizeChanger: true,
              showQuickJumper: true,
              showTotal: (total: number) => `共 ${total} 条`
            }}
            onChange={handleTradeTableChange}
            scroll={{ x: 1400 }}
            rowClassName={() => 'bg-transparent text-gray-300 hover:bg-white/5'}
            locale={{
              emptyText: (
                <div className="text-center py-8">
                  <Text className="text-gray-400">暂无交易记录</Text>
                </div>
              )
            }}
          />
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
