// AI_GENERATE_START --
import './styles.css';
import React, { useMemo, useState } from 'react';
import {
  Card,
  DatePicker,
  Empty,
  Input,
  Segmented,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { SearchOutlined, CheckCircleOutlined, ClockCircleOutlined } from '@ant-design/icons';
import { usePageHeader } from '../../layouts/PageHeaderContext';

const { Text, Title } = Typography;

type DemoKey = 'grouped' | 'expandable' | 'review';

interface DemoTrade {
  key: string;
  stockCode: string;
  stockName: string;
  buyTime: string;
  sellTime?: string;
  buyQuantity: number;
  soldQuantity: number;
  buyPrice: number;
  sellPrice?: number;
  buyAmount: number;
  sellAmount?: number;
  fee: number;
  netProfit?: number;
  returnRate?: number;
  holdingDays?: number;
}

const trades: DemoTrade[] = [
  {
    key: '000659-1', stockCode: '000659', stockName: '珠海中富', buyTime: '2024-12-04 10:04', sellTime: '2024-12-05 09:31',
    buyQuantity: 100, soldQuantity: 100, buyPrice: 3.43, sellPrice: 3.39, buyAmount: 343, sellAmount: 339,
    fee: 10, netProfit: -14, returnRate: -0.0402, holdingDays: 1,
  },
  {
    key: '000564-1', stockCode: '000564', stockName: '供销大集', buyTime: '2024-12-03 10:04', sellTime: '2024-12-04 09:31',
    buyQuantity: 100, soldQuantity: 100, buyPrice: 3.69, sellPrice: 3.40, buyAmount: 369, sellAmount: 340,
    fee: 10, netProfit: -39, returnRate: -0.1043, holdingDays: 1,
  },
  {
    key: '002329-1', stockCode: '002329', stockName: '皇氏集团', buyTime: '2024-11-29 11:07', sellTime: '2024-12-02 09:31',
    buyQuantity: 100, soldQuantity: 100, buyPrice: 4.43, sellPrice: 4.13, buyAmount: 443, sellAmount: 413,
    fee: 10, netProfit: -40, returnRate: -0.0903, holdingDays: 3,
  },
  {
    key: '600979-1', stockCode: '600979', stockName: '广安爱众', buyTime: '2024-11-26 10:03',
    buyQuantity: 100, soldQuantity: 0, buyPrice: 4.78, buyAmount: 478, fee: 10,
  },
];

const money = (value?: number) => value == null ? '-' : `¥${value.toFixed(2)}`;
const percent = (value?: number) => value == null ? '-' : `${(value * 100).toFixed(2)}%`;
const profitClass = (value?: number) => value == null ? 'demo-neutral' : value >= 0 ? 'demo-profit' : 'demo-loss';

const stockCell = (record: DemoTrade) => (
  <div className="demo-stock-cell">
    <strong>{record.stockCode}</strong>
    <span>{record.stockName}</span>
  </div>
);

const statusTag = (record: DemoTrade) => record.soldQuantity > 0
  ? <Tag icon={<CheckCircleOutlined />} color="success">已完成</Tag>
  : <Tag icon={<ClockCircleOutlined />} color="processing">持仓中</Tag>;

const GroupedDemo: React.FC = () => {
  const columns: ColumnsType<DemoTrade> = [
    { title: '股票', key: 'stock', fixed: 'left', width: 170, render: (_, record) => stockCell(record) },
    {
      title: '买入信息', children: [
        { title: '时间', dataIndex: 'buyTime', key: 'buyTime', width: 150 },
        { title: '数量', dataIndex: 'buyQuantity', key: 'buyQuantity', width: 82, className: 'demo-number-cell' },
        { title: '均价', dataIndex: 'buyPrice', key: 'buyPrice', width: 90, className: 'demo-number-cell', render: money },
        { title: '金额', dataIndex: 'buyAmount', key: 'buyAmount', width: 100, className: 'demo-number-cell', render: money },
      ],
    },
    {
      title: '卖出信息', children: [
        { title: '时间', dataIndex: 'sellTime', key: 'sellTime', width: 150, render: (value?: string) => value || '-' },
        { title: '数量', dataIndex: 'soldQuantity', key: 'soldQuantity', width: 82, className: 'demo-number-cell' },
        { title: '均价', dataIndex: 'sellPrice', key: 'sellPrice', width: 90, className: 'demo-number-cell', render: money },
        { title: '金额', dataIndex: 'sellAmount', key: 'sellAmount', width: 100, className: 'demo-number-cell', render: money },
      ],
    },
    { title: '手续费', dataIndex: 'fee', key: 'fee', width: 92, className: 'demo-number-cell', render: money },
    { title: '净收益', dataIndex: 'netProfit', key: 'netProfit', width: 105, className: 'demo-number-cell', render: (value?: number) => <strong className={profitClass(value)}>{money(value)}</strong> },
    { title: '收益率', dataIndex: 'returnRate', key: 'returnRate', width: 100, className: 'demo-number-cell', render: (value?: number) => <strong className={profitClass(value)}>{percent(value)}</strong> },
    { title: '状态', key: 'status', width: 100, render: (_, record) => statusTag(record) },
  ];

  return <Table<DemoTrade> rowKey="key" className="demo-table grouped-table" columns={columns} dataSource={trades} scroll={{ x: 1500 }} pagination={false} />;
};

const ExpandableDemo: React.FC = () => {
  const columns: ColumnsType<DemoTrade> = [
    { title: '', key: 'expand', width: 44 },
    { title: '股票', key: 'stock', width: 190, render: (_, record) => stockCell(record) },
    { title: '买入时间', dataIndex: 'buyTime', key: 'buyTime', width: 160 },
    { title: '卖出时间', dataIndex: 'sellTime', key: 'sellTime', width: 160, render: (value?: string) => value || <Text type="secondary">待卖出</Text> },
    { title: '净收益', dataIndex: 'netProfit', key: 'netProfit', width: 120, className: 'demo-number-cell', render: (value?: number) => <strong className={profitClass(value)}>{money(value)}</strong> },
    { title: '收益率', dataIndex: 'returnRate', key: 'returnRate', width: 110, className: 'demo-number-cell', render: (value?: number) => <strong className={profitClass(value)}>{percent(value)}</strong> },
    { title: '持仓时间', dataIndex: 'holdingDays', key: 'holdingDays', width: 100, render: (value?: number) => value == null ? '-' : `${value} 天` },
    { title: '状态', key: 'status', width: 110, render: (_, record) => statusTag(record) },
  ];

  return (
    <Table<DemoTrade>
      rowKey="key"
      className="demo-table expandable-table"
      columns={columns}
      dataSource={trades}
      scroll={{ x: 1000 }}
      pagination={false}
      expandable={{
        defaultExpandAllRows: true,
        expandedRowRender: (record) => (
          <div className="trade-detail-grid">
            <div><span>买入数量</span><strong>{record.buyQuantity}</strong></div>
            <div><span>卖出数量</span><strong>{record.soldQuantity || '-'}</strong></div>
            <div><span>买入均价</span><strong>{money(record.buyPrice)}</strong></div>
            <div><span>卖出均价</span><strong>{money(record.sellPrice)}</strong></div>
            <div><span>买入金额</span><strong>{money(record.buyAmount)}</strong></div>
            <div><span>卖出金额</span><strong>{money(record.sellAmount)}</strong></div>
            <div><span>手续费</span><strong>{money(record.fee)}</strong></div>
          </div>
        ),
      }}
    />
  );
};

const ReviewDemo: React.FC = () => (
  <div className="review-list">
    {trades.map((record) => (
      <article className="review-trade" key={record.key}>
        <div className="review-trade-head">
          {stockCell(record)}
          <div className="review-status">{statusTag(record)}</div>
        </div>
        <div className="review-trade-flow">
          <div className="review-leg">
            <span>买入</span>
            <strong>{money(record.buyPrice)} × {record.buyQuantity}</strong>
            <small>{record.buyTime}</small>
          </div>
          <div className="review-arrow">→</div>
          <div className="review-leg review-leg-sell">
            <span>卖出</span>
            <strong>{money(record.sellPrice)} × {record.soldQuantity || '-'}</strong>
            <small>{record.sellTime || '等待卖出'}</small>
          </div>
        </div>
        <div className="review-trade-summary">
          <div><span>净收益</span><strong className={profitClass(record.netProfit)}>{money(record.netProfit)}</strong></div>
          <div><span>收益率</span><strong className={profitClass(record.returnRate)}>{percent(record.returnRate)}</strong></div>
          <div><span>持仓时间</span><strong>{record.holdingDays == null ? '-' : `${record.holdingDays} 天`}</strong></div>
          <div><span>手续费</span><strong>{money(record.fee)}</strong></div>
        </div>
      </article>
    ))}
  </div>
);

const demoOptions: Array<{ value: DemoKey; label: string }> = [
  { value: 'grouped', label: '方案 A · 分组表头' },
  { value: 'expandable', label: '方案 B · 展开详情' },
  { value: 'review', label: '方案 C · 复盘卡片' },
];

const TradeRecordDemos: React.FC = () => {
  const [activeDemo, setActiveDemo] = useState<DemoKey>('grouped');
  const activeDescription = useMemo(() => ({
    grouped: '保留完整字段，通过买入信息、卖出信息和收益结果分组降低阅读成本。',
    expandable: '主表只展示决策所需字段，买入金额、手续费等辅助信息放入行详情。',
    review: '把每笔交易变成一条复盘记录，适合快速浏览盈亏和交易过程。',
  }[activeDemo]), [activeDemo]);

  usePageHeader('交易记录表格方案对比');

  return (
    <div className="app-page trade-record-demo-page">
      <Card className="surface-card trade-demo-intro">
        <div>
          <Text className="demo-eyebrow">TRADE RECORD UI EXPLORER</Text>
          <Title level={3}>交易记录页面方案对比</Title>
          <Text type="secondary">同一组数据、三种信息层级，先选交互结构，再迁移到正式页面。</Text>
        </div>
        <div className="demo-filter-preview">
          <Input prefix={<SearchOutlined />} value="股票代码、名称或委托编号" readOnly />
          <DatePicker.RangePicker value={[null, null]} placeholder={['开始日期', '结束日期']} disabled />
        </div>
      </Card>

      <Card className="surface-card demo-selector-card">
        <Segmented block value={activeDemo} options={demoOptions} onChange={(value) => setActiveDemo(value as DemoKey)} />
        <div className="demo-selection-copy">
          <div><strong>{demoOptions.find((item) => item.value === activeDemo)?.label}</strong><span>{activeDescription}</span></div>
          <Tag color="blue">仅展示层 Demo</Tag>
        </div>
      </Card>

      <Card className="surface-card demo-preview-card" title="预览">
        {activeDemo === 'grouped' && <GroupedDemo />}
        {activeDemo === 'expandable' && <ExpandableDemo />}
        {activeDemo === 'review' && <ReviewDemo />}
        {trades.length === 0 && <Empty description="暂无示例数据" />}
      </Card>
    </div>
  );
};

export default TradeRecordDemos;
// AI_GENERATE_END --
