// AI_GENERATE_START---------------------------------------
import { Button, Drawer, Empty, Input, Segmented, Statistic } from 'antd'
import { BarChartOutlined, UnorderedListOutlined } from '@ant-design/icons'
import ReactECharts from 'echarts-for-react'
import { useEffect, useMemo, useState } from 'react'
import { getData, type DataRecord } from '../../api/client'
import { LoadState, PageHeader } from '../../components/Page'
import { useRequest } from '../../hooks/useRequest'
import { useMediaQuery } from '../../hooks/useMediaQuery'

const numeric = (value: unknown) => Number(value || 0)
const price = (value: unknown) => Number.isFinite(Number(value)) ? Number(value).toFixed(2) : '-'

export default function MarketPage() {
  const mobile = useMediaQuery('(max-width: 480px)')
  const [keyword, setKeyword] = useState('')
  const [selected, setSelected] = useState('')
  const [interval, setInterval] = useState<'day' | '5m'>('day')
  const [directoryOpen, setDirectoryOpen] = useState(false)
  const instruments = useRequest(() => getData<DataRecord[]>('/market/instruments', { keyword, limit: 300 }), [keyword])
  const statistics = useRequest(() => getData<DataRecord>('/market/statistics'), [])
  const bars = useRequest(() => selected ? getData<DataRecord[]>('/market/bars', { code: selected, interval, limit: interval === 'day' ? 240 : 600 }) : Promise.resolve([]), [selected, interval])

  useEffect(() => {
    if (!selected && instruments.data?.[0]?.code) setSelected(String(instruments.data[0].code))
  }, [instruments.data, selected])

  const rows = useMemo(() => [...(bars.data || [])].reverse(), [bars.data])
  const selectedInstrument = instruments.data?.find((item) => String(item.code) === selected)
  const latest = rows.at(-1)
  const latestOpen = numeric(latest?.open)
  const latestClose = numeric(latest?.close)
  const latestChange = latestOpen ? (latestClose - latestOpen) / latestOpen * 100 : 0
  const dates = rows.map((row) => interval === 'day' ? row.trade_date : row.bar_end)
  const candleData = rows.map((row) => [row.open, row.close, row.low, row.high])
  const volumes = rows.map((row) => numeric(row.volume ?? row.vol))
  const volumeColors = rows.map((row) => numeric(row.close) >= numeric(row.open) ? '#cf3f36' : '#138a72')

  const option = {
    animation: false,
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, borderWidth: 0, textStyle: { fontSize: 12 } },
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    grid: mobile
      ? [{ left: 44, right: 10, top: 20, height: '64%' }, { left: 44, right: 10, top: '75%', height: '14%' }]
      : [{ left: 58, right: 22, top: 24, height: '65%' }, { left: 58, right: 22, top: '76%', height: '13%' }],
    xAxis: [
      { type: 'category', data: dates, boundaryGap: true, axisLine: { lineStyle: { color: '#7f8a94' } }, axisLabel: { show: false }, axisTick: { show: false } },
      { type: 'category', gridIndex: 1, data: dates, boundaryGap: true, axisLine: { lineStyle: { color: '#7f8a94' } }, axisLabel: { hideOverlap: true, fontSize: 11 }, axisTick: { show: false } },
    ],
    yAxis: [
      { scale: true, splitNumber: 5, axisLabel: { fontSize: 11 }, splitLine: { lineStyle: { color: 'rgba(127,138,148,.16)' } } },
      { scale: true, gridIndex: 1, splitNumber: 2, axisLabel: { show: false }, axisLine: { show: false }, splitLine: { show: false } },
    ],
    dataZoom: mobile
      ? [{ type: 'inside', xAxisIndex: [0, 1], start: 45, end: 100 }]
      : [{ type: 'inside', xAxisIndex: [0, 1], start: 30, end: 100 }, { type: 'slider', xAxisIndex: [0, 1], height: 18, bottom: 2, borderColor: 'transparent' }],
    series: [
      {
        name: interval === 'day' ? '日 K' : '5 分钟',
        type: 'candlestick',
        data: candleData,
        itemStyle: { color: '#cf3f36', color0: '#138a72', borderColor: '#cf3f36', borderColor0: '#138a72' },
      },
      {
        name: '成交量', type: 'bar', xAxisIndex: 1, yAxisIndex: 1, data: volumes,
        itemStyle: { color: (params: { dataIndex: number }) => volumeColors[params.dataIndex], opacity: 0.55 },
      },
    ],
  }

  const chooseInstrument = (code: string) => {
    setSelected(code)
    setDirectoryOpen(false)
  }

  const instrumentList = <LoadState loading={instruments.loading} error={instruments.error}>
    {(instruments.data || []).length ? <div className="instrument-list" role="list">
      {(instruments.data || []).map((item) => {
        const code = String(item.code)
        return <button
          type="button"
          className={selected === code ? 'instrument-row is-selected' : 'instrument-row'}
          aria-pressed={selected === code}
          key={code}
          onClick={() => chooseInstrument(code)}
        >
          <span className="instrument-identity"><strong>{code}</strong><span>{String(item.name || '未命名')}</span></span>
          <span className="instrument-market">{String(item.market || '-')}</span>
        </button>
      })}
    </div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无股票目录" />}
  </LoadState>

  return <div className="page market-page">
    <PageHeader
      title="行情"
      description="A 股股票目录、日 K 与已有分钟行情"
      onReload={() => { instruments.reload(); statistics.reload(); bars.reload() }}
      actions={mobile && <Button icon={<UnorderedListOutlined />} onClick={() => setDirectoryOpen(true)}>选择股票</Button>}
    />
    <section className="metric-grid market-metrics" aria-label="市场统计">
      <Statistic title="数据日期" value={String(statistics.data?.asOfDate || '-')} />
      <Statistic title="上涨" value={Number(statistics.data?.advances || 0)} valueStyle={{ color: 'var(--market-up)' }} />
      <Statistic title="下跌" value={Number(statistics.data?.declines || 0)} valueStyle={{ color: 'var(--market-down)' }} />
      <Statistic title="平盘" value={Number(statistics.data?.unchanged || 0)} />
    </section>
    <div className="market-layout market-workspace">
      {!mobile && <aside className="instrument-panel">
        <div className="instrument-panel-heading"><div><strong>股票目录</strong><span>{instruments.data?.length || 0} 条</span></div></div>
        <Input.Search allowClear placeholder="搜索代码或名称" onSearch={setKeyword} />
        {instrumentList}
      </aside>}
      <section className="chart-panel">
        <div className="chart-toolbar">
          <div className="market-title">
            <div><h2>{selectedInstrument?.name ? String(selectedInstrument.name) : selected || '选择股票'}</h2>{selected && <span>{selected}</span>}</div>
            {latest && <div className="market-quote" aria-label="最新价格摘要">
              <strong className={latestChange >= 0 ? 'is-up' : 'is-down'}>{price(latest.close)}</strong>
              <span className={latestChange >= 0 ? 'is-up' : 'is-down'}>{latestChange >= 0 ? '+' : ''}{latestChange.toFixed(2)}%</span>
            </div>}
          </div>
          <Segmented value={interval} options={[{ label: '日 K', value: 'day' }, { label: '5 分钟', value: '5m' }]} onChange={(value) => setInterval(value as 'day' | '5m')} />
        </div>
        {latest && <div className="ohlc-strip">
          <span>开 <strong>{price(latest.open)}</strong></span>
          <span>高 <strong>{price(latest.high)}</strong></span>
          <span>低 <strong>{price(latest.low)}</strong></span>
          <span>收 <strong>{price(latest.close)}</strong></span>
          <span>量 <strong>{numeric(latest.volume ?? latest.vol).toLocaleString()}</strong></span>
        </div>}
        <div className="chart-stage">
          <LoadState loading={bars.loading} error={bars.error}>
            {rows.length ? <ReactECharts option={option} style={{ height: mobile ? 390 : 540 }} notMerge /> : <div className="empty-chart">
              <BarChartOutlined aria-hidden="true" />
              <strong>{selected ? '暂无 K 线数据' : '等待选择股票'}</strong>
              <span>{selected ? `${selected} 当前没有可展示的 ${interval === 'day' ? '日线' : '5 分钟'}数据` : '股票目录加载后将自动选择首只股票'}</span>
            </div>}
          </LoadState>
        </div>
      </section>
    </div>
    <Drawer title="选择股票" placement="bottom" height="78dvh" open={directoryOpen} onClose={() => setDirectoryOpen(false)} rootClassName="page-drawer">
      <div className="drawer-search"><Input.Search allowClear placeholder="搜索代码或名称" onSearch={setKeyword} /></div>
      <div className="drawer-list instrument-panel">{instrumentList}</div>
    </Drawer>
  </div>
}
// AI_GENERATE_END---------------------------------------
