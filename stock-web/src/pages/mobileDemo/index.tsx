// AI_GENERATE_START ---
import { useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { App, Button, ConfigProvider, Drawer, Input, Segmented, Statistic, Tag, theme } from 'antd'
import {
  BarChartOutlined, BookOutlined, BulbOutlined, DashboardOutlined, DatabaseOutlined,
  EllipsisOutlined, MessageOutlined, MoonOutlined, OrderedListOutlined, SendOutlined, SunOutlined,
} from '@ant-design/icons'
import ReactECharts from 'echarts-for-react'
import { RecordTable } from '../../components/Page'

type DemoView = 'overview' | 'market' | 'trading' | 'agent'

const navItems: Array<{ key: DemoView; label: string; icon: ReactNode }> = [
  { key: 'overview', label: '概览', icon: <DashboardOutlined /> },
  { key: 'market', label: '行情', icon: <BarChartOutlined /> },
  { key: 'trading', label: '交易', icon: <OrderedListOutlined /> },
  { key: 'agent', label: 'Agent', icon: <MessageOutlined /> },
]

const orders = [
  { client_order_id: 'BUY-20261031-001', code: '600519', side: '买入', status: 'SUBMITTED', quantity: 100, price: 1684.32, business_date: '2026-10-31' },
  { client_order_id: 'SELL-20261031-002', code: '000858', side: '卖出', status: 'FILLED', quantity: 200, price: 126.48, business_date: '2026-10-31' },
]

const performanceOption = {
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 10, top: 20, bottom: 28 },
  xAxis: { type: 'category', data: ['10/21', '10/23', '10/25', '10/27', '10/29', '10/31'] },
  yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
  series: [{ name: '累计收益', type: 'line', smooth: true, showSymbol: false, lineStyle: { width: 2 }, data: [0.2, 0.7, 0.4, 1.3, 1.8, 2.4] }],
}

export default function MobileDemoPage() {
  const [view, setView] = useState<DemoView>('overview')
  const [moreOpen, setMoreOpen] = useState(false)
  const [dark, setDark] = useState(false)
  const bars = useMemo(() => Array.from({ length: 24 }, (_, index) => {
    const base = 18 + index * .08
    return [base, base + (index % 3 === 0 ? -.16 : .2), base - .3, base + .36]
  }), [])
  const marketOption = {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 8, top: 22, bottom: 30 },
    xAxis: { type: 'category', data: bars.map((_, index) => `10/${String(index + 1).padStart(2, '0')}`), axisLabel: { hideOverlap: true } },
    yAxis: { type: 'value', scale: true },
    dataZoom: [{ type: 'inside' }],
    series: [{ name: '日 K', type: 'candlestick', data: bars }],
  }
  const title = navItems.find((item) => item.key === view)?.label || '移动端 Demo'

  return <ConfigProvider theme={{ algorithm: dark ? theme.darkAlgorithm : theme.defaultAlgorithm, token: { colorPrimary: '#167b68', borderRadius: 6 } }}>
    <App><main className="mobile-demo-page"><div className="mobile-demo-shell">
      <header className="demo-mobile-header"><div className="mobile-header-title"><img src="/system-mark.svg" alt="" /><div><strong>{title}</strong><span>iPhone 12 静态演示</span></div></div><Tag color="green">Demo</Tag></header>
      <div className="demo-mobile-content">
        {view === 'overview' && <div className="page demo-page"><header className="page-header"><div><h2>账户概览</h2><span className="muted">静态样例，不连接交易数据</span></div></header>
          <section className="metric-grid"><Statistic title="总资产" value={286420.56} precision={2} /><Statistic title="可用资金" value={98240.18} precision={2} /><Statistic title="当日收益" value={1.26} precision={2} suffix="%" /><Statistic title="累计收益" value={8.42} precision={2} suffix="%" /></section>
          <section className="panel"><h3>绩效曲线</h3><ReactECharts option={performanceOption} style={{ height: 230 }} /></section>
          <RecordTable title="最近委托" rows={orders} forceCards maxColumns={7} />
        </div>}
        {view === 'market' && <div className="page demo-page"><header className="page-header"><div><h2>600519 贵州茅台</h2><span className="muted">日 K · 数据日期 2026-10-31</span></div><Segmented size="small" options={['日 K', '5 分钟']} defaultValue="日 K" /></header>
          <section className="metric-grid market-metrics"><Statistic title="最新价" value={1684.32} precision={2} /><Statistic title="涨跌幅" value={1.18} precision={2} suffix="%" valueStyle={{ color: 'var(--market-up)' }} /><Statistic title="最高" value={1698.6} precision={2} /><Statistic title="最低" value={1656.2} precision={2} /></section>
          <section className="panel chart-panel"><ReactECharts option={marketOption} style={{ height: 360 }} /></section>
        </div>}
        {view === 'trading' && <div className="page demo-page"><header className="page-header"><div><h2>交易记录</h2><span className="muted">委托、成交和幂等订单号</span></div></header><RecordTable rows={orders} forceCards maxColumns={8} /></div>}
        {view === 'agent' && <div className="page demo-page agent-page"><header className="page-header"><div><h2>Agent</h2><span className="muted">只读事实问答与证据来源</span></div></header><section className="agent-chat demo-agent-chat"><div className="message-list"><div className="message message-user"><strong>你</strong><p>今天的账户收益和最近异常是什么？</p></div><div className="message message-assistant"><strong>Agent</strong><Tag color="green">只读证据</Tag><p>当日收益为 1.26%。最近一次任务已完成，暂无失败任务。</p><span className="evidence muted">来源：console_v_performance_daily、console_v_task_runs</span></div></div><div className="chat-input"><Input.TextArea autoSize={{ minRows: 2, maxRows: 3 }} placeholder="询问账户、交易、模型或任务" /><Button type="primary" aria-label="发送" icon={<SendOutlined />} /></div></section></div>}
      </div>
      <nav className="demo-mobile-tabbar" aria-label="Demo 导航">{navItems.map((item) => <button type="button" className={view === item.key ? 'mobile-tabbar-item active' : 'mobile-tabbar-item'} key={item.key} onClick={() => setView(item.key)}>{item.icon}<span>{item.label}</span></button>)}<button type="button" className="mobile-tabbar-item" onClick={() => setMoreOpen(true)}><EllipsisOutlined /><span>更多</span></button></nav>
    </div></main>
    <Drawer title="更多功能" placement="bottom" height="auto" open={moreOpen} onClose={() => setMoreOpen(false)} rootClassName="mobile-more-drawer"><div className="mobile-more-grid"><button type="button" className="mobile-more-item" disabled><DatabaseOutlined /><span>模型与预测</span></button><button type="button" className="mobile-more-item" disabled><BulbOutlined /><span>运行状态</span></button><button type="button" className="mobile-more-item" disabled><BookOutlined /><span>文档工作台</span></button><button type="button" className="mobile-more-item" onClick={() => setDark((value) => !value)}>{dark ? <SunOutlined /> : <MoonOutlined />}<span>{dark ? '浅色主题' : '深色主题'}</span></button></div></Drawer>
    </App>
  </ConfigProvider>
}
// AI_GENERATE_END ---
