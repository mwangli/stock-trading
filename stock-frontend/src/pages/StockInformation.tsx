// AI_GENERATE_START ------
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Card, Empty, Input, Pagination, Space, Table, Tooltip, message } from 'antd';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import request from '../utils/request';
import { formatDateTime } from '../utils/dateTime';
import { usePageHeader } from '../layouts/PageHeaderContext';

interface StockInfo {
  id: number;
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
  updateTime?: string;
}

interface StockInfoResponse {
  success: boolean;
  message?: string;
  data: StockInfo[];
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
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [stocks, setStocks] = useState<StockInfo[]>([]);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
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
      }) as StockInfoResponse;
      if (!response.success) {
        throw new Error(response.message || '股票信息读取失败');
      }
      setStocks(response.data ?? []);
      setTotal(response.total ?? 0);
      setCurrent(response.current ?? page);
      setPageSize(response.pageSize ?? size);
    } catch (cause) {
      setStocks([]);
      setTotal(0);
      message.error(cause instanceof Error ? cause.message : '股票信息读取失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadStocks(current, pageSize, appliedKeyword);
  }, [appliedKeyword, current, loadStocks, pageSize]);

  const search = useCallback(() => {
    setCurrent(1);
    setAppliedKeyword(keyword.trim());
  }, [keyword]);

  const formatMarket = (value?: string) => value ? marketLabels[value.toUpperCase()] ?? value : '-';
  const formatAmount = (value?: number) => value == null ? '-' : `¥${compactAmountFormatter.format(Number(value))}`;
  const formatFullAmount = (value?: number) => value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;
  const formatPercent = (value?: number) => value == null ? '-' : `${Number(value).toFixed(2)}%`;
  const formatSecurityCategory = (value?: number) => value == null ? '-' : securityCategoryLabels[value] ?? `其他类别（${value}）`;

  const columns = [
    { title: '股票代码', dataIndex: 'code', key: 'code', width: 110, fixed: 'left' as const },
    { title: '股票名称', dataIndex: 'name', key: 'name', width: 140, fixed: 'left' as const },
    { title: '市场', dataIndex: 'market', key: 'market', width: 90, render: formatMarket },
    { title: '最新价', dataIndex: 'price', key: 'price', width: 100, align: 'right' as const, render: (value?: number) => numberValue(value, 3) },
    { title: '涨跌额', dataIndex: 'changeAmount', key: 'changeAmount', width: 100, align: 'right' as const, render: (value?: number) => numberValue(value, 3) },
    { title: '涨跌幅', dataIndex: 'changePercent', key: 'changePercent', width: 100, align: 'right' as const, render: (value?: number) => formatPercent(value) },
    {
      title: '总市值',
      dataIndex: 'totalMarketValue',
      key: 'totalMarketValue',
      width: 120,
      align: 'right' as const,
      render: (value?: number) => <Tooltip title={formatFullAmount(value)}>{formatAmount(value)}</Tooltip>,
    },
    { title: '换手率', dataIndex: 'turnoverRate', key: 'turnoverRate', width: 100, align: 'right' as const, render: formatPercent },
    { title: '量比', dataIndex: 'volumeRatio', key: 'volumeRatio', width: 90, align: 'right' as const, render: (value?: number) => numberValue(value) },
    { title: '证券类别', dataIndex: 'industryCode', key: 'industryCode', width: 170, render: formatSecurityCategory },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180, render: formatDateTime },
  ];

  const tableActions = useMemo(() => (
    <Space wrap>
      <Input
        className="w-56"
        prefix={<SearchOutlined />}
        placeholder="搜索股票代码或名称"
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
        <SearchOutlined />查询
      </button>
      <button
        type="button"
        className="inline-flex h-8 items-center gap-1 rounded border border-[var(--border-color)] px-3 text-sm text-[var(--text-primary)] hover:border-blue-500"
        onClick={() => void loadStocks(current, pageSize, appliedKeyword)}
      >
        <ReloadOutlined />刷新
      </button>
    </Space>
  ), [appliedKeyword, current, keyword, loadStocks, pageSize, search]);

  usePageHeader('展示系统已维护的股票基础信息');

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
          locale={{ emptyText: <Empty description="暂无股票基础信息" /> }}
          scroll={{ x: 1250, y: 'calc(100vh - 330px)' }}
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
              showTotal={(value, range) => `第 ${range[0]}-${range[1]} 条，共 ${value} 条`}
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
// AI_GENERATE_END ------
