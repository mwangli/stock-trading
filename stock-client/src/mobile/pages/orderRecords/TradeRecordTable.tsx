// AI_GENERATE_START ------------
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import dayjs from 'dayjs';
import { Button, DatePicker, Empty, Input, Pagination, Space, Spin, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { SearchOutlined } from '@ant-design/icons';
import request from '@shared/utils/request';
import type { ApiResponse } from '@shared/utils/api';
import { formatDateTime } from '@shared/utils/dateTime';
import { localizedText } from '@shared/utils/localizedText';
import { useTranslation } from 'react-i18next';
import StockDetailDrawer, { type StockDetailSelection } from '../../components/StockDetailDrawer';

const { Text } = Typography;

interface TradeRecord {
  tradeKey: string;
  stockCode: string;
  stockName: string;
  buyTime: string;
  firstSellTime?: string;
  lastSellTime?: string;
  buyQuantity: number;
  soldQuantity: number;
  remainingQuantity: number;
  buyPrice: number;
  averageSellPrice?: number;
  buyAmount: number;
  sellAmount: number;
  totalFee: number;
  grossProfit: number;
  netProfit: number;
  grossReturnRate: number;
  returnRate: number;
  holdingDays?: number;
  dailyReturnRate: number;
  grossDailyReturnRate: number;
}

interface TradeFilters {
  keyword: string;
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

const DATE_FORMAT = 'YYYY-MM-DD';
const MOBILE_MEDIA_QUERY = '(max-width: 991px)';

const buildDefaultFilters = (): TradeFilters => {
  const endDate = new Date();
  const startDate = new Date(endDate);
  startDate.setFullYear(startDate.getFullYear() - 5);
  return {
    keyword: '',
    startDate: formatLocalDate(startDate),
    endDate: formatLocalDate(endDate),
  };
};

const money = (value?: number) => value == null
  ? '-'
  : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const signedMoney = (value: number) => `${value >= 0 ? '+' : '-'}¥${Math.abs(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const timestamp = (value?: string) => {
  const parsed = value ? Date.parse(value) : Number.NaN;
  return Number.isFinite(parsed) ? parsed : 0;
};
const percent = (value?: number) => value == null ? '-' : `${(Number(value) * 100).toFixed(2)}%`;
const signedPercent = (value?: number) => value == null
  ? '-'
  : `${value >= 0 ? '+' : ''}${percent(value)}`;
const days = (value?: number) => value == null ? '-' : `${Math.trunc(Number(value))} 天`;
const compactDate = (value?: string) => value && dayjs(value).isValid()
  ? dayjs(value).format('YYYY-MM-DD')
  : '-';
const dailyReturn = (returnRate: number, holdingDays?: number) => {
  if (!holdingDays || holdingDays <= 0 || returnRate <= -1) return returnRate;
  return Math.pow(1 + returnRate, 1 / holdingDays) - 1;
};

const TradeRecordTable: React.FC = () => {
  const { t } = useTranslation();
  const defaultFilters = useMemo(buildDefaultFilters, []);
  const [draftFilters, setDraftFilters] = useState<TradeFilters>(defaultFilters);
  const [filters, setFilters] = useState<TradeFilters>(defaultFilters);
  const [records, setRecords] = useState<TradeRecord[]>([]);
  const [page, setPage] = useState<PageState>({ current: 1, pageSize: 10, total: 0 });
  const [cumulativeGrossProfit, setCumulativeGrossProfit] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [isMobile, setIsMobile] = useState(() => (
    typeof window !== 'undefined' && window.matchMedia(MOBILE_MEDIA_QUERY).matches
  ));
  const [, setError] = useState<string | null>(null);
  const [detailStock, setDetailStock] = useState<StockDetailSelection | null>(null);
  const loadMoreTriggerRef = useRef<HTMLDivElement | null>(null);
  const loadingMoreRef = useRef(false);
  const mobileLoadedPageRef = useRef(1);

  const loadRecords = useCallback(async (targetPage: number, append: boolean) => {
    if (append) {
      setLoadingMore(true);
    } else {
      setLoading(true);
    }
    setError(null);
    try {
      const response = await request.get('/trading/trades', {
        params: { limit: 500 },
      }) as ApiResponse<Array<Record<string, unknown>>>;
      if (!response.success) {
        throw new Error(response.message || '交易记录读取失败');
      }
      const keyword = filters.keyword.toLowerCase();
      const mappedRecords: TradeRecord[] = (response.data ?? []).map((item) => {
        const quantity = Number(item.quantity ?? 0);
        const buyTime = String(item.buy_time ?? item.buy_date ?? '');
        const sellTime = String(item.sell_time ?? item.sell_date ?? '');
        const netReturn = Number(item.net_return ?? 0);
        const grossReturn = Number(item.gross_return ?? 0);
        const holdingDays = item.holding_days == null
          ? undefined
          : Math.max(0, Math.trunc(Number(item.holding_days)));
        const soldQuantity = sellTime ? quantity : 0;
        return {
          tradeKey: String(item.trade_key ?? `${item.buy_order_id ?? ''}:${item.sell_order_id ?? ''}`),
          stockCode: String(item.instrument ?? ''),
          stockName: String(item.stock_name ?? ''),
          buyTime,
          firstSellTime: sellTime || undefined,
          lastSellTime: sellTime || undefined,
          buyQuantity: quantity,
          soldQuantity,
          remainingQuantity: quantity - soldQuantity,
          buyPrice: Number(item.buy_price ?? 0),
          averageSellPrice: Number(item.sell_price ?? 0),
          buyAmount: Number(item.cost_basis ?? 0),
          sellAmount: Number(item.sell_amount ?? 0),
          totalFee: Number(item.buy_fee ?? 0) + Number(item.sell_fee ?? 0),
          grossProfit: Number(item.gross_profit ?? 0),
          netProfit: Number(item.net_profit ?? 0),
          grossReturnRate: grossReturn,
          returnRate: netReturn,
          holdingDays,
          dailyReturnRate: dailyReturn(netReturn, holdingDays),
          grossDailyReturnRate: dailyReturn(grossReturn, holdingDays),
        };
      });
      const filteredRecords = mappedRecords.filter((item) => {
        const matchesKeyword = !keyword
          || item.stockCode.toLowerCase().includes(keyword)
          || item.stockName.toLowerCase().includes(keyword);
        const recordDate = (item.lastSellTime ?? item.buyTime).slice(0, 10);
        const matchesStart = !filters.startDate || recordDate >= filters.startDate;
        const matchesEnd = !filters.endDate || recordDate <= filters.endDate;
        return matchesKeyword && matchesStart && matchesEnd;
      }).sort((left, right) => (
        timestamp(right.lastSellTime ?? right.buyTime) - timestamp(left.lastSellTime ?? left.buyTime)
      ));
      const offset = (targetPage - 1) * page.pageSize;
      const nextRecords = filteredRecords.slice(offset, offset + page.pageSize);
      setRecords((current) => append ? [...current, ...nextRecords] : nextRecords);
      mobileLoadedPageRef.current = append ? targetPage : 1;
      setPage((current) => ({ ...current, total: filteredRecords.length }));
      setCumulativeGrossProfit(filteredRecords.reduce(
        (total, item) => total + (item.soldQuantity > 0 ? item.grossProfit : 0),
        0,
      ));
    } catch (cause) {
      if (!append) {
        setRecords([]);
        setPage((current) => ({ ...current, total: 0 }));
        setCumulativeGrossProfit(0);
      }
      setError(cause instanceof Error ? cause.message : '交易记录读取失败');
    } finally {
      if (append) {
        setLoadingMore(false);
      } else {
        setLoading(false);
      }
    }
  }, [filters, page.pageSize]);

  useEffect(() => {
    void loadRecords(isMobile ? 1 : page.current, false);
  }, [isMobile, loadRecords, page.current]);

  useEffect(() => {
    const mediaQuery = window.matchMedia(MOBILE_MEDIA_QUERY);
    const handleViewportChange = (event: MediaQueryListEvent) => {
      mobileLoadedPageRef.current = 1;
      setPage((current) => ({ ...current, current: 1 }));
      setIsMobile(event.matches);
    };
    mediaQuery.addEventListener('change', handleViewportChange);
    return () => mediaQuery.removeEventListener('change', handleViewportChange);
  }, []);

  useEffect(() => {
    loadingMoreRef.current = loadingMore;
  }, [loadingMore]);

  const loadNextMobilePage = useCallback(async () => {
    if (!isMobile || loadingMoreRef.current || records.length >= page.total) {
      return;
    }
    loadingMoreRef.current = true;
    try {
      await loadRecords(mobileLoadedPageRef.current + 1, true);
    } finally {
      loadingMoreRef.current = false;
    }
  }, [isMobile, loadRecords, page.total, records.length]);

  useEffect(() => {
    const trigger = loadMoreTriggerRef.current;
    if (!isMobile || !trigger || records.length >= page.total) {
      return undefined;
    }
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        void loadNextMobilePage();
      }
    }, { rootMargin: '160px 0px' });
    observer.observe(trigger);
    return () => observer.disconnect();
  }, [isMobile, loadNextMobilePage, page.total, records.length]);

  const applyFilters = () => {
    if (draftFilters.startDate && draftFilters.endDate && draftFilters.startDate > draftFilters.endDate) {
      setError('开始日期不能晚于结束日期');
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

  const profit = (value?: number) => value == null
    ? '-'
    : <Text type={value >= 0 ? 'danger' : 'success'}>{money(value)}</Text>;

  const rate = (value?: number) => value == null
    ? '-'
    : <Text type={value >= 0 ? 'danger' : 'success'}>{percent(value)}</Text>;

  const openTradeDetail = (item: TradeRecord) => {
    const hasSell = item.soldQuantity > 0;
    setDetailStock({
      code: item.stockCode,
      name: item.stockName,
      businessTitle: localizedText('交易详情', 'Trade details'),
      businessItems: [
        { key: 'tradeKey', label: localizedText('交易编号', 'Trade ID'), value: item.tradeKey || '-' },
        { key: 'buyTime', label: localizedText('买入时间', 'Buy time'), value: formatDateTime(item.buyTime) },
        { key: 'sellTime', label: localizedText('卖出时间', 'Sell time'), value: item.lastSellTime ? formatDateTime(item.lastSellTime) : localizedText('尚未卖出', 'Not sold') },
        { key: 'buyQuantity', label: localizedText('买入数量', 'Buy quantity'), value: item.buyQuantity },
        { key: 'soldQuantity', label: localizedText('卖出数量', 'Sold quantity'), value: item.soldQuantity || '-' },
        { key: 'remainingQuantity', label: localizedText('剩余数量', 'Remaining quantity'), value: item.remainingQuantity },
        { key: 'buyPrice', label: localizedText('买入价格', 'Buy price'), value: money(item.buyPrice) },
        { key: 'sellPrice', label: localizedText('卖出价格', 'Sell price'), value: money(item.averageSellPrice) },
        { key: 'buyAmount', label: localizedText('买入金额', 'Buy amount'), value: money(item.buyAmount) },
        { key: 'sellAmount', label: localizedText('卖出金额', 'Sell amount'), value: money(item.sellAmount) },
        { key: 'totalFee', label: localizedText('手续费', 'Fees'), value: money(item.totalFee) },
        { key: 'grossProfit', label: localizedText('收益', 'Profit'), value: hasSell ? profit(item.grossProfit) : '-' },
        { key: 'netProfit', label: localizedText('净收益', 'Net profit'), value: hasSell ? profit(item.netProfit) : '-' },
        { key: 'grossReturnRate', label: localizedText('收益率', 'Return'), value: hasSell ? rate(item.grossReturnRate) : '-' },
        { key: 'returnRate', label: localizedText('净收益率', 'Net return'), value: hasSell ? rate(item.returnRate) : '-' },
        { key: 'holdingDays', label: localizedText('持仓时间', 'Holding period'), value: days(item.holdingDays) },
        { key: 'grossDailyReturnRate', label: localizedText('日收益率', 'Daily return'), value: hasSell ? rate(item.grossDailyReturnRate) : '-' },
        { key: 'dailyReturnRate', label: localizedText('净日收益率', 'Net daily return'), value: hasSell ? rate(item.dailyReturnRate) : '-' },
      ],
    });
  };

  const columns: ColumnsType<TradeRecord> = [
    {
      title: t('tradeRecords.columns.stockCode'),
      dataIndex: 'stockCode',
      key: 'stockCode',
      fixed: 'left',
      width: 110,
      render: (code: string, item) => (
        <button
          type="button"
          className="order-records-stock-code"
          aria-label={localizedText(`查看 ${item.stockName || code}（${code}）详情`, `View details for ${item.stockName || code} (${code})`)}
          onClick={(event) => {
            event.stopPropagation();
            openTradeDetail(item);
          }}
        >
          {code}
        </button>
      ),
    },
    {
      title: t('tradeRecords.columns.stockName'),
      dataIndex: 'stockName',
      key: 'stockName',
      width: 130,
      ellipsis: true,
      render: (name?: string) => <span className="order-records-stock-name">{name || '-'}</span>,
    },
    { title: '买入时间', dataIndex: 'buyTime', key: 'buyTime', width: 180, render: formatDateTime },
    { title: '卖出时间', dataIndex: 'lastSellTime', key: 'lastSellTime', width: 180, render: (value?: string) => value ? formatDateTime(value) : <Typography.Text type="secondary">待卖出</Typography.Text> },
    { title: '买入价格', dataIndex: 'buyPrice', key: 'buyPrice', width: 120, className: 'trade-record-number', render: money },
    { title: '卖出价格', dataIndex: 'averageSellPrice', key: 'averageSellPrice', width: 120, className: 'trade-record-number', render: money },
    { title: '收益', dataIndex: 'grossProfit', key: 'grossProfit', width: 125, className: 'trade-record-number', render: (value: number, item) => item.soldQuantity > 0 ? <strong className="trade-record-result">{profit(value)}</strong> : '-' },
    { title: '收益率', dataIndex: 'grossReturnRate', key: 'grossReturnRate', width: 120, className: 'trade-record-number', render: (value: number, item) => item.soldQuantity > 0 ? <strong className="trade-record-result">{rate(value)}</strong> : '-' },
    { title: '净收益', dataIndex: 'netProfit', key: 'netProfit', width: 125, className: 'trade-record-number', render: (value: number, item) => item.soldQuantity > 0 ? <strong className="trade-record-result">{profit(value)}</strong> : '-' },
    { title: '净收益率', dataIndex: 'returnRate', key: 'returnRate', width: 120, className: 'trade-record-number', render: (value: number, item) => item.soldQuantity > 0 ? <strong className="trade-record-result">{rate(value)}</strong> : '-' },
  ];

  return (
    <div className="order-records-table-view">
        <Space wrap className="order-records-filters mb-4">
          <Input
            prefix={<SearchOutlined />}
            placeholder={t('tradeRecords.searchPlaceholder')}
            value={draftFilters.keyword}
            onChange={(event) => setDraftFilters((current) => ({ ...current, keyword: event.target.value }))}
            onPressEnter={applyFilters}
            allowClear
          />
          <DatePicker.RangePicker
            className="history-date-range"
            value={[
              draftFilters.startDate ? dayjs(draftFilters.startDate) : null,
              draftFilters.endDate ? dayjs(draftFilters.endDate) : null,
            ]}
            format={DATE_FORMAT}
            allowClear
            allowEmpty={[true, true]}
            placeholder={[localizedText('开始日期', 'Start date'), localizedText('结束日期', 'End date')]}
            presets={[
              { label: localizedText('最近 7 天', 'Last 7 days'), value: [dayjs().subtract(6, 'day'), dayjs()] },
              { label: localizedText('最近 30 天', 'Last 30 days'), value: [dayjs().subtract(29, 'day'), dayjs()] },
              { label: localizedText('今年以来', 'Year to date'), value: [dayjs().startOf('year'), dayjs()] },
            ]}
            onChange={(dates) => setDraftFilters((current) => ({
              ...current,
              startDate: dates?.[0]?.format(DATE_FORMAT) ?? '',
              endDate: dates?.[1]?.format(DATE_FORMAT) ?? '',
            }))}
          />
          <Button icon={<SearchOutlined />} onClick={applyFilters}>{t('common.search')}</Button>
        </Space>
        <div className="order-records-desktop-table">
          <Table
            rowKey="tradeKey"
            columns={columns}
            dataSource={records}
            loading={loading}
            locale={{ emptyText: <Empty description={t('tradeRecords.empty')} /> }}
            className="trade-records-table"
            tableLayout="fixed"
            scroll={{ x: 'max-content' }}
            pagination={false}
          />
        </div>
        <section className="order-records-mobile-lead" aria-label="交易记录摘要">
          <div className="order-records-mobile-lead-item">
            <span>累计交易</span>
            <strong>{page.total} 笔</strong>
          </div>
          <div className="order-records-mobile-lead-item order-records-mobile-lead-result">
            <span>累计收益</span>
            <strong className={cumulativeGrossProfit >= 0 ? 'is-up' : 'is-down'}>
              {signedMoney(cumulativeGrossProfit)}
            </strong>
          </div>
        </section>
        <div className="order-records-mobile-list" aria-live="polite">
          {loading && records.length === 0 ? (
            <div className="order-records-mobile-state"><Spin /></div>
          ) : records.length > 0 ? (
            <>
              {records.map((item) => (
                <article
                  className="order-records-mobile-row"
                  key={item.tradeKey}
                >
                  <span className="order-records-mobile-row-main">
                    <strong>{item.stockName || item.stockCode || '-'}</strong>
                    <small>{item.stockCode}</small>
                  </span>
                  <span className="order-records-mobile-row-prices">
                    <span className="order-records-mobile-row-sell-time">
                      <small>卖出时间</small>
                      <strong>{item.lastSellTime ? compactDate(item.lastSellTime) : '-'}</strong>
                    </span>
                    <span>
                      <small>买入</small>
                      <strong>{money(item.buyPrice)}</strong>
                    </span>
                    <span>
                      <small>卖出</small>
                      <strong>{item.soldQuantity > 0 ? money(item.averageSellPrice) : '-'}</strong>
                    </span>
                    <span>
                      <small>持仓</small>
                      <strong>{days(item.holdingDays)}</strong>
                    </span>
                  </span>
                  <span className="order-records-mobile-row-result">
                    <span className="order-records-mobile-row-result-line">
                      <small>收益</small>
                      <strong className={item.soldQuantity <= 0 ? 'is-neutral' : item.grossProfit >= 0 ? 'is-up' : 'is-down'}>
                        {item.soldQuantity > 0 ? signedMoney(item.grossProfit) : '持仓中'}
                      </strong>
                      <small className={item.soldQuantity <= 0 ? 'is-neutral' : item.grossProfit >= 0 ? 'is-up' : 'is-down'}>
                        {item.soldQuantity > 0 ? signedPercent(item.grossReturnRate) : '-'}
                      </small>
                    </span>
                    <span className="order-records-mobile-row-result-line">
                      <small>净收益</small>
                      <strong className={item.soldQuantity <= 0 ? 'is-neutral' : item.netProfit >= 0 ? 'is-up' : 'is-down'}>
                        {item.soldQuantity > 0 ? signedMoney(item.netProfit) : '-'}
                      </strong>
                      <small className={item.soldQuantity <= 0 ? 'is-neutral' : item.netProfit >= 0 ? 'is-up' : 'is-down'}>
                        {item.soldQuantity > 0 ? signedPercent(item.returnRate) : '-'}
                      </small>
                    </span>
                  </span>
                </article>
              ))}
              {(records.length < page.total || loadingMore) && (
                <div
                  ref={loadMoreTriggerRef}
                  className="order-records-mobile-load-more"
                  aria-label={loadingMore ? localizedText('正在加载更多', 'Loading more') : undefined}
                >
                  {loadingMore && <Spin size="small" />}
                </div>
              )}
            </>
          ) : (
            <div className="order-records-mobile-state"><Empty description={t('tradeRecords.empty')} /></div>
          )}
        </div>
        <div className="order-records-desktop-pagination mt-4 overflow-x-auto pb-1">
          <div className="flex min-w-max justify-end">
            <Pagination
              current={page.current}
              pageSize={page.pageSize}
              total={page.total}
              showSizeChanger
              showQuickJumper
              responsive
              pageSizeOptions={[10, 20, 50, 100]}
              showTotal={(total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`}
              onChange={changePage}
            />
          </div>
        </div>
        <StockDetailDrawer stock={detailStock} onClose={() => setDetailStock(null)} />
    </div>
  );
};

export default TradeRecordTable;
// AI_GENERATE_END ------------
