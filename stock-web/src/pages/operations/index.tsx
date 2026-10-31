// AI_GENERATE_START -
import { Alert, Badge, Space, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { getData, type DataRecord } from '../../api/client'
import { ContractAlert, LoadState, PageHeader, RecordTable } from '../../components/Page'
import { useRequest } from '../../hooks/useRequest'

interface OperationStatus { contract: DataRecord; taskLogAvailable: boolean; taskLog: string; runs: DataRecord[] }

export default function OperationsPage() {
  const status = useRequest(() => getData<OperationStatus>('/operations/status'), [])
  const [logs, setLogs] = useState<string[]>([])
  useEffect(() => {
    getData<string[]>('/operations/logs', { lines: 300 }).then(setLogs).catch(() => setLogs([]))
    const source = new EventSource('/api/operations/logs/stream', { withCredentials: true })
    source.addEventListener('log', (event) => setLogs((current) => [...current.slice(-499), (event as MessageEvent).data]))
    return () => source.close()
  }, [])
  return <div className="page">
    <PageHeader title="运行状态与日志" description="Cron 任务状态、失败原因和固定日志文件，只读展示" onReload={status.reload} />
    <LoadState loading={status.loading} error={status.error}>
      <ContractAlert contract={status.data?.contract} />
      <section className="status-strip"><Space><Badge status={status.data?.taskLogAvailable ? 'success' : 'warning'} /><strong>任务日志</strong><Typography.Text type="secondary">{status.data?.taskLog}</Typography.Text></Space></section>
      {!status.data?.taskLogAvailable && <Alert type="warning" showIcon message="任务日志文件不可用" />}
      <RecordTable title="最近任务" rows={status.data?.runs || []} maxColumns={12} />
      <section className="panel"><h3>实时日志</h3><pre className="log-view">{logs.join('\n') || '暂无日志'}</pre></section>
    </LoadState>
  </div>
}
// AI_GENERATE_END -
