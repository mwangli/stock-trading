// AI_GENERATE_START -
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import dayjs from 'dayjs';
import { Button, DatePicker, Empty, Input, Pagination, Select, Space, Spin, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { SearchOutlined } from '@ant-design/icons';
import request from '@shared/utils/request';
import type { ApiResponse } from '@shared/utils/api';
import { formatDateTime } from '@shared/utils/dateTime';
import { localizedText } from '@shared/utils/localizedText';
import { useTranslation } from 'react-i18next';
import StockDetailDrawer, { type StockDetailSelection } from '../../components/StockDetailDrawer';

interface BrokerOrder {
  orderId: string;
  stockCode: string;
  stockName: string;
  direction: string;
  orderPrice: number;
  orderQuantity: number;
  filledQuantity: number;
  averageFillPrice?: number;
  transactionAmount: number;
  commission: number;
  status: string;
  orderTime: string;
}

interface OrderFilters {
  keyword: string;
  direction: string;
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

const buildDefaultFilters = (): OrderFilters => {
  const endDate = new Date();
  const startDate = new Date(endDate);
  startDate.setFullYear(startDate.getFullYear() - 5);
  return {
    keyword: '',
    direction: 'ALL',
    startDate: formatLocalDate(startDate),
    endDate: formatLocalDate(endDate),
  };
};

const money = (value?: number) => value == null ? '-' : `¥${Number(value).toFixed(2)}`;
const timestamp = (value?: string) => {
  const parsed = value ? Date.parse(value) : Number.NaN;
  return Number.isFinite(parsed) ? parsed : 0;
};

const OrderHistoryTable: React.FC = () => {
  const { t } = useTranslation();
  const defaultFilters = useMemo(buildDefaultFilters, []);
  const [draftFilters, setDraftFilters] = useState<OrderFilters>(defaultFilters);
  const [filters, setFilters] = useState<OrderFilters>(defaultFilters);
  const [orders, setOrders] = useState<BrokerOrder[]>([]);
  const [page, setPage] = useState<PageState>({ current: 1, pageSize: 10, total: 0 });
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

  const loadOrders = useCallback(async (targetPage: number, append: boolean) => {
    if (append) {
      setLoadingMore(true);
    } else {
      setLoading(true);
    }
    setError(null);
    try {
      const response = await request.get('/trading/orders', {
        params: { limit: 500 },
      }) as ApiResponse<Array<Record<string, unknown>>>;
      if (!response.success) {
        throw new Error(response.message || t('historyOrders.loadError'));
      }
      const keyword = filters.keyword.toLowerCase();
      const mappedOrders: BrokerOrder[] = (response.data ?? []).map((item) => {
        const price = Number(item.price ?? 0);
        const quantity = Number(item.quantity ?? 0);
        const filledQuantity = Number(item.filled_quantity ?? 0);
        const averageFillPrice = item.average_fill_price == null ? undefined : Number(item.average_fill_price);
        return {
          orderId: String(item.broker_order_id ?? ''),
          stockCode: String(item.instrument ?? ''),
          stockName: String(item.stock_name ?? ''),
          direction: String(item.side ?? ''),
          orderPrice: price,
          orderQuantity: quantity,
          filledQuantity,
          averageFillPrice,
          transactionAmount: (averageFillPrice ?? price) * (filledQuantity || quantity),
          commission: Number(item.total_fee ?? 0),
          status: String(item.status ?? item.raw_status ?? ''),
          orderTime: String(item.order_time ?? item.updated_at ?? ''),
        };
      });
      const filteredOrders = mappedOrders.filter((item) => {
        const matchesKeyword = !keyword
          || item.stockCode.toLowerCase().includes(keyword)
          || item.stockName.toLowerCase().includes(keyword);
        const matchesDirection = filters.direction === 'ALL' || item.direction === filters.direction;
        const orderDate = item.orderTime.slice(0, 10);
        const matchesStart = !filters.startDate || orderDate >= filters.startDate;
        const matchesEnd = !filters.endDate || orderDate <= filters.endDate;
        return matchesKeyword && matchesDirection && matchesStart && matchesEnd;
      }).sort((left, right) => timestamp(right.orderTime) - timestamp(left.orderTime));
      const offset = (targetPage - 1) * page.pageSize;
      const nextOrders = filteredOrders.slice(offset, offset + page.pageSize);
      setOrders((current) => append ? [...current, ...nextOrders] : nextOrders);
      mobileLoadedPageRef.current = append ? targetPage : 1;
      setPage((current) => ({ ...current, total: filteredOrders.length }));
    } catch (cause) {
      if (!append) {
        setOrders([]);
        setPage((current) => ({ ...current, total: 0 }));
      }
      setError(cause instanceof Error ? cause.message : t('historyOrders.loadError'));
    } finally {
      if (append) {
        setLoadingMore(false);
      } else {
        setLoading(false);
      }
    }
  }, [filters, page.pageSize, t]);

  useEffect(() => {
    void loadOrders(isMobile ? 1 : page.current, false);
  }, [isMobile, loadOrders, page.current]);

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
    if (!isMobile || loadingMoreRef.current || orders.length >= page.total) {
      return;
    }
    loadingMoreRef.current = true;
    try {
      await loadOrders(mobileLoadedPageRef.current + 1, true);
    } finally {
      loadingMoreRef.current = false;
    }
  }, [isMobile, loadOrders, orders.length, page.total]);

  useEffect(() => {
    const trigger = loadMoreTriggerRef.current;
    if (!isMobile || !trigger || orders.length >= page.total) {
      return undefined;
    }
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        void loadNextMobilePage();
      }
    }, { rootMargin: '160px 0px' });
    observer.observe(trigger);
    return () => observer.disconnect();
  }, [isMobile, loadNextMobilePage, orders.length, page.total]);

  const applyFilters = () => {
    if (draftFilters.startDate && draftFilters.endDate && draftFilters.startDate > draftFilters.endDate) {
      setError(t('historyOrders.dateError'));
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

  const directionTag = (value: string) => {
    const color = value === 'BUY' ? 'red' : value === 'SELL' ? 'green' : 'default';
    const label = value === 'BUY' ? t('historyOrders.buy') : value === 'SELL' ? t('historyOrders.sell') : value;
    return <Tag color={color}>{label}</Tag>;
  };

  const openOrderDetail = (item: BrokerOrder) => {
    setDetailStock({
      code: item.stockCode,
      name: item.stockName,
      businessTitle: localizedText('订单详情', 'Order details'),
      businessItems: [
        { key: 'orderId', label: localizedText('订单编号', 'Order ID'), value: item.orderId || '-' },
        { key: 'direction', label: localizedText('方向', 'Direction'), value: directionTag(item.direction) },
        { key: 'status', label: localizedText('订单状态', 'Order status'), value: item.status || '-' },
        { key: 'orderPrice', label: localizedText('委托价格', 'Order price'), value: money(item.orderPrice) },
        { key: 'orderQuantity', label: localizedText('委托数量', 'Order quantity'), value: item.orderQuantity },
        { key: 'filledQuantity', label: localizedText('成交数量', 'Filled quantity'), value: item.filledQuantity },
        { key: 'averageFillPrice', label: localizedText('成交均价', 'Average fill price'), value: money(item.averageFillPrice) },
        { key: 'transactionAmount', label: localizedText('成交金额', 'Transaction amount'), value: money(item.transactionAmount) },
        { key: 'commission', label: localizedText('手续费', 'Commission'), value: money(item.commission) },
        { key: 'orderTime', label: localizedText('委托时间', 'Order time'), value: formatDateTime(item.orderTime) },
      ],
    });
  };

  const columns: ColumnsType<BrokerOrder> = [
    {
      title: t('historyOrders.columns.stockCode'),
      dataIndex: 'stockCode',
      key: 'stockCode',
      width: 110,
      render: (code: string, item) => (
        <button
          type="button"
          className="order-records-stock-code"
          aria-label={localizedText(`查看 ${item.stockName || code}（${code}）详情`, `View details for ${item.stockName || code} (${code})`)}
          onClick={(event) => {
            event.stopPropagation();
            openOrderDetail(item);
          }}
        >
          {code}
        </button>
      ),
    },
    {
      title: t('historyOrders.columns.stockName'),
      dataIndex: 'stockName',
      key: 'stockName',
      width: 130,
      ellipsis: true,
      render: (name?: string) => <span className="order-records-stock-name">{name || '-'}</span>,
    },
    { title: t('historyOrders.columns.direction'), dataIndex: 'direction', key: 'direction', render: directionTag },
    { title: t('historyOrders.columns.orderPrice'), dataIndex: 'orderPrice', key: 'orderPrice', render: money },
    { title: t('historyOrders.columns.orderQuantity'), dataIndex: 'orderQuantity', key: 'orderQuantity' },
    { title: t('historyOrders.columns.averageFillPrice'), dataIndex: 'averageFillPrice', key: 'averageFillPrice', render: money },
    { title: t('historyOrders.columns.transactionAmount'), dataIndex: 'transactionAmount', key: 'transactionAmount', render: money },
    { title: t('historyOrders.columns.time'), dataIndex: 'orderTime', key: 'orderTime', render: formatDateTime },
  ];

  return (
    <div className="order-records-table-view">
        <Space wrap className="order-records-filters mb-4">
          <Input
            prefix={<SearchOutlined />}
            placeholder={t('historyOrders.searchPlaceholder')}
            value={draftFilters.keyword}
            onChange={(event) => setDraftFilters((current) => ({ ...current, keyword: event.target.value }))}
            onPressEnter={applyFilters}
            allowClear
          />
          <Select
            value={draftFilters.direction}
            onChange={(value) => setDraftFilters((current) => ({ ...current, direction: value }))}
            options={[
              { value: 'ALL', label: t('historyOrders.allDirections') },
              { value: 'BUY', label: t('historyOrders.buy') },
              { value: 'SELL', label: t('historyOrders.sell') },
            ]}
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
            rowKey={(item) => `${item.orderId}-${item.orderTime}`}
            columns={columns}
            dataSource={orders}
            loading={loading}
            locale={{ emptyText: <Empty description={t('historyOrders.empty')} /> }}
            scroll={{ x: 'max-content' }}
            pagination={false}
          />
        </div>
        <div className="order-records-mobile-list" aria-live="polite">
          {loading && orders.length === 0 ? (
            <div className="order-records-mobile-state"><Spin /></div>
          ) : orders.length > 0 ? (
            <>
              {orders.map((item) => (
                <article className="order-records-mobile-card" key={`${item.orderId}-${item.orderTime}`}>
              <header className="order-records-mobile-card-header">
                <div className="order-records-mobile-security">
                  <button
                    type="button"
                    className="order-records-stock-code"
                    aria-label={localizedText(`查看 ${item.stockName || item.stockCode}（${item.stockCode}）详情`, `View details for ${item.stockName || item.stockCode} (${item.stockCode})`)}
                    onClick={() => openOrderDetail(item)}
                  >
                    {item.stockCode}
                  </button>
                  <strong>{item.stockName || '-'}</strong>
                </div>
                {directionTag(item.direction)}
              </header>
              <div className="order-records-mobile-grid">
                <div><span>{t('historyOrders.columns.orderPrice')}</span><strong>{money(item.orderPrice)}</strong></div>
                <div><span>{t('historyOrders.columns.averageFillPrice')}</span><strong>{money(item.averageFillPrice)}</strong></div>
                <div><span>{t('historyOrders.columns.orderQuantity')}</span><strong>{item.orderQuantity}</strong></div>
                <div><span>{t('historyOrders.columns.filledQuantity')}</span><strong>{item.filledQuantity}</strong></div>
                <div><span>{t('historyOrders.columns.transactionAmount')}</span><strong>{money(item.transactionAmount)}</strong></div>
                <div><span>{t('historyOrders.columns.commission')}</span><strong>{money(item.commission)}</strong></div>
              </div>
              <footer className="order-records-mobile-card-footer">
                <span>{formatDateTime(item.orderTime)}</span>
                <strong>{item.status || '-'}</strong>
              </footer>
                </article>
              ))}
              {(orders.length < page.total || loadingMore) && (
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
            <div className="order-records-mobile-state"><Empty description={t('historyOrders.empty')} /></div>
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
              showTotal={(total, range) => t('common.pagination', { start: range[0], end: range[1], total })}
              onChange={changePage}
            />
          </div>
        </div>
        <StockDetailDrawer stock={detailStock} onClose={() => setDetailStock(null)} />
    </div>
  );
};

export default OrderHistoryTable;
// AI_GENERATE_END -
