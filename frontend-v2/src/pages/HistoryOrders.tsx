import React, { useState, useEffect } from 'react';
import { Card, Table, Tag, Input, Select, Space, Typography, Button, message } from 'antd';
import { SearchOutlined, ReloadOutlined, HistoryOutlined } from '@ant-design/icons';
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

interface PageData {
  content: HistoryOrder[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

const HistoryOrders: React.FC = () => {
  const [data, setData] = useState<HistoryOrder[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [searchText, setSearchText] = useState('');
  const [directionFilter, setDirectionFilter] = useState<string | undefined>(undefined);

  const fetchData = async () => {
    setLoading(true);
    try {
      const params: Record<string, any> = {
        page: pagination.current - 1,
        size: pagination.pageSize
      };

      if (searchText) {
        params.stockCode = searchText;
      }
      if (directionFilter) {
        params.direction = directionFilter;
      }

      const response = await axios.get('/api/history-orders/page', { params });
      const pageData: PageData = response.data;

      setData(pageData.content || []);
      setPagination(prev => ({
        ...prev,
        total: pageData.totalElements || 0
      }));
    } catch (error) {
      console.error('Failed to fetch history orders:', error);
      message.error('获取历史订单数据失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [pagination.current, pagination.pageSize, directionFilter]);

  const handleSearch = () => {
    setPagination(prev => ({ ...prev, current: 1 }));
    fetchData();
  };

  const handleTableChange = (pag: any) => {
    setPagination(prev => ({
      ...prev,
      current: pag.current,
      pageSize: pag.pageSize
    }));
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

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row justify-between items-center mb-6">
        <div>
          <Title level={2} className="!text-white flex items-center gap-2 mb-1">
            <HistoryOutlined className="text-[#00e396]" />
            历史订单
          </Title>
          <Text className="text-gray-400">查询所有历史委托订单记录</Text>
        </div>
        <Space>
          <Text className="text-gray-400">共 {pagination.total} 条记录</Text>
          <Button
            icon={<ReloadOutlined />}
            onClick={fetchData}
            loading={loading}
          >
            刷新
          </Button>
        </Space>
      </div>

      <Card bordered={false} className="glass p-4">
        <div className="flex flex-wrap gap-4 mb-4">
          <Input
            placeholder="搜索股票代码"
            prefix={<SearchOutlined className="text-gray-500" />}
            className="bg-black/20 border-gray-700 text-white w-48"
            value={searchText}
            onChange={e => setSearchText(e.target.value)}
            onPressEnter={handleSearch}
            allowClear
          />
          <Select
            placeholder="方向筛选"
            className="w-32"
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
          <Button type="primary" onClick={handleSearch}>
            查询
          </Button>
        </div>

        <Table
          columns={columns}
          dataSource={data}
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
      </Card>
    </div>
  );
};

export default HistoryOrders;
