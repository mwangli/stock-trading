// AI_GENERATE_START----------------------------
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { Button, DatePicker, Empty, Input, Pagination, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { SearchOutlined } from '@ant-design/icons';
import request from '../../utils/request';
import type { ApiResponse } from '../../utils/api';
import { formatDateTime } from '../../utils/dateTime';
import { localizedText } from '../../utils/localizedText';
import { useTranslation } from 'react-i18next';
import StockDetailDrawer, { type StockDetailSelection } from '../../components/StockDetailDrawer';

const { Text } = Typography;

interface TradeRecord {
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

interface BrokerPage<T> {
  items: T[];
  total: number;
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

const money = (value?: number) => value == null ? '-' : `¥${Number(value).toFixed(2)}`;
const percent = (value?: number) => value == null ? '-' : `${(Number(value) * 100).toFixed(2)}%`;
const days = (value?: number) => value == null ? '-' : `${Number(value).toFixed(2)} 天`;

const TradeRecordTable: React.FC = () => {
  const { t } = useTranslation();
  const defaultFilters = useMemo(buildDefaultFilters, []);
  const [draftFilters, setDraftFilters] = useState<TradeFilters>(defaultFilters);
  const [filters, setFilters] = useState<TradeFilters>(defaultFilters);
  const [records, setRecords] = useState<TradeRecord[]>([]);
  const [page, setPage] = useState<PageState>({ current: 1, pageSize: 10, total: 0 });
  const [loading, setLoading] = useState(false);
  const [, setError] = useState<string | null>(null);
  const [detailStock, setDetailStock] = useState<StockDetailSelection | null>(null);

  const loadRecords = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await request.get('/tradeRecords/trades', {
        params: {
          keyword: filters.keyword || undefined,
          startDate: filters.startDate,
          endDate: filters.endDate,
          current: page.current,
          pageSize: page.pageSize,
        },
      }) as ApiResponse<BrokerPage<TradeRecord>>;
      if (!response.success) {
        throw new Error(response.message || '交易记录读取失败');
      }
      setRecords(response.data?.items ?? []);
      setPage((current) => ({ ...current, total: response.data?.total ?? 0 }));
    } catch (cause) {
      setRecords([]);
      setPage((current) => ({ ...current, total: 0 }));
      setError(cause instanceof Error ? cause.message : '交易记录读取失败');
    } finally {
      setLoading(false);
    }
  }, [filters, page.current, page.pageSize]);

  useEffect(() => {
    void loadRecords();
  }, [loadRecords]);

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

  const detailValue = (label: string, value: React.ReactNode, className = '') => (
    <div className={`trade-record-detail-item ${className}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );

  const expandedRowRender = (item: TradeRecord) => (
    <div className="trade-record-expanded">
      <div className="trade-record-detail-grid">
        {detailValue('买入数量', item.buyQuantity, 'trade-record-number')}
        {detailValue('卖出数量', item.soldQuantity || '-', 'trade-record-number')}
        {detailValue('买入金额', money(item.buyAmount), 'trade-record-number')}
        {detailValue('卖出金额', money(item.sellAmount), 'trade-record-number')}
        {detailValue('手续费', money(item.totalFee), 'trade-record-number')}
        {detailValue('毛收益', item.soldQuantity > 0 ? profit(item.grossProfit) : '-', 'trade-record-number')}
        {detailValue('净收益', item.soldQuantity > 0 ? profit(item.netProfit) : '-', 'trade-record-number')}
        {detailValue('毛收益率', item.soldQuantity > 0 ? rate(item.grossReturnRate) : '-', 'trade-record-number')}
        {detailValue('日收益率', item.soldQuantity > 0 ? rate(item.dailyReturnRate) : '-', 'trade-record-number')}
      </div>
    </div>
  );

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
            setDetailStock({ code, name: item.stockName });
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
    { title: '净收益', dataIndex: 'netProfit', key: 'netProfit', width: 125, className: 'trade-record-number', render: (value: number, item) => item.soldQuantity > 0 ? <strong className="trade-record-result">{profit(value)}</strong> : '-' },
    { title: '收益率', dataIndex: 'returnRate', key: 'returnRate', width: 120, className: 'trade-record-number', render: (value: number, item) => item.soldQuantity > 0 ? <strong className="trade-record-result">{rate(value)}</strong> : '-' },
    { title: '持仓时间', dataIndex: 'holdingDays', key: 'holdingDays', width: 110, className: 'trade-record-number', render: days },
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
        <Table
          rowKey={(item, index) => `${item.stockCode}-${item.buyTime}-${index ?? 0}`}
          columns={columns}
          dataSource={records}
          loading={loading}
          locale={{ emptyText: <Empty description={t('tradeRecords.empty')} /> }}
          className="trade-records-table"
          tableLayout="fixed"
          scroll={{ x: 'max-content' }}
          expandable={{ expandedRowRender, columnWidth: 44 }}
          pagination={false}
        />
        <div className="mt-4 overflow-x-auto pb-1">
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
// AI_GENERATE_END----------------------------
