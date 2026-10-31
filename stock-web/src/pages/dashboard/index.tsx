// AI_GENERATE_START---------------------------
import { Alert, Statistic } from 'antd'
import ReactECharts from 'echarts-for-react'
import { getData, type DataRecord } from '../../api/client'
import { ContractAlert, LoadState, PageHeader, RecordTable } from '../../components/Page'
import { useRequest } from '../../hooks/useRequest'
import { useMediaQuery } from '../../hooks/useMediaQuery'

interface Overview extends DataRecord {
  contract: DataRecord
  account: DataRecord
  positions: DataRecord[]
  performance: DataRecord[]
  recentOrders: DataRecord[]
  recentTasks: DataRecord[]
}

const number = (value: unknown) => Number(value || 0)

export default function DashboardPage() {
  const mobile = useMediaQuery('(max-width: 480px)')
  const request = useRequest(() => getData<Overview>('/dashboard/overview'), [])
  const data = request.data
  const account = data?.account || {}
  const performance = data?.performance || []
  const chartRows = [...performance].reverse()
  const cumulativeReturns = chartRows.reduce<number[]>((values, row) => {
    const previous = values.at(-1) || 0
    values.push((1 + previous) * (1 + number(row.net_return)) - 1)
    return values
  }, [])
  const cumulativeReturn = cumulativeReturns.at(-1) || 0
  const dailyReturn = number(performance[0]?.net_return)
  const monthlyReturn = performance.slice(0, 22).reduce((value, row) => value * (1 + number(row.net_return)), 1) - 1
  const chart = {
    tooltip: { trigger: 'axis' },
    grid: mobile ? { left: 40, right: 10, top: 22, bottom: 30 } : { left: 48, right: 24, top: 30, bottom: 36 },
    xAxis: { type: 'category', data: chartRows.map((row) => row.business_date) },
    yAxis: { type: 'value', scale: true },
    series: [{ name: '累计收益', type: 'line', smooth: true, showSymbol: false, data: cumulativeReturns }],
  }
  return <div className="page">
    <PageHeader title="概览" description="账户、持仓、绩效与最近运行异常" onReload={request.reload} />
    <LoadState loading={request.loading} error={request.error}>
      <ContractAlert contract={data?.contract} />
      <section className="metric-grid dashboard-metrics">
        <Statistic title="总资产" value={number(account.total_assets ?? account.total_asset)} precision={2} />
        <Statistic title="可用资金" value={number(account.available_cash ?? account.cash_available)} precision={2} />
        <Statistic title="持仓数量" value={data?.positions.length || 0} />
        <Statistic title="当日收益" value={dailyReturn * 100} precision={2} suffix="%" valueStyle={{ color: dailyReturn >= 0 ? 'var(--market-up)' : 'var(--market-down)' }} />
        <Statistic title="近月收益" value={monthlyReturn * 100} precision={2} suffix="%" valueStyle={{ color: monthlyReturn >= 0 ? 'var(--market-up)' : 'var(--market-down)' }} />
        <Statistic title="累计收益" value={cumulativeReturn * 100} precision={2} suffix="%" valueStyle={{ color: cumulativeReturn >= 0 ? 'var(--market-up)' : 'var(--market-down)' }} />
      </section>
      {!data?.recentTasks.length && <Alert type="info" showIcon message="暂无任务运行记录" />}
      <section className="panel"><h3>绩效曲线</h3><ReactECharts option={chart} style={{ height: mobile ? 240 : 300 }} /></section>
      <RecordTable title="当前持仓" rows={data?.positions || []} />
      <div className="two-column"><RecordTable title="最近委托" rows={data?.recentOrders || []} maxColumns={7} /><RecordTable title="最近任务" rows={data?.recentTasks || []} maxColumns={7} /></div>
    </LoadState>
  </div>
}
// AI_GENERATE_END---------------------------
