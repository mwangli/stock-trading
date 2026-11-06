// AI_GENERATE_START ---
import './styles.css';
import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Empty, Input, Pagination, Skeleton, Table, Tooltip, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import request from '@shared/utils/request';
import type { ApiResponse } from '@shared/utils/api';
import { formatDateTime } from '@shared/utils/dateTime';
import { usePageHeader } from '../../layouts/PageHeaderContext';

const { Text } = Typography;

interface ActiveStockItem {
  code: string;
  name: string;
  market: string;
  price?: number;
  changePercent?: number;
  volume?: number;
  volumeDisplay?: string;
  amount?: number;
  amountDisplay?: string;
  updateTime?: string;
}

interface ActiveStockPage {
  items: Array<Record<string, unknown>>;
  total: number;
  page: number;
  pageSize: number;
  asOfDate?: string;
}

const toText = (value: unknown) => value == null ? '' : String(value);
const toNumber = (value: unknown) => {
  if (value == null || value === '') return undefined;
  const result = Number(value);
  return Number.isFinite(result) ? result : undefined;
};
const formatPrice = (value?: number) => value == null ? '-' : `¥${value.toFixed(2)}`;
const formatPercent = (value?: number) => value == null ? '-' : `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
const formatQuantity = (value?: number) => value == null ? '-' : value.toLocaleString('zh-CN');
const formatAmount = (value?: number) => value == null
  ? '-'
  : `¥${value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const changeType = (value?: number): 'danger' | 'success' | 'secondary' => {
  if (value == null || value === 0) return 'secondary';
  return value > 0 ? 'danger' : 'success';
};

const StockInformation: React.FC = () => {
  const { t } = useTranslation();
  const [stocks, setStocks] = useState<ActiveStockItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [searchInput, setSearchInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [asOfDate, setAsOfDate] = useState('');
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  usePageHeader(t('stockInformation.subtitle'));

  const loadStocks = useCallback(async (page: number, pageSize: number, nextKeyword: string) => {
    setLoading(true);
    setLoadFailed(false);
    try {
      const response = await request.get('/market/active-instruments', {
        params: { page, pageSize, keyword: nextKeyword.trim() },
      }) as ApiResponse<ActiveStockPage>;
      if (!response.success || !response.data) {
        throw new Error(response.message || 'Failed to load active stocks');
      }
      const stockPage = response.data;
      setStocks(stockPage.items.map((item) => ({
        code: toText(item.code),
        name: toText(item.name),
        market: toText(item.market),
        price: toNumber(item.price),
        changePercent: toNumber(item.change_percent),
        volume: toNumber(item.volume),
        volumeDisplay: toText(item.volume_display),
        amount: toNumber(item.amount),
        amountDisplay: toText(item.amount_display),
        updateTime: toText(item.update_time),
      })));
      setPagination({ current: stockPage.page, pageSize: stockPage.pageSize, total: stockPage.total });
      setAsOfDate(stockPage.asOfDate ?? '');
    } catch {
      setStocks([]);
      setPagination({ current: page, pageSize, total: 0 });
      setAsOfDate('');
      setLoadFailed(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadStocks(1, 10, '');
  }, [loadStocks]);

  const submitSearch = () => {
    const nextKeyword = searchInput.trim();
    setKeyword(nextKeyword);
    void loadStocks(1, pagination.pageSize, nextKeyword);
  };
  const columns: ColumnsType<ActiveStockItem> = [
    { title: t('stockInformation.columns.code'), dataIndex: 'code', key: 'code', width: 132 },
    { title: t('stockInformation.columns.name'), dataIndex: 'name', key: 'name', width: 160, ellipsis: true },
    { title: t('stockInformation.columns.market'), dataIndex: 'market', key: 'market', width: 90 },
    { title: t('stockInformation.columns.price'), dataIndex: 'price', key: 'price', width: 120, align: 'right', render: formatPrice },
    {
      title: t('stockInformation.columns.changePercent'),
      dataIndex: 'changePercent',
      key: 'changePercent',
      width: 120,
      align: 'right',
      render: (value?: number) => <Text type={changeType(value)}>{formatPercent(value)}</Text>,
    },
    {
      title: t('stockInformation.columns.volume'),
      dataIndex: 'volume',
      key: 'volume',
      width: 130,
      align: 'right',
      render: (value: number | undefined, item: ActiveStockItem) => (
        <span title={formatQuantity(value)}>{item.volumeDisplay || formatQuantity(value)}</span>
      ),
    },
    {
      title: t('stockInformation.columns.amount'),
      dataIndex: 'amount',
      key: 'amount',
      width: 140,
      align: 'right',
      render: (value: number | undefined, item: ActiveStockItem) => (
        <span title={formatAmount(value)}>{item.amountDisplay || formatAmount(value)}</span>
      ),
    },
    { title: t('stockInformation.columns.updateTime'), dataIndex: 'updateTime', key: 'updateTime', width: 180, align: 'center', render: formatDateTime },
  ];

  const emptyDescription = loadFailed ? t('stockInformation.loadError') : t('stockInformation.empty');

  return (
    <div className="app-page stock-information-page">
      <Card
        className="surface-card stock-information-card"
        title={t('stockInformation.tableTitle')}
        extra={asOfDate ? <Text type="secondary">{t('stockInformation.snapshot', { date: asOfDate })}</Text> : null}
      >
        <div className="stock-information-toolbar">
          <form
            className="stock-information-search"
            onSubmit={(event) => {
              event.preventDefault();
              submitSearch();
            }}
          >
            <Input
              allowClear
              value={searchInput}
              placeholder={t('stockInformation.searchPlaceholder')}
              prefix={<SearchOutlined aria-hidden="true" />}
              onChange={(event) => setSearchInput(event.target.value)}
              onClear={() => {
                setSearchInput('');
                setKeyword('');
                void loadStocks(1, pagination.pageSize, '');
              }}
            />
            <Tooltip title={t('common.search')}>
              <Button htmlType="submit" type="primary" icon={<SearchOutlined />} aria-label={t('common.search')} />
            </Tooltip>
          </form>
          <Tooltip title={t('common.refresh')}>
            <Button
              icon={<ReloadOutlined />}
              aria-label={t('common.refresh')}
              loading={loading}
              onClick={() => void loadStocks(pagination.current, pagination.pageSize, keyword)}
            />
          </Tooltip>
        </div>

        <div className="stock-information-desktop">
          <Table
            rowKey="code"
            size="middle"
            columns={columns}
            dataSource={stocks}
            loading={loading}
            scroll={{ x: 1132 }}
            pagination={{
              current: pagination.current,
              pageSize: pagination.pageSize,
              total: pagination.total,
              showSizeChanger: true,
              pageSizeOptions: [10, 20, 50],
              showTotal: (total) => t('stockInformation.total', { total }),
              onChange: (page, pageSize) => {
                const nextPage = pageSize === pagination.pageSize ? page : 1;
                void loadStocks(nextPage, pageSize, keyword);
              },
            }}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyDescription} /> }}
          />
        </div>
        <div className="stock-information-mobile" aria-busy={loading}>
          {loading ? (
            Array.from({ length: 5 }, (_, index) => (
              <div className="stock-information-mobile-skeleton" key={index}>
                <Skeleton active title={{ width: '45%' }} paragraph={{ rows: 2 }} />
              </div>
            ))
          ) : stocks.length > 0 ? (
            stocks.map((stock) => (
              <article className="stock-information-mobile-item" key={stock.code}>
                <div className="stock-information-mobile-primary">
                  <span className="stock-information-mobile-identity">
                    <strong>{stock.name || stock.code}</strong>
                    <span>{stock.code} · {stock.market || '-'}</span>
                  </span>
                  <span className="stock-information-mobile-quote">
                    <strong>{formatPrice(stock.price)}</strong>
                    <Text type={changeType(stock.changePercent)}>{formatPercent(stock.changePercent)}</Text>
                  </span>
                </div>
                <dl className="stock-information-mobile-details">
                  <div><dt>{t('stockInformation.columns.volume')}</dt><dd>{stock.volumeDisplay || formatQuantity(stock.volume)}</dd></div>
                  <div><dt>{t('stockInformation.columns.amount')}</dt><dd>{stock.amountDisplay || formatAmount(stock.amount)}</dd></div>
                  <div><dt>{t('stockInformation.columns.updateTime')}</dt><dd>{formatDateTime(stock.updateTime)}</dd></div>
                </dl>
              </article>
            ))
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyDescription} />
          )}
          {pagination.total > 0 && (
            <Pagination
              className="stock-information-mobile-pagination"
              current={pagination.current}
              pageSize={pagination.pageSize}
              total={pagination.total}
              showSizeChanger={false}
              showLessItems
              onChange={(page) => void loadStocks(page, pagination.pageSize, keyword)}
            />
          )}
        </div>
      </Card>
    </div>
  );
};

export default StockInformation;
// AI_GENERATE_END ---
