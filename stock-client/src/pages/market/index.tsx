// AI_GENERATE_START--------------------------------------
import React, { useMemo, useState, useEffect, useRef } from 'react';
import ReactECharts from 'echarts-for-react';
import { Descriptions, Drawer, Empty, Input, Pagination, Select, Skeleton, Table, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { TablePaginationConfig } from 'antd/es/table/interface';
import type { EChartsOption, TooltipComponentFormatterCallbackParams } from 'echarts';
import type { InputRef } from 'antd';
import { ArrowLeftOutlined, InfoCircleOutlined, NotificationOutlined, SearchOutlined, ThunderboltOutlined, RiseOutlined, StarOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import request from '../../utils/request';
import type { ApiResponse } from '../../utils/api';
import { useAppTheme } from '../../theme/AppThemeProvider';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import { localizedText } from '../../utils/localizedText';
import { formatDateTime } from '../../utils/dateTime';

interface StockInfo {
  id?: string;
  code: string;
  name: string;
  market?: string;
  price?: number;
  changeAmount?: number;
  changePercent?: number;
  volume?: number;
  amount?: number;
  totalMarketValue?: number;
  turnoverRate?: number;
  volumeRatio?: number;
  industryCode?: number;
  listedDate?: string;
  updateTime?: string;
}

interface StockNewsSummary {
  id: string;
  title: string;
  summary?: string;
  source?: string;
  category?: string;
  publishTime?: string;
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
  amount: number;
  starred: boolean;
  stockInfo: StockInfo;
}

type MarketSortField = 'updateTime' | 'price' | 'change' | 'amount';
type MarketSortOrder = 'asc' | 'desc';

const DEFAULT_SORT_FIELD: MarketSortField = 'updateTime';
const DEFAULT_SORT_ORDER: MarketSortOrder = 'desc';
const DEFAULT_DETAIL_DRAWER_WIDTH = 480;
const MIN_DETAIL_DRAWER_WIDTH = 360;
const DETAIL_DRAWER_VIEWPORT_GAP = 24;

const getDetailDrawerMaxWidth = () => typeof window === 'undefined'
  ? DEFAULT_DETAIL_DRAWER_WIDTH
  : Math.max(0, window.innerWidth - DETAIL_DRAWER_VIEWPORT_GAP);

const clampDetailDrawerWidth = (width: number, maxWidth: number) => Math.min(
  Math.max(width, Math.min(MIN_DETAIL_DRAWER_WIDTH, maxWidth)),
  maxWidth,
);

interface DailyRankingItem {
  period: string;
  stockCode: string;
  stockName?: string;
  close: number;
  preclose: number;
  returnRate: number;
}

interface KlinePoint {
  value: [number, number, number, number];
}

interface MarketStatCard {
  label: string;
  value: React.ReactNode;
  color: string;
  icon: React.ReactNode;
  isTopGainer?: boolean;
  gainerProgress?: number;
}

const STOCK_UP_COLOR = '#ef4444';
const STOCK_DOWN_COLOR = '#22c55e';

const MARKET_LABELS: Record<string, [string, string]> = {
  SH: ['上海', 'Shanghai'],
  SZ: ['深圳', 'Shenzhen'],
  BJ: ['北京', 'Beijing'],
};

const SECURITY_CATEGORY_LABELS: Record<number, [string, string]> = {
  0: ['深市主板', 'Shenzhen Main Board'],
  2: ['深市主板（原中小板）', 'Shenzhen Main Board'],
  9: ['沪市主板', 'Shanghai Main Board'],
  18: ['创业板', 'ChiNext'],
};

const compactAmountFormatters = {
  zh: new Intl.NumberFormat('zh-CN', {
    notation: 'compact',
    maximumFractionDigits: 2,
  }),
  en: new Intl.NumberFormat('en-US', {
    notation: 'compact',
    maximumFractionDigits: 2,
  }),
};

const formatNumber = (value?: number, digits = 2) => (
  value == null ? '-' : Number(value).toFixed(digits)
);

const formatPercent = (value?: number) => value == null ? '-' : `${Number(value).toFixed(2)}%`;

const formatMarket = (value?: string) => {
  if (!value) return '-';
  const labels = MARKET_LABELS[value.toUpperCase()];
  return labels ? localizedText(labels[0], labels[1]) : value;
};

const formatSecurityCategory = (value?: number) => {
  if (value == null) return '-';
  const labels = SECURITY_CATEGORY_LABELS[value];
  return labels ? localizedText(labels[0], labels[1]) : localizedText(`其他类别（${value}）`, `Other (${value})`);
};

const formatCompactAmount = (value?: number) => value == null
  ? '-'
  : localizedText(
    `¥${compactAmountFormatters.zh.format(Number(value))}`,
    `¥${compactAmountFormatters.en.format(Number(value))}`,
  );

const formatFullAmount = (value?: number) => value == null
  ? '-'
  : `¥${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;

const Market: React.FC = () => {
  const { t } = useTranslation();
  const { mode } = useAppTheme();
  const isDark = mode === 'dark';
  const chartTextColor = isDark ? '#a9b6ca' : '#526078';
  usePageHeader(t('marketPage.subtitle'));
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
  const [detailStock, setDetailStock] = useState<StockInfo | null>(null);
  const [latestNews, setLatestNews] = useState<StockNewsSummary[]>([]);
  const [latestNewsLoading, setLatestNewsLoading] = useState(false);
  const [latestNewsLoadFailed, setLatestNewsLoadFailed] = useState(false);
  const [detailDrawerMaxWidth, setDetailDrawerMaxWidth] = useState(getDetailDrawerMaxWidth);
  const [isCompactViewport, setIsCompactViewport] = useState(() => window.innerWidth <= 767);
  const [mobileWorkspaceView, setMobileWorkspaceView] = useState<'list' | 'chart'>('list');
  const [detailDrawerWidth, setDetailDrawerWidth] = useState(() => clampDetailDrawerWidth(
    DEFAULT_DETAIL_DRAWER_WIDTH,
    getDetailDrawerMaxWidth(),
  ));
  const [klineType] = useState<'daily'>('daily');
  const [timeRange, setTimeRange] = useState<'last1Month' | 'last1Year' | 'last3Years'>('last1Month');
  const [searchKeyword, setSearchKeyword] = useState('');
  const searchInputRef = useRef<InputRef>(null);

  useEffect(() => {
    const syncDetailDrawerWidth = () => {
      const nextMaxWidth = getDetailDrawerMaxWidth();
      setDetailDrawerMaxWidth(nextMaxWidth);
      setIsCompactViewport(window.innerWidth <= 767);
      setDetailDrawerWidth((currentWidth) => clampDetailDrawerWidth(currentWidth, nextMaxWidth));
    };

    window.addEventListener('resize', syncDetailDrawerWidth);
    return () => window.removeEventListener('resize', syncDetailDrawerWidth);
  }, []);

  useEffect(() => {
    if (!detailStock?.code) {
      setLatestNews([]);
      setLatestNewsLoadFailed(false);
      return;
    }

    setLatestNews([]);
    setLatestNewsLoading(false);
    setLatestNewsLoadFailed(false);
  }, [detailStock?.code]);

  // 时间范围选项（默认展示最近一月，移除最近一周）
  const timeRangeOptions = [
    { value: 'last1Month', label: localizedText('最近一月', 'Last Month') },
    { value: 'last1Year', label: localizedText('最近一年', 'Last Year') },
    { value: 'last3Years', label: localizedText('最近三年', 'Last 3 Years') },
  ];

  // 处理时间范围变化
  const handleTimeRangeChange = (value: 'last1Month' | 'last1Year' | 'last3Years') => {
    setTimeRange(value);
    if (selectedStock) {
      fetchKlineData(selectedStock.code, selectedStock.name, klineType, value);
    }
  };

  // 动态生成 K 线图表配置，并与全局明暗主题保持一致
  const chartOption = useMemo<EChartsOption>(() => ({
    animation: false,
    animationDuration: 0,
    animationDurationUpdate: 0,
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
        const values = Array.isArray(data?.value) ? data.value : [];
        const [open, close, lowest, highest] = values.map((value) => {
          const numericValue = typeof value === 'number' ? value : Number(value);
          return Number.isFinite(numericValue) ? numericValue : undefined;
        });
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
      boundaryGap: true,
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
    dataZoom: [{
      type: 'inside',
      start: 0,
      end: 100,
      zoomOnMouseWheel: false,
      moveOnMouseMove: false,
      moveOnMouseWheel: false,
    }],
    series: [
      {
        type: 'candlestick',
        data: klineData.kline.map((values): KlinePoint => ({
          value: [values[0], values[1], values[2], values[3]],
        })),
        itemStyle: {
          color: STOCK_UP_COLOR,
          color0: STOCK_DOWN_COLOR,
          borderColor: STOCK_UP_COLOR,
          borderColor0: STOCK_DOWN_COLOR
        }
      }
    ]
  }), [chartBorderColor, chartTextColor, chartTooltipBackground, isDark, klineData.dates, klineData.kline, t]);

  // Watchlist Data
  const [watchlist, setWatchlist] = useState<WatchlistItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 9,
    total: 0,
  });
  const [sortField, setSortField] = useState<MarketSortField>(DEFAULT_SORT_FIELD);
  const [sortOrder, setSortOrder] = useState<MarketSortOrder>(DEFAULT_SORT_ORDER);

  const fetchStocks = async (
    page: number = 1,
    size: number = 9,
    keyword: string = '',
    requestedSortField: MarketSortField = sortField,
    requestedSortOrder: MarketSortOrder = sortOrder,
  ) => {
    setLoading(true);
    try {
      const instrumentsResponse = await request.get('/market/instruments', {
        params: { keyword: keyword.trim(), limit: 500 },
      }) as ApiResponse<Array<Record<string, unknown>>>;
      if (instrumentsResponse.success && Array.isArray(instrumentsResponse.data)) {
        const mappedData: WatchlistItem[] = instrumentsResponse.data.map((row) => {
          const code = String(row.code ?? '');
          const item: StockInfo = {
            code,
            name: String(row.name ?? code),
            market: row.market == null ? undefined : String(row.market),
            price: row.price == null ? undefined : Number(row.price),
            changeAmount: row.change_amount == null ? undefined : Number(row.change_amount),
            changePercent: row.change_percent == null ? undefined : Number(row.change_percent),
            volume: row.volume == null ? undefined : Number(row.volume),
            amount: row.amount == null ? undefined : Number(row.amount),
            listedDate: row.as_of_date == null ? undefined : String(row.as_of_date),
            updateTime: row.update_time == null ? undefined : String(row.update_time),
          };
          return {
          key: item.code,
          symbol: item.code,
          name: item.name,
          price: item.price ?? 0,
          change: item.changePercent ?? 0,
          amount: item.amount ?? 0,
          starred: false,
          stockInfo: item,
          };
        });
        const direction = requestedSortOrder === 'asc' ? 1 : -1;
        mappedData.sort((left, right) => {
          if (requestedSortField === 'price') return (left.price - right.price) * direction;
          if (requestedSortField === 'change') return (left.change - right.change) * direction;
          if (requestedSortField === 'amount') return (left.amount - right.amount) * direction;
          return left.symbol.localeCompare(right.symbol) * direction;
        });
        const offset = (page - 1) * size;
        setWatchlist(mappedData.slice(offset, offset + size));
        setPagination(prev => ({
          ...prev,
          current: page,
          pageSize: size,
          total: mappedData.length,
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
  const fetchKlineData = async (code: string, name: string, type: 'daily' = 'daily', range?: 'last1Month' | 'last1Year' | 'last3Years') => {
    const timeRangeParam = range || timeRange;
    try {
      const limit = timeRangeParam === 'last3Years' ? 750 : timeRangeParam === 'last1Year' ? 260 : 40;
      const res = await request.get('/market/bars', {
        params: { code, interval: type === 'daily' ? 'day' : 'day', limit },
      }) as ApiResponse<Array<Record<string, unknown>>>;

      if (res.success && Array.isArray(res.data)) {
        const bars = [...res.data].reverse();
        const dates = bars.map((item) => String(item.trade_date ?? item.bar_end ?? ''));
        const points = bars.map((item) => [
          Number(item.open ?? 0),
          Number(item.close ?? 0),
          Number(item.low ?? 0),
          Number(item.high ?? 0),
        ]);
        const latestData = points[points.length - 1];
        const previousData = points.length > 1 ? points[points.length - 2] : null;

        if (!latestData) {
          setSelectedStock({ code, name });
          setKlineData({ dates: [], kline: [], volumes: [] });
          return;
        }
        const currentPrice = latestData[1]; // 收盘价
        const previousPrice = previousData ? previousData[1] : currentPrice;
        const change = previousPrice ? ((currentPrice - previousPrice) / previousPrice * 100) : 0;

        setSelectedStock({ code, name, price: currentPrice, change });
        setKlineData({
          dates,
          kline: points,
          volumes: bars.map((item) => Number(item.volume ?? 0)),
        });
      } else {
        console.warn('获取K线数据失败，使用默认数据');
        setSelectedStock({ code, name });
        setKlineData({ dates: [], kline: [], volumes: [] });
      }
    } catch (error) {
      console.error('获取K线数据失败:', error);
      setSelectedStock({ code, name });
      setKlineData({ dates: [], kline: [], volumes: [] });
    } finally {
    }
  };

  const selectStockForChart = (record: WatchlistItem) => {
    setSelectedStock({
      code: record.symbol,
      name: record.name,
      price: record.price,
      change: record.change,
    });
    if (isCompactViewport) {
      setMobileWorkspaceView('chart');
    }
    void fetchKlineData(record.symbol, record.name, klineType, timeRange);
  };

  useEffect(() => {
    fetchStocks(1, 9, searchKeyword);
    fetchMarketStats();
    fetchTopGainers();
  }, []);

  const fetchMarketStats = async () => {
    try {
      const res = await request.get('/market/statistics') as ApiResponse<Record<string, unknown>>;
      if (res && res.success === true && res.data != null) {
        setMarketStats({
          marketStatus: res.data.available ? localizedText(`数据日期 ${String(res.data.asOfDate ?? '-')}`, `As of ${String(res.data.asOfDate ?? '-')}`) : localizedText('数据不可用', 'Unavailable'),
          upCount: Number(res.data.advances ?? 0),
          downCount: Number(res.data.declines ?? 0),
          flatCount: Number(res.data.unchanged ?? 0),
          totalCount: Number(res.data.total ?? 0),
          totalAmount: Number(res.data.total_amount ?? 0),
          totalVolume: Number(res.data.total_volume ?? 0),
        });
      }
    } catch (error) {
      console.error('获取市场统计信息失败:', error);
    }
  };

  const fetchTopGainers = async () => {
    try {
      const res = await request.get('/market/daily-ranking', { params: { limit: 10 } }) as ApiResponse<DailyRankingItem[]>;
      if (res?.success && Array.isArray(res.data)) {
        setTopGainers(res.data.map((item) => ({
          code: item.stockCode,
          name: item.stockName ?? item.stockCode,
          changePercent: Number(item.returnRate) * 100,
        })));
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

  const columns: ColumnsType<WatchlistItem> = [
    {
      title: t('market.columns.symbol'),
      dataIndex: 'symbol',
      key: 'symbol',
      width: 100,
      render: (text: string, record: WatchlistItem) => (
        <button
          type="button"
          className="group max-w-full text-left"
          aria-label={localizedText(`查看 ${record.name}（${text}）详情`, `View details for ${record.name} (${text})`)}
          onClick={(event) => {
            event.stopPropagation();
            setDetailStock(record.stockInfo);
          }}
        >
          <span className="font-mono text-sm font-semibold text-[var(--brand)] transition-colors group-hover:text-blue-400 group-focus-visible:text-blue-400">{text}</span>
        </button>
      )
    },
    {
      title: t('market.columns.name'),
      dataIndex: 'name',
      key: 'name',
      width: 118,
      ellipsis: true,
      render: (name: string) => <span className="text-sm text-[var(--text-primary)]">{name}</span>,
    },
    {
      title: t('market.columns.price'),
      dataIndex: 'price',
      key: 'price',
      width: 96,
      align: 'right',
      sorter: true,
      sortOrder: sortField === 'price' ? (sortOrder === 'asc' ? 'ascend' : 'descend') : undefined,
      render: (price: number) => <span className="font-mono text-white text-sm">¥{price.toFixed(2)}</span>
    },
    {
      title: t('market.columns.change'),
      dataIndex: 'change',
      key: 'change',
      width: 96,
      align: 'right',
      sorter: true,
      sortOrder: sortField === 'change' ? (sortOrder === 'asc' ? 'ascend' : 'descend') : undefined,
      render: (change: number) => (
        <span className={`font-mono font-bold text-sm px-2 py-1 rounded ${change >= 0 ? 'bg-[#ef4444]/10 text-[#ef4444]' : 'bg-[#22c55e]/10 text-[#22c55e]'}`}>
          {change > 0 ? '+' : ''}{change.toFixed(2)}%
        </span>
      )
    },
    {
      title: t('market.columns.amount'),
      dataIndex: 'amount',
      key: 'amount',
      width: 88,
      align: 'right',
      sorter: true,
      sortOrder: sortField === 'amount' ? (sortOrder === 'asc' ? 'ascend' : 'descend') : undefined,
      className: 'text-gray-400 font-mono text-xs',
      render: (val: number) => <span className="text-gray-400">{formatCompactAmount(val)}</span>
    },

  ];

  // 格式化金额显示
  const formatAmount = (amount: number) => {
    if (!amount) return '0';
    if (amount >= 1e12) return (amount / 1e12).toFixed(2) + localizedText('万亿', 'T');
    if (amount >= 1e8) return (amount / 1e8).toFixed(2) + localizedText('亿', 'B');
    if (amount >= 1e4) return (amount / 1e4).toFixed(2) + localizedText('万', '万');
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
            <span className="text-[#ef4444]">{stats?.upCount || 0}</span>
            <span className="text-gray-500">↑ / </span>
            <span className="text-[#22c55e]">{stats?.downCount || 0}</span>
            <span className="text-gray-500">↓</span>
          </span>
        ),
        color: stats?.changePercent && stats.changePercent >= 0 ? STOCK_UP_COLOR : STOCK_DOWN_COLOR,
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
                (topGainers[currentGainerIndex]?.changePercent || 0) >= 0 ? 'text-[#ef4444]' : 'text-[#22c55e]'
              }`}>
                {(topGainers[currentGainerIndex]?.changePercent || 0) >= 0 ? '+' : ''}
                {(topGainers[currentGainerIndex]?.changePercent || 0).toFixed(2)}%
              </span>
            </div>
          </div>
        ) : '--',
        color: STOCK_UP_COLOR,
        icon: <RiseOutlined />,
        isTopGainer: topGainers.length > 1,
        gainerProgress
      }
    ];
  };

  return (
    <div className="app-page">
      {/* Top Bar Stats */}
      <div className="metric-strip">
        {getStatsData().map((stat, idx) => (
          <div key={idx} className="metric-strip-item relative flex items-center justify-between overflow-hidden">
            {/* 领涨卡片的纵向进度条 */}
            {stat.isTopGainer && (
              <div className="absolute left-1 top-3 bottom-3 w-1 bg-white/10 rounded-full overflow-hidden">
                <div
                  className="w-full bg-[#ef4444] transition-all duration-100 ease-linear rounded-full"
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

      <div className={`market-workspace ${mobileWorkspaceView === 'chart' ? 'is-chart-view' : 'is-list-view'}`}>
        <div className="market-workspace-track">
          {/* Left: Watchlist */}
          <div
            className="surface-card market-workspace-pane market-list-pane flex flex-col overflow-hidden"
            aria-hidden={isCompactViewport && mobileWorkspaceView !== 'list' ? true : undefined}
            inert={isCompactViewport && mobileWorkspaceView !== 'list'}
          >
            <div className="border-b border-[var(--border-color)] p-3">
              <Input
                ref={searchInputRef}
                prefix={<SearchOutlined className="text-gray-500" />}
                placeholder={t('marketPage.searchPlaceholder')}
                className="!rounded"
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                onPressEnter={() => {
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
                      const keyword = searchInputRef.current?.input?.value || '';
                      fetchStocks(1, 9, keyword);
                      setSearchKeyword(keyword);
                    }}
                  />
                }
              />
            </div>
            {isCompactViewport ? (
              <div className="market-mobile-list-shell">
                <div className="market-mobile-list" aria-busy={loading}>
                  {loading ? (
                    Array.from({ length: 5 }, (_, index) => (
                      <div key={index} className="market-mobile-stock-skeleton">
                        <Skeleton active title={false} paragraph={{ rows: 2, width: ['44%', '100%'] }} />
                      </div>
                    ))
                  ) : watchlist.length > 0 ? (
                    watchlist.map((record) => {
                      const isSelected = selectedStock?.code === record.symbol;
                      return (
                        <div key={record.key} className={`market-mobile-stock ${isSelected ? 'is-selected' : ''}`}>
                          <button
                            type="button"
                            className="market-mobile-stock-main"
                            aria-label={localizedText(`查看 ${record.name}（${record.symbol}）K 线`, `View chart for ${record.name} (${record.symbol})`)}
                            aria-current={isSelected ? 'true' : undefined}
                            onClick={() => selectStockForChart(record)}
                          >
                            <span className="market-mobile-stock-identity">
                              <strong>{record.name}</strong>
                              <span>{record.symbol}</span>
                            </span>
                            <span className="market-mobile-stock-quote">
                              <strong>¥{record.price.toFixed(2)}</strong>
                              <span className={record.change >= 0 ? 'is-up' : 'is-down'}>
                                {record.change > 0 ? '+' : ''}{record.change.toFixed(2)}%
                              </span>
                            </span>
                          </button>
                          <button
                            type="button"
                            className="market-mobile-stock-detail"
                            aria-label={localizedText(`查看 ${record.name} 详情`, `View details for ${record.name}`)}
                            onClick={() => setDetailStock(record.stockInfo)}
                          >
                            <InfoCircleOutlined aria-hidden="true" />
                            <span>{localizedText('详情', 'Details')}</span>
                          </button>
                        </div>
                      );
                    })
                  ) : (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={localizedText('暂无行情数据', 'No market data')} />
                  )}
                </div>
                {pagination.total > 0 && (
                  <Pagination
                    className="market-mobile-pagination"
                    current={pagination.current}
                    pageSize={9}
                    total={pagination.total}
                    showSizeChanger={false}
                    showQuickJumper={false}
                    showLessItems
                    onChange={(page) => fetchStocks(page, 9, searchKeyword)}
                  />
                )}
              </div>
            ) : (
              <Table
                className="market-desktop-table"
                size="small"
                loading={loading}
                dataSource={watchlist}
                columns={columns}
                scroll={{ x: 498 }}
                showSorterTooltip={{ title: localizedText('点击切换排序', 'Click to change sort') }}
                pagination={{
                  current: pagination.current,
                  pageSize: 9,
                  total: pagination.total,
                  position: ['bottomCenter'],
                  className: 'glass-pagination',
                  showSizeChanger: false,
                  showQuickJumper: false,
                  showTotal: (total) => total > 0 ? localizedText(`共 ${Math.ceil(total / 9)} 页`, `${Math.ceil(total / 9)} pages`) : localizedText('无数据', 'No data'),
                }}
                onChange={(
                  tablePagination: TablePaginationConfig,
                  _filters,
                  sorter,
                  extra,
                ) => {
                  if (extra.action === 'paginate') {
                    fetchStocks(tablePagination.current ?? 1, 9, searchKeyword);
                    return;
                  }
                  if (extra.action !== 'sort') {
                    return;
                  }
                  const activeSorter = Array.isArray(sorter) ? sorter[0] : sorter;
                  const nextField = activeSorter?.columnKey as MarketSortField | undefined;
                  if (!activeSorter?.order) {
                    setSortField(DEFAULT_SORT_FIELD);
                    setSortOrder(DEFAULT_SORT_ORDER);
                    fetchStocks(1, 9, searchKeyword, DEFAULT_SORT_FIELD, DEFAULT_SORT_ORDER);
                    return;
                  }
                  const nextOrder: MarketSortOrder = activeSorter.order === 'ascend' ? 'asc' : 'desc';
                  if (!nextField || !['price', 'change', 'amount'].includes(nextField)) {
                    return;
                  }
                  setSortField(nextField);
                  setSortOrder(nextOrder);
                  fetchStocks(1, 9, searchKeyword, nextField, nextOrder);
                }}
                rowClassName={(record) => `hover:bg-white/5 cursor-pointer transition-colors ${selectedStock?.code === record.symbol ? 'market-table-row-selected' : ''}`}
                onRow={(record) => ({
                  onClick: () => selectStockForChart(record)
                })}
              />
            )}
          </div>

          {/* Right: Chart */}
          <div
            className="surface-card market-workspace-pane market-chart-pane flex flex-col p-4"
            aria-hidden={isCompactViewport && mobileWorkspaceView !== 'chart' ? true : undefined}
            inert={isCompactViewport && mobileWorkspaceView !== 'chart'}
          >
            <div className="market-chart-header mb-4 flex items-center justify-between">
              {isCompactViewport && (
                <button
                  type="button"
                  className="market-chart-back"
                  aria-label={localizedText('返回股票列表', 'Back to stock list')}
                  onClick={() => setMobileWorkspaceView('list')}
                >
                  <ArrowLeftOutlined aria-hidden="true" />
                  <span>{localizedText('股票列表', 'Stocks')}</span>
                </button>
              )}
              <div className="market-chart-heading">
                <h2 className="flex items-center gap-3 text-xl font-semibold text-[var(--text-primary)] sm:text-2xl">
                  {selectedStock?.code || '--'} <span className="text-lg text-gray-500 font-normal">{selectedStock?.name || '--'}</span>
                </h2>
                <div className="market-chart-price mt-2 font-mono text-3xl font-bold text-[var(--text-primary)] sm:text-4xl">
                  {selectedStock?.price ? `¥${selectedStock.price.toFixed(2)}` : '¥--'}{' '}
                  <span className={`text-lg ml-2 ${(selectedStock?.change || 0) >= 0 ? 'text-[#ef4444]' : 'text-[#22c55e]'}`}>
                    {selectedStock?.change !== undefined ? `${selectedStock.change >= 0 ? '+' : ''}${selectedStock.change.toFixed(2)}%` : '--%'}
                  </span>
                </div>
              </div>
              <div className="market-chart-controls flex items-center gap-2">
                <Select
                  value={timeRange}
                  onChange={handleTimeRangeChange}
                  options={timeRangeOptions}
                  className="w-28"
                  placeholder={localizedText('选择时间范围', 'Select time range')}
                />
                <span className="rounded bg-blue-600 px-3 py-1.5 text-xs text-white">
                  {t('marketPage.daily')}
                </span>
              </div>
            </div>

            <div className="flex-1 w-full">
              <ReactECharts
                option={chartOption}
                style={{ height: '100%', minHeight: isCompactViewport ? '300px' : '400px', width: '100%' }}
                notMerge={true}
                lazyUpdate={false}
              />
            </div>
          </div>
        </div>
      </div>

      <Drawer
        open={Boolean(detailStock)}
        placement="right"
        size={isCompactViewport ? '100%' : detailDrawerWidth}
        maxSize={detailDrawerMaxWidth}
        resizable={isCompactViewport ? false : {
          onResize: (nextWidth) => {
            setDetailDrawerWidth(clampDetailDrawerWidth(nextWidth, detailDrawerMaxWidth));
          },
        }}
        classNames={{ dragger: 'market-detail-drawer-resize-handle' }}
        title={detailStock ? (
          <div className="flex min-w-0 items-baseline gap-3">
            <span className="truncate text-base font-semibold text-[var(--text-primary)]">{detailStock.name}</span>
            <span className="shrink-0 font-mono text-sm font-normal text-[var(--text-secondary)]">{detailStock.code}</span>
          </div>
        ) : t('marketPage.details.title')}
        onClose={() => setDetailStock(null)}
      >
        {detailStock && (
          <div className="flex flex-col gap-5">
            <div className="border-b border-[var(--border-color)] pb-5">
              <div className="text-xs text-[var(--text-secondary)]">{t('marketPage.details.price')}</div>
              <div className="mt-2 flex items-end gap-3">
                <span className="font-mono text-3xl font-semibold text-[var(--text-primary)]">
                  {detailStock.price == null ? '-' : `¥${formatNumber(detailStock.price, 3)}`}
                </span>
                <span className={`pb-1 font-mono text-sm font-semibold ${(detailStock.changePercent ?? 0) >= 0 ? 'text-[#ef4444]' : 'text-[#22c55e]'}`}>
                  {detailStock.changePercent != null && detailStock.changePercent > 0 ? '+' : ''}{formatPercent(detailStock.changePercent)}
                </span>
              </div>
            </div>
            <Descriptions
              bordered
              size="small"
              column={1}
              labelStyle={{ width: isCompactViewport ? 104 : 128 }}
              items={[
                { key: 'market', label: t('marketPage.details.market'), children: formatMarket(detailStock.market) },
                { key: 'category', label: t('marketPage.details.category'), children: formatSecurityCategory(detailStock.industryCode) },
                { key: 'changeAmount', label: t('marketPage.details.changeAmount'), children: formatNumber(detailStock.changeAmount, 3) },
                { key: 'marketCap', label: t('marketPage.details.marketCap'), children: <span title={formatFullAmount(detailStock.totalMarketValue)}>{formatCompactAmount(detailStock.totalMarketValue)}</span> },
                { key: 'turnover', label: t('marketPage.details.turnover'), children: formatPercent(detailStock.turnoverRate) },
                { key: 'volumeRatio', label: t('marketPage.details.volumeRatio'), children: formatNumber(detailStock.volumeRatio) },
                { key: 'listedDate', label: t('marketPage.details.listedDate'), children: detailStock.listedDate?.slice(0, 10) || '-' },
                { key: 'updatedAt', label: t('marketPage.details.updatedAt'), children: formatDateTime(detailStock.updateTime) },
              ]}
            />
            <section className="border-t border-[var(--border-color)] pt-5">
              <div className="mb-3 flex items-center gap-2">
                <NotificationOutlined className="text-[var(--brand)]" />
                <h3 className="text-sm font-semibold text-[var(--text-primary)]">
                  {t('marketPage.details.latestNews')}
                </h3>
              </div>
              {latestNewsLoading ? (
                <div className="space-y-3" aria-label={t('marketPage.details.loadingNews')}>
                  {[0, 1, 2].map((item) => (
                    <Skeleton key={item} active paragraph={{ rows: 2 }} title={{ width: '72%' }} />
                  ))}
                </div>
              ) : latestNewsLoadFailed ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('marketPage.details.newsUnavailable')} />
              ) : latestNews.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('marketPage.details.noNews')} />
              ) : (
                <div className="space-y-3">
                  {latestNews.map((news) => (
                    <Tooltip
                      key={news.id}
                      title={(
                        <div className="max-h-72 overflow-y-auto break-all whitespace-pre-wrap">
                          <div className="font-semibold">{news.title}</div>
                          {news.summary && news.summary !== news.title && (
                            <div className="mt-2 opacity-90">{news.summary}</div>
                          )}
                        </div>
                      )}
                      placement="left"
                      overlayStyle={{ maxWidth: 420 }}
                    >
                      <article
                        tabIndex={0}
                        className="min-h-[112px] border border-[var(--border-color)] bg-[var(--panel-muted)] p-3 outline-none transition-colors hover:border-[var(--brand)] focus-visible:border-[var(--brand)]"
                      >
                        <h4
                          className="overflow-hidden break-all text-sm font-semibold leading-5 text-[var(--text-primary)]"
                          style={{ display: '-webkit-box', WebkitBoxOrient: 'vertical', WebkitLineClamp: 2 }}
                        >
                          {news.title}
                        </h4>
                        <p
                          className="mt-2 overflow-hidden break-all text-xs leading-5 text-[var(--text-secondary)]"
                          style={{ display: '-webkit-box', WebkitBoxOrient: 'vertical', WebkitLineClamp: 2 }}
                        >
                          {news.summary || news.title}
                        </p>
                        <div className="mt-2 flex items-center justify-between gap-3 text-[11px] text-[var(--text-muted)]">
                          <span className="min-w-0 truncate">{news.source || news.category || t('marketPage.details.unknownSource')}</span>
                          <span className="shrink-0 font-mono">{formatDateTime(news.publishTime)}</span>
                        </div>
                      </article>
                    </Tooltip>
                  ))}
                </div>
              )}
            </section>
          </div>
        )}
      </Drawer>
    </div>
  );
};

export default Market;
// AI_GENERATE_END--------------------------------------
