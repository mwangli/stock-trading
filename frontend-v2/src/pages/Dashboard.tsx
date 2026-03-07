import React from 'react';
import ReactECharts from 'echarts-for-react';
import { Card, Row, Col, Statistic, Table, Tag } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined, RocketOutlined, DollarOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

const Dashboard: React.FC = () => {
  // Mock Data for Charts
  const { t } = useTranslation();
  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
      axisLine: { lineStyle: { color: '#888' } }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
      axisLine: { lineStyle: { color: '#888' } }
    },
    series: [
      {
        name: t('dashboard.chart.portfolioValue'),
        type: 'line',
        stack: 'Total',
        smooth: true,
        lineStyle: { width: 3, color: '#00e396' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: 'rgba(0, 227, 150, 0.5)' }, { offset: 1, color: 'rgba(0, 227, 150, 0)' }]
          }
        },
        emphasis: { focus: 'series' },
        data: [120, 132, 101, 134, 90, 230, 210]
      },
      {
        name: t('dashboard.chart.benchmark'),
        type: 'line',
        stack: 'Total',
        smooth: true,
        lineStyle: { width: 2, type: 'dashed', color: '#775dd0' },
        data: [220, 182, 191, 234, 290, 330, 310]
      }
    ]
  };

  // Mock Data for Table
  const columns = [
    { title: t('dashboard.table.symbol'), dataIndex: 'symbol', key: 'symbol', render: (text: string) => <span className="text-[#00e396] font-bold">{text}</span> },
    { title: t('dashboard.table.type'), dataIndex: 'type', key: 'type', render: (type: string) => <Tag color={type === 'BUY' ? 'success' : 'error'}>{t(`dashboard.type.${type.toLowerCase()}`)}</Tag> },
    { title: t('dashboard.table.price'), dataIndex: 'price', key: 'price' },
    { title: t('dashboard.table.quantity'), dataIndex: 'quantity', key: 'quantity' },
    { title: t('dashboard.table.status'), dataIndex: 'status', key: 'status', render: (status: string) => <span className="text-gray-400">{t(`dashboard.status.${status.toLowerCase()}`)}</span> },
  ];

  const data = [
    { key: '1', symbol: 'AAPL', type: 'BUY', price: '$150.23', quantity: 100, status: 'Completed' },
    { key: '2', symbol: 'TSLA', type: 'SELL', price: '$890.11', quantity: 50, status: 'Completed' },
    { key: '3', symbol: 'NVDA', type: 'BUY', price: '$420.69', quantity: 200, status: 'Pending' },
  ];

  return (
    <div className="space-y-6">
      {/* Stats Row */}
      <Row gutter={[24, 24]}>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} className="glass neon-border h-full">
            <Statistic 
              title={<span className="text-gray-400">{t('dashboard.stats.totalBalance')}</span>}
              value={112893.50} 
              precision={2} 
              prefix={<DollarOutlined className="text-[#00e396]" />} 
              valueStyle={{ color: '#fff', fontSize: '24px', fontWeight: 'bold' }}
            />
            <div className="mt-2 text-[#00e396] text-sm flex items-center">
              <ArrowUpOutlined className="mr-1" /> +12.5% {t('dashboard.stats.today')}
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} className="glass h-full">
            <Statistic 
              title={<span className="text-gray-400">{t('dashboard.stats.activePositions')}</span>}
              value={12} 
              prefix={<RocketOutlined className="text-[#775dd0]" />}
              valueStyle={{ color: '#fff', fontSize: '24px', fontWeight: 'bold' }}
            />
             <div className="mt-2 text-gray-500 text-sm">3 {t('dashboard.stats.pendingOrders')}</div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
           <Card bordered={false} className="glass h-full">
            <Statistic 
              title={<span className="text-gray-400">{t('dashboard.stats.dailyPnL')}</span>}
              value={2450.20} 
              precision={2}
              prefix={<ArrowUpOutlined className="text-[#00e396]" />}
              valueStyle={{ color: '#00e396', fontSize: '24px', fontWeight: 'bold' }}
            />
             <div className="mt-2 text-gray-500 text-sm">{t('dashboard.stats.vsYesterday')}</div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
           <Card bordered={false} className="glass h-full">
            <Statistic 
              title={<span className="text-gray-400">{t('dashboard.stats.riskLevel')}</span>}
              value={t('dashboard.stats.risk.moderate')}
              valueStyle={{ color: '#ffbd2e', fontSize: '24px', fontWeight: 'bold' }}
            />
             <div className="mt-2 text-[#ff4560] text-sm flex items-center">
              <ArrowDownOutlined className="mr-1" /> -2% {t('dashboard.stats.volatility')}
            </div>
          </Card>
        </Col>
      </Row>

      {/* Main Chart */}
      <Card bordered={false} className="glass p-4" title={<span className="text-white text-lg font-bold">{t('dashboard.chart.performance')}</span>}>
         <ReactECharts option={option} style={{ height: '400px', width: '100%' }} theme="dark" />
      </Card>

      {/* Recent Transactions */}
      <Card bordered={false} className="glass p-4" title={<span className="text-white text-lg font-bold">{t('dashboard.table.recentActivity')}</span>}>
        <Table 
          columns={columns} 
          dataSource={data} 
          pagination={false} 
          rowClassName={() => 'bg-transparent text-gray-300 hover:bg-white/5'}
          // Ant Design table styling overrides are in global css, but we can add specific ones
        />
      </Card>
    </div>
  );
};

export default Dashboard;
