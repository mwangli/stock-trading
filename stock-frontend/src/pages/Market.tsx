// AI_GENERATE_START --
import React, { useState, useEffect, useRef } from 'react';
import ReactECharts from 'echarts-for-react';
import { Table, Input, Button, Select, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { SorterResult } from 'antd/es/table/interface';
import type { EChartsOption, TooltipComponentFormatterCallbackParams } from 'echarts';
import type { InputRef } from 'antd';
import { SearchOutlined, ThunderboltOutlined, RiseOutlined, StarOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import request from '../utils/request';
import { useAppTheme } from '../theme/AppThemeProvider';

const { Text, Title } = Typography;
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

/** 与后端 StockInfoController#getMarketStats / MarketStatsDto 一致 */
interface MarketStats {
  marketStatus?: string;
  changePercent?: number;
  upCount?: number;
  downCount?: number;
  flatCount?: number;
  totalAmount?: number;
  totalVolume?: number;
  topGainerCode?: string | null;
  topGainerName?: string | null;
  topGainerChange?: number | null;
  topLoserCode?: string | null;
  topLoserName?: string | null;
  topLoserChange?: number | null;
  totalCount?: number;
  avgTurnoverRate?: number;
}

interface WatchlistItem {
  key: string;
  symbol: string;
  name: string;
  price: number;
  change: number;
  volume: string;
  volumeValue: number; // 用于排序的原始数值
  starred: boolean;
}

interface KlineData {
  dates: string[];
  kline: number[][];
  volumes: number[];
}

interface MarketStatCard {
  label: string;
  value: React.ReactNode;
  color: string;
  icon: React.ReactNode;
  isTopGainer?: boolean;
  gainerProgress?: number;
}

const Market: React.FC = () => {
  const { t } = useTranslation();
  const { mode } = useAppTheme();
  const isDark = mode === 'dark';
  const chartTextColor = isDark ? '#a9b6ca' : '#526078';
  const chartBorderColor = isDark ? '#253047' : '#dce3ed';
  const chartTooltipBackground = isDark ? 'rgba(13, 21, 36, 0.96)' : 'rgba(255, 255, 255, 0.98)';

  // 市场统计数据状态
  const [marketStats, setMarketStats] = useState<MarketStats | null>(null);

  // 领涨股数据状态
  const [topGainers, setTopGainers] = useState<{code: string; name: string; changePercent: number}[]>([]);
  const [currentGainerIndex, setCurrentGainerIndex] = useState(0);
  const [gainerProgress, setGainerProgress] = useState(0);
  const [gainerDirection, setGainerDirection] = useState<'up' | 'down'>('up');

  // K线数据状态
  const [klineData, setKlineData] = useState<{ dates: string[]; kline: number[][]; volumes: number[] }>({
    dates: [],
    kline: [],
    volumes: []
  });
  const [selectedStock, setSelectedStock] = useState<{ code: string; name: string; price?: number; change?: number } | null>(null);
  const [klineType, setKlineType] = useState<'daily' | 'weekly' | 'monthly'>('daily');
  const [timeRange, setTimeRange] = useState<'last1Week' | 'last1Month' | 'last1Year' | 'last3Years'>('last1Month');
  const [searchKeyword, setSearchKeyword] = useState('');
  const searchInputRef = useRef<InputRef>(null);

  // 时间范围选项（保留最近三年，去掉最近三月避免与「最近3年」混淆）
  const timeRangeOptions = [
    { value: 'last1Week', label: '最近一周' },
    { value: 'last1Month', label: '最近一月' },
    { value: 'last1Year', label: '最近一年' },
    { value: 'last3Years', label: '最近三年' },
  ];

  // 表格排序配置
  const [tableSorter, setTableSorter] = useState<{
    field: 'price' | 'change' | 'volumeValue';
    order: 'ascend' | 'descend' | null;
  }>({
    field: 'change',
    order: 'descend',
  });

  // 处理时间范围变化
  const handleTimeRangeChange = (value: 'last1Week' | 'last1Month' | 'last1Year' | 'last3Years') => {
    setTimeRange(value);
    if (selectedStock) {
      fetchKlineData(selectedStock.code, selectedStock.name, klineType, value);
    }
  };

  // 动态生成 K 线图表配置，并与全局明暗主题保持一致
  const chartOption: EChartsOption = {
    backgroundColor: 'transparent',
    textStyle: { color: chartTextColor },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: chartTooltipBackground,
      borderColor: chartBorderColor,
      textStyle: { color: isDark ? '#eef4ff' : '#172033' },
      formatter: (params: TooltipComponentFormatterCallbackParams) => {
        const entries = Array.isArray(params) ? params : [params];
        const data = entries[0];
        const values = Array.isArray(data?.data) ? data.data : [];
        const [open, close, lowest, highest] = values.map((value) => typeof value === 'number' ? value : undefined);
        const formatPrice = (value: number | undefined) => value?.toFixed(2) ?? '--';
        const labelColor = isDark ? '#8a96a8' : '#526078';
        const valueColor = isDark ? '#eef4ff' : '#172033';

        return `
          <div style="font-size: 12px; line-height: 1.8;">
            <div style="font-weight: 600; margin-bottom: 4px; color: ${valueColor};">${String(data?.name ?? '')}</div>
            <div><span style="color: ${labelColor};">${t('market.kline.open')}:</span> <span style="color: ${valueColor};">¥${formatPrice(open)}</span></div>
            <div><span style="color: ${labelColor};">${t('market.kline.close')}:</span> <span style="color: ${valueColor};">¥${formatPrice(close)}</span></div>
            <div><span style="color: ${labelColor};">${t('market.kline.lowest')}:</span> <span style="color: #ef4444;">¥${formatPrice(lowest)}</span></div>
            <div><span style="color: ${labelColor};">${t('market.kline.highest')}:</span> <span style="color: #16a34a;">¥${formatPrice(highest)}</span></div>
          </div>
        `;
      }
    },
    grid: { left: '3%', right: '3%', bottom: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      data: klineData.dates,
      boundaryGap: false,
      axisLabel: { color: chartTextColor },
      axisLine: { lineStyle: { color: chartBorderColor } },
      splitLine: { show: false }
    },
    yAxis: {
      scale: true,
      axisLabel: { color: chartTextColor },
      splitLine: { lineStyle: { color: chartBorderColor, opacity: 0.55 } },
      axisLine: { lineStyle: { color: chartBorderColor } }
    },
    dataZoom: [{ type: 'inside', start: 50, end: 100 }],
    series: [
      {
        type: 'candlestick',
        data: klineData.kline,
        itemStyle: {
          color: '#16a34a',
          color0: '#ef4444',
          borderColor: '#16a34a',
          borderColor0: '#ef4444'
        }
      }
    ]
  };

  // Watchlist Data
  const [watchlist, setWatchlist] = useState<WatchlistItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 9,
    total: 0,
  });

  const formatVolume = (val: number) => {
    if (!val) return '0';
    if (val >= 1e9) return (val / 1e9).toFixed(1) + 'B';
    if (val >= 1e6) return (val / 1e6).toFixed(1) + 'M';
    if (val >= 1e3) return (val / 1e3).toFixed(1) + 'K';
    return val.toString();
  };

  const fetchStocks = async (page: number = 1, size: number = 9, keyword: string = '') => {
    setLoading(true);
    try {
      console.log('[fetchStocks] page:', page, 'keyword:', keyword);

      // 使用 keywords 参数进行模糊搜索 (name 或 code)
      const params: Record<string, unknown> = { current: page, pageSize: size };
      if (keyword && keyword.trim()) {
        params.keywords = keyword.trim();
        console.log('[fetchStocks] search params:', params);
      }

      const res = await request.get('/stockInfo/list', {
        params
      }) as unknown as ApiResponse<StockInfo[]> & { total?: number };

      console.log('[fetchStocks] response:', res);

      // 正确的逻辑：HTTP 200 且 success: true 时使用接口数据
      if (res && res.success === true && Array.isArray(res.data)) {
        // 过滤掉关键字段为空的记录
        const validData = res.data.filter(item => item.code && item.name);
        const mappedData: WatchlistItem[] = validData.map((item, index) => ({
          key: item.code || String(index),
          symbol: item.code,
          name: item.name,
          price: item.price ?? 0,
          change: item.changePercent ?? 0,
          volume: formatVolume(item.totalMarketValue),
          volumeValue: item.totalMarketValue ?? 0,
          starred: false,
        }));
        setWatchlist(mappedData);
        // 更新分页信息
        setPagination(prev => ({
          ...prev,
          current: page,
          pageSize: size,
          total: res.total ?? 0,
        }));
        return;
      }
      setWatchlist([]);
      setPagination(prev => ({ ...prev, current: page, pageSize: size, total: 0 }));
    } catch (error) {
      console.error('获取股票列表失败:', error);
      setWatchlist([]);
      setPagination(prev => ({ ...prev, current: page, pageSize: size, total: 0 }));
    } finally {
      setLoading(false);
    }
  };

  // 获取K线数据
  const fetchKlineData = async (code: string, name: string, type: 'daily' | 'weekly' | 'monthly' = 'daily', range?: 'last1Week' | 'last1Month' | 'last1Year' | 'last3Years') => {
    const timeRangeParam = range || timeRange;
    try {
      const res = await request.get(`/stock-data/kline/${code}`, {
        params: { type, timeRange: timeRangeParam }
      }) as { success: boolean; data: KlineData; total: number };
      
      if (res.success && res.data) {
        // 获取最新价格和涨跌幅
        const latestData = res.data.kline[res.data.kline.length - 1];
        const previousData = res.data.kline.length > 1 ? res.data.kline[res.data.kline.length - 2] : null;
        
        const currentPrice = latestData ? latestData[1] : 0; // 收盘价
        const previousPrice = previousData ? previousData[1] : currentPrice;
        const change = previousPrice ? ((currentPrice - previousPrice) / previousPrice * 100) : 0;
        
        setSelectedStock({ code, name, price: currentPrice, change });
        setKlineData({
          dates: res.data.dates || [],
          kline: res.data.kline || [],
          volumes: res.data.volumes || []
        });
      } else {
        console.warn('获取K线数据失败，使用默认数据');
        setSelectedStock({ code, name, price: 0, change: 0 });
        setKlineData({ dates: [], kline: [], volumes: [] });
      }
    } catch (error) {
      console.error('获取K线数据失败:', error);
      setSelectedStock({ code, name, price: 0, change: 0 });
      setKlineData({ dates: [], kline: [], volumes: [] });
    } finally {
    }
  };

  useEffect(() => {
    fetchStocks(1, 9, searchKeyword);
    fetchMarketStats();
    fetchTopGainers();
  }, []);

  // 市场统计接口：与后端 /api/stockInfo/marketStats 一致（勿写成 /Info/marketStats）
  const fetchMarketStats = async () => {
    try {
      const res = await request.get('/stockInfo/marketStats') as ApiResponse<MarketStats>;
      if (res && res.success === true && res.data != null) {
        setMarketStats(res.data);
      }
    } catch (error) {
      console.error('获取市场统计信息失败:', error);
    }
  };

  // 获取涨幅榜TOP10
  const fetchTopGainers = async () => {
    try {
      const res = await request.get('/stockInfo/listIncreaseRate') as { success: boolean; data: {code: string; name: string; changePercent: number}[] };
      if (res && res.success && res.data) {
        setTopGainers(res.data);
      }
    } catch (error) {
      console.error('获取涨幅榜失败:', error);
    }
  };

  // 领涨股自动滚动
  useEffect(() => {
    if (topGainers.length <= 1) return;

    // 进度条动画：3秒内从0到100
    const progressInterval = setInterval(() => {
      setGainerProgress(prev => {
        if (prev >= 100) {
          // 进度满时切换到下一个，交替方向
          setGainerDirection(dir => dir === 'up' ? 'down' : 'up');
          setCurrentGainerIndex(prevIdx => (prevIdx + 1) % topGainers.length);
          return 0;
        }
        return prev + 100 / 30; // 30次 * 100ms = 3000ms
      });
    }, 100);

    return () => clearInterval(progressInterval);
  }, [topGainers.length]);

  // 默认加载第一个股票的K线数据
  useEffect(() => {
    if (watchlist.length > 0 && !selectedStock) {
      fetchKlineData(watchlist[0].symbol, watchlist[0].name, klineType, timeRange);
    }
  }, [watchlist, klineType, timeRange]);

  // K线类型切换
  const handleKlineTypeChange = (type: 'daily' | 'weekly' | 'monthly') => {
    setKlineType(type);
    if (selectedStock) {
      fetchKlineData(selectedStock.code, selectedStock.name, type, timeRange);
    }
  };

  const columns: ColumnsType<WatchlistItem> = [
    {
      title: t('market.columns.symbol'),
      dataIndex: 'symbol',
      key: 'symbol',
      render: (text: string, record: WatchlistItem) => (
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
      sorter: (a: WatchlistItem, b: WatchlistItem) => a.price - b.price,
      sortOrder: tableSorter.field === 'price' ? tableSorter.order : null,
      render: (price: number) => <span className="font-mono text-white text-sm">¥{price.toFixed(2)}</span>
    },
    {
      title: t('market.columns.change'),
      dataIndex: 'change',
      key: 'change',
      align: 'right',
      sorter: (a: WatchlistItem, b: WatchlistItem) => a.change - b.change,
      sortOrder: tableSorter.field === 'change' ? tableSorter.order : null,
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
      sorter: (a: WatchlistItem, b: WatchlistItem) => a.volumeValue - b.volumeValue,
      sortOrder: tableSorter.field === 'volumeValue' ? tableSorter.order : null,
      className: 'text-gray-400 font-mono text-xs',
      render: (val: string) => <span className="text-gray-400">{val}</span>
    },
    {
      title: t('market.columns.action'),
      key: 'action',
      align: 'center',
      render: () => (
        <Button size="small" type="primary">
          {t('market.watchlist.trade')}
        </Button>
      )
    }
  ];

  // 格式化金额显示
  const formatAmount = (amount: number) => {
    if (!amount) return '0';
    if (amount >= 1e12) return (amount / 1e12).toFixed(2) + '万亿';
    if (amount >= 1e8) return (amount / 1e8).toFixed(2) + '亿';
    if (amount >= 1e4) return (amount / 1e4).toFixed(2) + '万';
    return amount.toFixed(2);
  };

  // 获取市场统计数据用于展示
  const getStatsData = (): MarketStatCard[] => {
    const stats = marketStats;
    return [
      {
        label: t('market.stats.marketStatus'),
        value: stats?.marketStatus || '--',
        color: stats?.marketStatus === '开盘' ? '#00e396' : '#ffbd2e',
        icon: <ThunderboltOutlined />
      },
      {
        label: t('market.stats.upDown'),
        value: (
          <span>
            <span className="text-[#00e396]">{stats?.upCount || 0}</span>
            <span className="text-gray-500">↑ / </span>
            <span className="text-[#ff4560]">{stats?.downCount || 0}</span>
            <span className="text-gray-500">↓</span>
          </span>
        ),
        color: stats?.changePercent && stats.changePercent >= 0 ? '#00e396' : '#ff4560',
        icon: <RiseOutlined />
      },
      {
        label: t('market.stats.totalVolume'),
        value: stats ? formatAmount(stats.totalAmount || 0) : '--',
        color: 'var(--text-primary)',
        icon: <StarOutlined />
      },
      {
        label: t('market.stats.topGainer'),
        value: topGainers.length > 0 ? (
          <div
            key={`${currentGainerIndex}-${gainerDirection}`}
            className={gainerDirection === 'up' ? 'animate-slide-up' : 'animate-slide-down'}
          >
            <div className="flex items-center gap-2">
              <span>{topGainers[currentGainerIndex]?.code}</span>
              <span className="text-xs text-gray-400">{topGainers[currentGainerIndex]?.name}</span>
              <span className={`text-sm font-mono ${
                (topGainers[currentGainerIndex]?.changePercent || 0) >= 0 ? 'text-[#00e396]' : 'text-[#ff4560]'
              }`}>
                {(topGainers[currentGainerIndex]?.changePercent || 0) >= 0 ? '+' : ''}
                {(topGainers[currentGainerIndex]?.changePercent || 0).toFixed(2)}%
              </span>
            </div>
          </div>
        ) : '--',
        color: '#00e396',
        icon: <RiseOutlined />,
        isTopGainer: topGainers.length > 1,
        gainerProgress
      }
    ];
  };

  return (
    <div className="app-page">
      <div className="page-heading">
        <div>
          <Title level={2} className="page-title">市场行情</Title>
          <Text className="page-subtitle">查看市场广度、自选列表与股票 K 线走势</Text>
        </div>
      </div>

      {/* Top Bar Stats */}
      <div className="metric-strip">
        {getStatsData().map((stat, idx) => (
          <div key={idx} className="metric-strip-item relative flex items-center justify-between overflow-hidden">
            {/* 领涨卡片的纵向进度条 */}
            {stat.isTopGainer && (
              <div className="absolute left-1 top-3 bottom-3 w-1 bg-white/10 rounded-full overflow-hidden">
                <div
                  className="w-full bg-[#00e396] transition-all duration-100 ease-linear rounded-full"
                  style={{ height: `${stat.gainerProgress}%` }}
                />
              </div>
            )}
            <div className="flex-1 ml-2">
              <div className="metric-label">{stat.label}</div>
              <div className="metric-value font-mono" style={{ color: stat.color }}>{stat.value}</div>
            </div>
            <div className="text-2xl opacity-20" style={{ color: stat.color }}>{stat.icon}</div>
          </div>
        ))}
      </div>

      <div className="flex flex-col lg:flex-row gap-6 flex-1 min-h-0">
        {/* Left: Watchlist */}
        <div className="surface-card flex w-full flex-col overflow-hidden lg:w-1/3">
          <div className="border-b border-[var(--border-color)] p-4">
            <Input
              ref={searchInputRef}
              prefix={<SearchOutlined className="text-gray-500" />}
              placeholder="搜索股票名称或代码"
              className="!rounded"
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              onPressEnter={() => {
                // 使用 ref 获取输入框当前值
                const keyword = searchInputRef.current?.input?.value || '';
                fetchStocks(1, 9, keyword);
                setSearchKeyword(keyword);
              }}
              allowClear
              onClear={() => {
                setSearchKeyword('');
                fetchStocks(1, 9, '');
              }}
              suffix={
                <SearchOutlined
                  className="text-gray-400 cursor-pointer hover:text-white"
                  onClick={() => {
                    // 使用 ref 获取输入框当前值
                    const keyword = searchInputRef.current?.input?.value || '';
                    fetchStocks(1, 9, keyword);
                    setSearchKeyword(keyword);
                  }}
                />
              }
            />
          </div>
          <Table 
            loading={loading}
            dataSource={watchlist} 
            columns={columns}
            pagination={{
              current: pagination.current,
              pageSize: 9,
              total: pagination.total,
              position: ['bottomCenter'],
              className: 'glass-pagination',
              showSizeChanger: false,
              showQuickJumper: false,
              showTotal: (total) => total > 0 ? `共 ${Math.ceil(total / 9)} 页` : '无数据',
              onChange: (page) => {
                fetchStocks(page, 9, searchKeyword);
              }
            }}
            onChange={(_pagination, _filters, sorter: SorterResult<WatchlistItem> | SorterResult<WatchlistItem>[]) => {
              const activeSorter = Array.isArray(sorter) ? sorter[0] : sorter;
              if (activeSorter?.field) {
                const field = activeSorter.field as 'price' | 'change' | 'volumeValue';
                setTableSorter({
                  field,
                  order: activeSorter.order ?? null,
                });
              }
            }}
            rowClassName="hover:bg-white/5 cursor-pointer transition-colors"
            onRow={(record) => ({
              onClick: () => fetchKlineData(record.symbol, record.name, klineType, timeRange)
            })}
          />
        </div>

        {/* Right: Chart */}
        <div className="surface-card flex min-h-[500px] flex-1 flex-col p-5">
          <div className="flex justify-between items-center mb-6">
            <div>
              <h2 className="flex items-center gap-3 text-2xl font-semibold text-[var(--text-primary)]">
                {selectedStock?.code || '--'} <span className="text-lg text-gray-500 font-normal">{selectedStock?.name || '--'}</span>
              </h2>
              <div className="text-4xl font-mono font-bold text-[#00e396] mt-2">
                {selectedStock?.price ? `¥${selectedStock.price.toFixed(2)}` : '¥--'}{' '}
                <span className={`text-lg ml-2 ${(selectedStock?.change || 0) >= 0 ? 'text-[#00e396]' : 'text-[#ff4560]'}`}>
                  {selectedStock?.change !== undefined ? `${selectedStock.change >= 0 ? '+' : ''}${selectedStock.change.toFixed(2)}%` : '--%'}
                </span>
              </div>

            </div>
            <div className="flex gap-2 items-center">
              <Select
                value={timeRange}
                onChange={handleTimeRangeChange}
                options={timeRangeOptions}
                className="w-28"
                placeholder="选择时间范围"
              />
              {[
                { key: 'daily', label: '日K' },
                { key: 'weekly', label: '周K' },
                { key: 'monthly', label: '月K' }
              ].map(item => (
                <button 
                  key={item.key} 
                  onClick={() => handleKlineTypeChange(item.key as 'daily' | 'weekly' | 'monthly')}
                  className={`rounded px-3 py-1.5 text-xs transition-colors ${klineType === item.key ? 'bg-blue-600 text-white' : 'border border-[var(--border-color)] bg-[var(--panel-muted)] text-[var(--text-secondary)] hover:border-blue-500 hover:text-blue-600'}`}
                >
                  {item.label}
                </button>
              ))}
            </div>
          </div>
          
          <div className="flex-1 w-full">
            <ReactECharts 
              option={chartOption} 
              style={{ height: '100%', minHeight: '400px', width: '100%' }} 
              notMerge={true}
            />
          </div>
        </div>
      </div>
    </div>
  );
};

export default Market;
// AI_GENERATE_END --
