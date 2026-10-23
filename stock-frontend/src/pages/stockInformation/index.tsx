// AI_GENERATE_START---------------
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Card, Empty, Input, Pagination, Space, Table, Tooltip, message } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import request from '../../utils/request';
import type { ApiResponse } from '../../utils/api';
import { formatDateTime } from '../../utils/dateTime';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import { useTranslation } from 'react-i18next';

interface StockInfo {
  id: string;
  code: string;
  name: string;
  market?: string;
  price?: number;
  changeAmount?: number;
  changePercent?: number;
  totalMarketValue?: number;
  turnoverRate?: number;
  volumeRatio?: number;
  industryCode?: number;
  listedDate?: string;
  updateTime?: string;
}

interface StockInfoPage {
  items: StockInfo[];
  total: number;
  current: number;
  pageSize: number;
}

const numberValue = (value?: number, digits = 2) => (
  value === undefined || value === null ? '-' : Number(value).toFixed(digits)
);

const marketLabels: Record<string, string> = {
  SH: '上海',
  SZ: '深圳',
  BJ: '北京',
};

const securityCategoryLabels: Record<number, string> = {
  0: '深市主板',
  2: '深市主板（原中小板）',
  9: '沪市主板',
  18: '创业板',
};

const compactAmountFormatter = new Intl.NumberFormat('en-US', {
  notation: 'compact',
  maximumFractionDigits: 2,
});

const StockInformation: React.FC = () => {
  const { t } = useTranslation();
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [stocks, setStocks] = useState<StockInfo[]>([]);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [searchToken, setSearchToken] = useState(0);
  const [loading, setLoading] = useState(false);

  const loadStocks = useCallback(async (page: number, size: number, search: string) => {
    setLoading(true);
    try {
      const response = await request.get('/stockInfo/list', {
        params: {
          current: page,
          pageSize: size,
          keywords: search || undefined,
        },
      }) as ApiResponse<StockInfoPage>;
      if (!response.success) {
        throw new Error(response.message || t('stockInformation.loadError'));
      }
      setStocks(response.data?.items ?? []);
      setTotal(response.data?.total ?? 0);
      setCurrent(response.data?.current ?? page);
      setPageSize(response.data?.pageSize ?? size);
    } catch (cause) {
      setStocks([]);
      setTotal(0);
      message.error(cause instanceof Error ? cause.message : t('stockInformation.loadError'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadStocks(current, pageSize, appliedKeyword);
  }, [appliedKeyword, current, loadStocks, pageSize, searchToken]);

  const search = useCallback(() => {
    setCurrent(1);
    setAppliedKeyword(keyword.trim());
    setSearchToken((value) => value + 1);
  }, [keyword]);

  const formatMarket = (value?: string) => value ? marketLabels[value.toUpperCase()] ?? value : '-';
  const formatAmount = (value?: number) => value == null ? '-' : `¥${compactAmountFormatter.format(Number(value))}`;
  const formatFullAmount = (value?: number) => value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;
  const formatPercent = (value?: number) => value == null ? '-' : `${Number(value).toFixed(2)}%`;
  const formatSecurityCategory = (value?: number) => value == null ? '-' : securityCategoryLabels[value] ?? `其他类别（${value}）`;

  const columns = [
    { title: t('stockInformation.columns.code'), dataIndex: 'code', key: 'code', width: 110, fixed: 'left' as const },
    { title: t('stockInformation.columns.name'), dataIndex: 'name', key: 'name', width: 140, fixed: 'left' as const },
    { title: t('stockInformation.columns.market'), dataIndex: 'market', key: 'market', width: 90, render: formatMarket },
    { title: t('stockInformation.columns.price'), dataIndex: 'price', key: 'price', width: 100, align: 'right' as const, render: (value?: number) => numberValue(value, 3) },
    { title: t('stockInformation.columns.changeAmount'), dataIndex: 'changeAmount', key: 'changeAmount', width: 100, align: 'right' as const, render: (value?: number) => numberValue(value, 3) },
    { title: t('stockInformation.columns.changePercent'), dataIndex: 'changePercent', key: 'changePercent', width: 100, align: 'right' as const, render: (value?: number) => formatPercent(value) },
    {
      title: t('stockInformation.columns.marketCap'),
      dataIndex: 'totalMarketValue',
      key: 'totalMarketValue',
      width: 120,
      align: 'right' as const,
      render: (value?: number) => <Tooltip title={formatFullAmount(value)}>{formatAmount(value)}</Tooltip>,
    },
    { title: t('stockInformation.columns.turnover'), dataIndex: 'turnoverRate', key: 'turnoverRate', width: 100, align: 'right' as const, render: formatPercent },
    { title: t('stockInformation.columns.volumeRatio'), dataIndex: 'volumeRatio', key: 'volumeRatio', width: 90, align: 'right' as const, render: (value?: number) => numberValue(value) },
    { title: t('stockInformation.columns.category'), dataIndex: 'industryCode', key: 'industryCode', width: 170, render: formatSecurityCategory },
    { title: t('stockInformation.columns.listedDate'), dataIndex: 'listedDate', key: 'listedDate', width: 120, render: (value?: string) => value ? value.slice(0, 10) : '-' },
    { title: t('stockInformation.columns.updatedAt'), dataIndex: 'updateTime', key: 'updateTime', width: 180, render: formatDateTime },
  ];

  const tableActions = useMemo(() => (
    <Space wrap>
      <Input
        className="w-56"
        prefix={<SearchOutlined />}
        placeholder={t('stockInformation.searchPlaceholder')}
        value={keyword}
        allowClear
        onChange={(event) => setKeyword(event.target.value)}
        onPressEnter={search}
      />
      <button
        type="button"
        className="inline-flex h-8 items-center gap-1 rounded border border-[var(--border-color)] px-3 text-sm text-[var(--text-primary)] hover:border-blue-500"
        onClick={search}
      >
        <SearchOutlined />{t('common.search')}
      </button>
    </Space>
  ), [appliedKeyword, current, keyword, loadStocks, pageSize, search]);

  usePageHeader(t('stockInformation.subtitle'));

  return (
    <div className="app-page">
      <Card className="surface-card min-w-0">
        <div className="mb-4 flex justify-start overflow-x-auto pb-1">
          {tableActions}
        </div>
        <Table
          rowKey={(record) => record.id || record.code}
          columns={columns}
          dataSource={stocks}
          loading={loading}
          locale={{ emptyText: <Empty description={t('stockInformation.empty')} /> }}
          scroll={{ x: 'max-content' }}
          pagination={false}
        />
        <div className="mt-4 overflow-x-auto pb-1">
          <div className="flex min-w-max justify-end">
            <Pagination
              current={current}
              pageSize={pageSize}
              total={total}
              showSizeChanger
              showQuickJumper
              responsive
              pageSizeOptions={[10, 20, 50, 100]}
              showTotal={(value, range) => t('common.pagination', { start: range[0], end: range[1], total: value })}
              onChange={(page, size) => {
                setCurrent(size === pageSize ? page : 1);
                setPageSize(size);
              }}
            />
          </div>
        </div>
      </Card>
    </div>
  );
};

export default StockInformation;
// AI_GENERATE_END---------------
