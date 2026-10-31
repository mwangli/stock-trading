// AI_GENERATE_START -
import { Tabs } from 'antd'
import { getData, type DataRecord } from '../../api/client'
import { LoadState, PageHeader, RecordTable } from '../../components/Page'
import { useRequest } from '../../hooks/useRequest'

export default function TradingPage() {
  const request = useRequest(async () => {
    const [orders, fills, positions] = await Promise.all([
      getData<DataRecord[]>('/trading/orders', { limit: 300 }),
      getData<DataRecord[]>('/trading/fills', { limit: 300 }),
      getData<DataRecord[]>('/trading/positions', { limit: 300 }),
    ])
    return { orders, fills, positions }
  }, [])
  return <div className="page">
    <PageHeader title="交易记录" description="委托、成交、持仓与幂等订单号，只读展示" onReload={request.reload} />
    <LoadState loading={request.loading} error={request.error}>
      <Tabs items={[
        { key: 'orders', label: `委托 ${request.data?.orders.length || 0}`, children: <RecordTable rows={request.data?.orders || []} maxColumns={12} /> },
        { key: 'fills', label: `成交 ${request.data?.fills.length || 0}`, children: <RecordTable rows={request.data?.fills || []} maxColumns={12} /> },
        { key: 'positions', label: `持仓 ${request.data?.positions.length || 0}`, children: <RecordTable rows={request.data?.positions || []} maxColumns={12} /> },
      ]} />
    </LoadState>
  </div>
}
// AI_GENERATE_END -
