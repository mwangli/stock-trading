import React, { useState, useEffect } from 'react';
import ReactECharts from 'echarts-for-react';
import { Table, Input, Button } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { SearchOutlined, StarOutlined, StarFilled, ThunderboltOutlined, RiseOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import request from '../utils/request';
interface StockInfo {
  code: string;
  name: string;
  price: number;
  changePercent: number;
  totalMarketValue: number;
}

interface ApiResponse<T> {
  success: boolean;
  data: T;
  total?: number;
}

interface WatchlistItem {
  key: string;
  symbol: string;
  name: string;
  price: number;
  change: number;
  volume: string;
  starred: boolean;
}


const MOCK_WATCHLIST: WatchlistItem[] = [
  { key: '1', symbol: 'AAPL', name: 'Apple Inc.', price: 173.50, change: 1.25, volume: '42.5M', starred: true },
  { key: '2', symbol: 'MSFT', name: 'Microsoft Corp.', price: 320.80, change: 0.85, volume: '28.1M', starred: true },
  { key: '3', symbol: 'GOOGL', name: 'Alphabet Inc.', price: 140.20, change: -0.50, volume: '18.3M', starred: false },
  { key: '4', symbol: 'AMZN', name: 'Amazon.com', price: 178.35, change: 2.10, volume: '35.2M', starred: true },
  { key: '5', symbol: 'TSLA', name: 'Tesla Inc.', price: 210.45, change: -3.20, volume: '55.8M', starred: false },
  { key: '6', symbol: 'NVDA', name: 'NVIDIA Corp.', price: 850.10, change: 5.60, volume: '62.1M', starred: true },
  { key: '7', symbol: 'META', name: 'Meta Platforms', price: 485.90, change: 1.80, volume: '22.5M', starred: false },
  { key: '8', symbol: 'AMD', name: 'AMD Inc.', price: 180.25, change: 4.20, volume: '45.6M', starred: true },
  { key: '9', symbol: 'INTC', name: 'Intel Corp.', price: 42.15, change: -1.50, volume: '38.2M', starred: false },
  { key: '10', symbol: 'NFLX', name: 'Netflix Inc.', price: 610.50, change: 0.95, volume: '12.4M', starred: false },
  { key: '11', symbol: 'BABA', name: 'Alibaba Group', price: 72.35, change: -0.45, volume: '15.2M', starred: false },
  { key: '12', symbol: 'TCEHY', name: 'Tencent Holdings', price: 35.60, change: 1.10, volume: '8.5M', starred: true },
  { key: '13', symbol: 'ORCL', name: 'Oracle Corp.', price: 112.40, change: 0.75, volume: '9.3M', starred: false },
  { key: '14', symbol: 'CRM', name: 'Salesforce', price: 295.10, change: 1.30, volume: '6.7M', starred: true },
  { key: '15', symbol: 'ADBE', name: 'Adobe Inc.', price: 480.50, change: -1.20, volume: '4.2M', starred: false },
  { key: '16', symbol: 'CSCO', name: 'Cisco Systems', price: 48.90, change: -0.15, volume: '14.1M', starred: false },
  { key: '17', symbol: 'AVGO', name: 'Broadcom Inc.', price: 1250.60, change: 8.50, volume: '5.6M', starred: true },
  { key: '18', symbol: 'QCOM', name: 'Qualcomm Inc.', price: 155.30, change: 2.40, volume: '10.8M', starred: true },
  { key: '19', symbol: 'TXN', name: 'Texas Inst.', price: 168.40, change: -0.80, volume: '7.4M', starred: false },
  { key: '20', symbol: 'IBM', name: 'IBM Corp.', price: 192.15, change: 0.65, volume: '5.9M', starred: false },
];
const Market: React.FC = () => {
  const { t } = useTranslation();
  // Mock Data Generation
  const generateData = (count: number) => {
    let basePrice = 150;
    let data = [];
    for (let i = 0; i < count; i++) {
      let change = (Math.random() - 0.5) * 5;
      basePrice += change;
      let open = basePrice;
      let close = basePrice + (Math.random() - 0.5) * 3;
      let low = Math.min(open, close) - Math.random();
      let high = Math.max(open, close) + Math.random();
      data.push([open, close, low, high]);
    }
    return data;
  };

  const kLineData = generateData(50);
  const dates = Array.from({ length: 50 }, (_, i) => `2024-03-${i + 1}`);

  const chartOption = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    grid: { left: '3%', right: '3%', bottom: '10%' },
    xAxis: {
      type: 'category',
      data: dates,
      scale: true,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#555' } },
      splitLine: { show: false }
    },
    yAxis: {
      scale: true,
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
      axisLine: { lineStyle: { color: '#555' } }
    },
    dataZoom: [{ type: 'inside', start: 50, end: 100 }],
    series: [
      {
        type: 'candlestick',
        data: kLineData,
        itemStyle: {
          color: '#00e396',        // Up color
          color0: '#ff4560',       // Down color
          borderColor: '#00e396',  // Up border
          borderColor0: '#ff4560'  // Down border
        }
      }
    ]
  };

  // Watchlist Data
  const [watchlist, setWatchlist] = useState<WatchlistItem[]>([]);
  const [loading, setLoading] = useState(false);

  const formatVolume = (val: number) => {
    if (!val) return '0';
    if (val >= 1e9) return (val / 1e9).toFixed(1) + 'B';
    if (val >= 1e6) return (val / 1e6).toFixed(1) + 'M';
    if (val >= 1e3) return (val / 1e3).toFixed(1) + 'K';
    return val.toString();
  };

  useEffect(() => {
    const fetchStocks = async () => {
      setLoading(true);
      try {
        // Interceptor returns response.data, so we cast to expected type
        const res = await request.get('/stockInfo/list') as unknown as ApiResponse<StockInfo[]>;
        
        if (res.success && Array.isArray(res.data) && res.data.length > 0) {
          const mappedData: WatchlistItem[] = res.data.map((item, index) => ({
            key: item.code || String(index),
            symbol: item.code,
            name: item.name,
            price: item.price,
            change: item.changePercent,
            volume: formatVolume(item.totalMarketValue),
            starred: false,
          }));
          setWatchlist(mappedData);
        } else {
            console.warn('API returned empty or invalid data, using mock data.');
            setWatchlist(MOCK_WATCHLIST);
        }
      } catch (error) {
        console.error('Failed to fetch stock list, using mock data:', error);
        setWatchlist(MOCK_WATCHLIST);
      } finally {
        setLoading(false);
      }
    };

    fetchStocks();
  }, []);

  const toggleStar = (key: string) => {
    setWatchlist(prev => prev.map(item => item.key === key ? { ...item, starred: !item.starred } : item));
  };

  const columns: ColumnsType<WatchlistItem> = [
    {
      title: '',
      dataIndex: 'starred',
      key: 'starred',
      width: 50,
      render: (starred: boolean, record: any) => (
        <div onClick={() => toggleStar(record.key)} className="cursor-pointer text-lg hover:text-yellow-400 transition-colors">
          {starred ? <StarFilled className="text-yellow-400" /> : <StarOutlined className="text-gray-600" />}
        </div>
      )
    },
    {
      title: t('market.columns.symbol'),
      dataIndex: 'symbol',
      key: 'symbol',
      render: (text: string, record: any) => (
        <div className="flex flex-col">
          <span className="font-bold text-white text-base tracking-wide">{text}</span>
          <span className="text-xs text-gray-500 truncate max-w-[100px]">{record.name}</span>
        </div>
      )
    },
    {
      title: t('market.columns.price'),
      dataIndex: 'price',
      key: 'price',
      align: 'right',
      render: (price: number) => <span className="font-mono text-white text-sm">¥{price.toFixed(2)}</span>
    },
    {
      title: t('market.columns.change'),
      dataIndex: 'change',
      key: 'change',
      align: 'right',
      render: (change: number) => (
        <span className={`font-mono font-bold text-sm px-2 py-1 rounded ${change >= 0 ? 'bg-[#00e396]/10 text-[#00e396]' : 'bg-[#ff4560]/10 text-[#ff4560]'}`}>
          {change > 0 ? '+' : ''}{change.toFixed(2)}%
        </span>
      )
    },
    {
      title: t('market.columns.volume'),
      dataIndex: 'volume',
      key: 'volume',
      align: 'right',
      className: 'text-gray-400 font-mono text-xs',
      render: (val: string) => <span className="text-gray-400">{val}</span>
    },
    {
      title: t('market.columns.action'),
      key: 'action',
      align: 'center',
      render: () => (
        <Button size="small" type="primary" className="bg-[#00e396]/20 border border-[#00e396] text-[#00e396] hover:bg-[#00e396] hover:text-black">
          {t('market.watchlist.trade')}
        </Button>
      )
    }
  ];

  return (
    <div className="h-full flex flex-col gap-6">
      {/* Top Bar Stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {[
          { label: t('market.stats.marketStatus'), value: t('market.stats.open'), color: '#00e396', icon: <ThunderboltOutlined /> },
          { label: t('market.stats.volatility'), value: '14.2', color: '#ffbd2e', icon: <RiseOutlined /> },
          { label: t('market.stats.volume'), value: '¥42.5B', color: '#fff', icon: <StarOutlined /> },
          { label: t('market.stats.topGainer'), value: 'NVDA +5.6%', color: '#00e396', icon: <RiseOutlined /> }
        ].map((stat, idx) => (
          <div key={idx} className="glass p-4 flex items-center justify-between rounded-xl">
             <div>
               <div className="text-gray-500 text-xs uppercase">{stat.label}</div>
               <div className="text-xl font-bold font-mono mt-1" style={{ color: stat.color }}>{stat.value}</div>
             </div>
             <div className="text-2xl opacity-20" style={{ color: stat.color }}>{stat.icon}</div>
          </div>
        ))}
      </div>

      <div className="flex flex-col lg:flex-row gap-6 flex-1 min-h-0">
        {/* Left: Watchlist */}
        <div className="w-full lg:w-1/3 flex flex-col glass rounded-xl overflow-hidden">
          <div className="p-4 border-b border-white/10">
            <Input 
              prefix={<SearchOutlined className="text-gray-500" />} 
              placeholder={t('market.watchlist.search')}
              className="bg-black/20 border-white/10 text-white rounded-lg"
            />
          </div>
          <div className="flex-1 overflow-hidden">
            <Table 
              loading={loading}
              dataSource={watchlist} 
              columns={columns} 
              pagination={{ pageSize: 8, position: ['bottomCenter'], className: 'glass-pagination' }} 
              rowClassName="hover:bg-white/5 cursor-pointer transition-colors"
              onRow={() => ({
                onClick: () => { /* Select Stock Logic */ }
              })}
            />
          </div>
        </div>

        {/* Right: Chart */}
        <div className="flex-1 glass rounded-xl p-6 flex flex-col min-h-[500px]">
          <div className="flex justify-between items-center mb-6">
            <div>
              <h2 className="text-3xl font-bold text-white flex items-center gap-3">
                AAPL <span className="text-lg text-gray-500 font-normal">Apple Inc.</span>
              </h2>
              <div className="text-4xl font-mono font-bold text-[#00e396] mt-2">
                ¥172.50 <span className="text-lg text-[#00e396] ml-2">+1.25%</span>
              </div>

            </div>
            <div className="flex gap-2">
              {['1m', '5m', '15m', '1H', '4H', '1D'].map(tf => (
                <button key={tf} className="px-3 py-1 rounded bg-white/5 hover:bg-[#00e396] hover:text-black text-xs transition-colors">
                  {tf}
                </button>
              ))}
            </div>
          </div>
          
          <div className="flex-1 w-full">
            <ReactECharts option={chartOption} style={{ height: '100%', minHeight: '400px', width: '100%' }} />
          </div>
        </div>
      </div>
    </div>
  );
};

export default Market;
