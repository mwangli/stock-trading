import React, { useState } from 'react';
import ReactECharts from 'echarts-for-react';
import { Table, Input, Button } from 'antd';
import { SearchOutlined, StarOutlined, StarFilled, ThunderboltOutlined, RiseOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

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
  const [watchlist, setWatchlist] = useState([
    { key: '1', symbol: 'AAPL', name: 'Apple Inc.', price: 172.50, change: 1.25, volume: '45M', starred: true },
    { key: '2', symbol: 'TSLA', name: 'Tesla Inc.', price: 198.30, change: -2.40, volume: '102M', starred: true },
    { key: '3', symbol: 'NVDA', name: 'NVIDIA Corp.', price: 820.15, change: 5.60, volume: '28M', starred: false },
    { key: '4', symbol: 'AMD', name: 'Adv. Micro Dev.', price: 180.20, change: 3.10, volume: '55M', starred: false },
    { key: '5', symbol: 'MSFT', name: 'Microsoft', price: 410.50, change: -0.50, volume: '18M', starred: false },
  ]);

  const toggleStar = (key: string) => {
    setWatchlist(prev => prev.map(item => item.key === key ? { ...item, starred: !item.starred } : item));
  };

  const columns = [
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
        <div>
          <div className="font-bold text-white">{text}</div>
          <div className="text-xs text-gray-500">{record.name}</div>
        </div>
      )
    },
    {
      title: t('market.columns.price'),
      dataIndex: 'price',
      key: 'price',
      render: (price: number) => <span className="font-mono text-white">${price.toFixed(2)}</span>
    },
    {
      title: t('market.columns.change'),
      dataIndex: 'change',
      key: 'change',
      render: (change: number) => (
        <span className={`font-mono font-bold ${change >= 0 ? 'text-[#00e396]' : 'text-[#ff4560]'}`}>
          {change > 0 ? '+' : ''}{change.toFixed(2)}%
        </span>
      )
    },
    {
      title: t('market.columns.volume'),
      dataIndex: 'volume',
      key: 'volume',
      className: 'text-gray-400 font-mono text-xs'
    },
    {
      title: t('market.columns.action'),
      key: 'action',
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
          { label: t('market.stats.volume'), value: '$42.5B', color: '#fff', icon: <StarOutlined /> },
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
          <div className="flex-1 overflow-auto">
            <Table 
              dataSource={watchlist} 
              columns={columns} 
              pagination={false} 
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
                $172.50 <span className="text-lg text-[#00e396] ml-2">+1.25%</span>
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
