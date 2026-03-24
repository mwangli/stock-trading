import React, { useState } from 'react';
import { Card, Table, Tag, Input, Select, Space, Typography, Tabs } from 'antd';
import { SearchOutlined, HistoryOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

const { Title, Text } = Typography;
const { Option } = Select;

interface Transaction {
  key: string;
  time: string;
  symbol: string;
  side: 'BUY' | 'SELL';
  price: number;
  quantity: number;
  totalValue: number;
  status: 'FILLED' | 'PENDING' | 'CANCELLED';
}

interface Trade {
  key: string;
  openTime: string;
  closeTime: string;
  symbol: string;
  quantity: number;
  buyPrice: number;
  sellPrice: number;
  totalAmount: number;
  fees: number;
  profit: number;
  returnRate: number;
}

const Transactions: React.FC = () => {
  const { t } = useTranslation();
  const [searchText, setSearchText] = useState('');
  const [sideFilter, setSideFilter] = useState<'ALL' | 'BUY' | 'SELL'>('ALL');

  // Mock Data - Orders
  const data: Transaction[] = [
    { key: '1', time: '2023-10-27 10:30:00', symbol: 'AAPL', side: 'BUY', price: 150.23, quantity: 100, totalValue: 15023.00, status: 'FILLED' },
    { key: '2', time: '2023-10-27 11:15:00', symbol: 'TSLA', side: 'SELL', price: 890.11, quantity: 50, totalValue: 44505.50, status: 'FILLED' },
    { key: '3', time: '2023-10-27 14:20:00', symbol: 'NVDA', side: 'BUY', price: 420.69, quantity: 200, totalValue: 84138.00, status: 'PENDING' },
    { key: '4', time: '2023-10-26 09:45:00', symbol: 'AMD', side: 'SELL', price: 95.00, quantity: 150, totalValue: 14250.00, status: 'CANCELLED' },
    { key: '5', time: '2023-10-26 13:10:00', symbol: 'MSFT', side: 'BUY', price: 330.50, quantity: 80, totalValue: 26440.00, status: 'FILLED' },
    { key: '6', time: '2023-10-25 15:55:00', symbol: 'GOOGL', side: 'SELL', price: 2800.00, quantity: 10, totalValue: 28000.00, status: 'FILLED' },
    { key: '7', time: '2023-10-25 10:00:00', symbol: 'AMZN', side: 'BUY', price: 3400.00, quantity: 5, totalValue: 17000.00, status: 'FILLED' },
    { key: '8', time: '2023-10-24 11:30:00', symbol: 'META', side: 'BUY', price: 320.00, quantity: 60, totalValue: 19200.00, status: 'FILLED' },
    { key: '9', time: '2023-10-24 14:00:00', symbol: 'NFLX', side: 'SELL', price: 650.00, quantity: 20, totalValue: 13000.00, status: 'FILLED' },
    { key: '10', time: '2023-10-23 09:30:00', symbol: 'BABA', side: 'BUY', price: 160.00, quantity: 100, totalValue: 16000.00, status: 'PENDING' },
    { key: '11', time: '2023-10-23 10:45:00', symbol: 'JD', side: 'SELL', price: 70.00, quantity: 200, totalValue: 14000.00, status: 'FILLED' },
    { key: '12', time: '2023-10-22 13:20:00', symbol: 'PDD', side: 'BUY', price: 90.00, quantity: 50, totalValue: 4500.00, status: 'CANCELLED' },
  ];

  // Mock Data - Trades (Aggregated Buy -> Sell)
  const tradesData: Trade[] = [
    { 
      key: '1', 
      openTime: '2023-10-27 10:30:00', 
      closeTime: '2023-10-27 14:45:00', 
      symbol: 'AAPL', 
      quantity: 100, 
      buyPrice: 150.23, 
      sellPrice: 155.50, 
      totalAmount: 15550.00, 
      fees: 15.50, 
      profit: 511.50, 
      returnRate: 3.40 
    },
    { 
      key: '2', 
      openTime: '2023-10-26 09:30:00', 
      closeTime: '2023-10-27 11:15:00', 
      symbol: 'TSLA', 
      quantity: 50, 
      buyPrice: 850.00, 
      sellPrice: 890.11, 
      totalAmount: 44505.50, 
      fees: 44.50, 
      profit: 1961.00, 
      returnRate: 4.61 
    },
    { 
      key: '3', 
      openTime: '2023-10-25 13:00:00', 
      closeTime: '2023-10-25 15:55:00', 
      symbol: 'GOOGL', 
      quantity: 10, 
      buyPrice: 2820.00, 
      sellPrice: 2800.00, 
      totalAmount: 28000.00, 
      fees: 28.00, 
      profit: -228.00, 
      returnRate: -0.81 
    },
    { 
      key: '4', 
      openTime: '2023-10-24 10:15:00', 
      closeTime: '2023-10-24 14:00:00', 
      symbol: 'NFLX', 
      quantity: 20, 
      buyPrice: 630.00, 
      sellPrice: 650.00, 
      totalAmount: 13000.00, 
      fees: 13.00, 
      profit: 387.00, 
      returnRate: 3.07 
    },
    { 
      key: '5', 
      openTime: '2023-10-23 09:45:00', 
      closeTime: '2023-10-23 10:45:00', 
      symbol: 'JD', 
      quantity: 200, 
      buyPrice: 72.00, 
      sellPrice: 70.00, 
      totalAmount: 14000.00, 
      fees: 14.00, 
      profit: -414.00, 
      returnRate: -2.87 
    },
  ];

  const filteredData = data.filter(item => {
    const matchSymbol = item.symbol.toLowerCase().includes(searchText.toLowerCase());
    const matchSide = sideFilter === 'ALL' || item.side === sideFilter;
    return matchSymbol && matchSide;
  });

  const columns = [
    {
      title: t('transactions.table.time'),
      dataIndex: 'time',
      key: 'time',
      render: (text: string) => <span className="text-gray-400">{text}</span>
    },
    {
      title: t('transactions.table.symbol'),
      dataIndex: 'symbol',
      key: 'symbol',
      render: (text: string) => <span className="text-[#00e396] font-bold">{text}</span>
    },
    {
      title: t('transactions.table.side'),
      dataIndex: 'side',
      key: 'side',
      render: (side: 'BUY' | 'SELL') => (
        <Tag color={side === 'BUY' ? 'success' : 'error'}>
          {side === 'BUY' ? t('transactions.filters.buy') : t('transactions.filters.sell')}
        </Tag>
      )
    },
    {
      title: t('transactions.table.price'),
      dataIndex: 'price',
      key: 'price',
      render: (price: number) => <span className="text-white">¥{price.toFixed(2)}</span>
    },
    {
      title: t('transactions.table.quantity'),
      dataIndex: 'quantity',
      key: 'quantity',
      render: (qty: number) => <span className="text-white">{qty}</span>
    },
    {
      title: t('transactions.table.totalValue'),
      dataIndex: 'totalValue',
      key: 'totalValue',
      render: (val: number) => <span className="text-[#ffbd2e] font-mono">¥{val.toFixed(2)}</span>
    },
    {
      title: t('transactions.table.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        let color = 'default';
        if (status === 'FILLED') color = 'success';
        if (status === 'PENDING') color = 'processing';
        if (status === 'CANCELLED') color = 'error';
        return <Tag color={color}>{t(`transactions.status.${status.toLowerCase()}`)}</Tag>;
      }
    }
  ];

  const tradeColumns = [
    {
      title: t('transactions.tradesTable.openTime'),
      dataIndex: 'openTime',
      key: 'openTime',
      render: (text: string) => <span className="text-gray-400">{text}</span>
    },
    {
      title: t('transactions.tradesTable.closeTime'),
      dataIndex: 'closeTime',
      key: 'closeTime',
      render: (text: string) => <span className="text-gray-400">{text}</span>
    },
    {
      title: t('transactions.table.symbol'),
      dataIndex: 'symbol',
      key: 'symbol',
      render: (text: string) => <span className="text-[#00e396] font-bold">{text}</span>
    },
    {
      title: t('transactions.table.quantity'),
      dataIndex: 'quantity',
      key: 'quantity',
      render: (qty: number) => <span className="text-white">{qty}</span>
    },
    {
      title: t('transactions.tradesTable.buyPrice'),
      dataIndex: 'buyPrice',
      key: 'buyPrice',
      render: (price: number) => <span className="text-gray-300">¥{price.toFixed(2)}</span>
    },
    {
      title: t('transactions.tradesTable.sellPrice'),
      dataIndex: 'sellPrice',
      key: 'sellPrice',
      render: (price: number) => <span className="text-gray-300">¥{price.toFixed(2)}</span>
    },
    {
      title: t('transactions.table.totalValue'),
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      render: (val: number) => <span className="text-[#ffbd2e] font-mono">¥{val.toFixed(2)}</span>
    },
    {
      title: t('transactions.tradesTable.fees'),
      dataIndex: 'fees',
      key: 'fees',
      render: (val: number) => <span className="text-gray-400">¥{val.toFixed(2)}</span>
    },
    {
      title: t('transactions.tradesTable.profit'),
      dataIndex: 'profit',
      key: 'profit',
      render: (val: number) => (
        <span className={`font-bold ${val >= 0 ? 'text-[#00e396]' : 'text-[#ff4560]'}`}>
          {val >= 0 ? '+' : ''}{val.toFixed(2)}
        </span>
      )
    },
    {
      title: t('transactions.tradesTable.returnRate'),
      dataIndex: 'returnRate',
      key: 'returnRate',
      render: (val: number) => (
        <Tag color={val >= 0 ? 'success' : 'error'}>
          {val >= 0 ? '+' : ''}{val.toFixed(2)}%
        </Tag>
      )
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
                placeholder={t('transactions.filters.searchPlaceholder')}
                prefix={<SearchOutlined className="text-gray-500" />}
                className="bg-black/20 border-gray-700 text-white w-64"
                onChange={e => setSearchText(e.target.value)}
                allowClear
              />
              <Select
                defaultValue="ALL"
                className="w-40"
                onChange={(value: 'ALL' | 'BUY' | 'SELL') => setSideFilter(value)}
                dropdownClassName="glass-dropdown"
              >
                <Option value="ALL">{t('transactions.filters.all')}</Option>
                <Option value="BUY">{t('transactions.filters.buy')}</Option>
                <Option value="SELL">{t('transactions.filters.sell')}</Option>
              </Select>
            </Space>
          </Space>
          <Table
            columns={columns}
            dataSource={filteredData}
            pagination={{ pageSize: 10, position: ['bottomRight'] }}
            rowClassName={() => 'bg-transparent text-gray-300 hover:bg-white/5'}
          />
        </div>
      )
    },
    {
      key: 'trades',
      label: t('transactions.tabs.trades'),
      children: (
        <Table
          columns={tradeColumns}
          dataSource={tradesData}
          pagination={{ pageSize: 10, position: ['bottomRight'] }}
          rowClassName={() => 'bg-transparent text-gray-300 hover:bg-white/5'}
        />
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
      </div>

      <Card bordered={false} className="glass p-4">
        <Tabs defaultActiveKey="orders" items={items} />
      </Card>
    </div>
  );
};

export default Transactions;
