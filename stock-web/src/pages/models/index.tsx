// AI_GENERATE_START -
import { Tabs } from 'antd'
import { getData, type DataRecord } from '../../api/client'
import { LoadState, PageHeader, RecordTable } from '../../components/Page'
import { useRequest } from '../../hooks/useRequest'

interface ModelOverview { versions: DataRecord[]; predictions: DataRecord[]; evaluations: DataRecord[]; trainingRequests: DataRecord[] }

export default function ModelsPage() {
  const request = useRequest(() => getData<ModelOverview>('/models/overview'), [])
  const data = request.data
  return <div className="page">
    <PageHeader title="模型与预测" description="模型版本、Top 5、滚动评估和重训请求" onReload={request.reload} />
    <LoadState loading={request.loading} error={request.error}>
      <Tabs items={[
        { key: 'versions', label: '模型版本', children: <RecordTable rows={data?.versions || []} maxColumns={12} /> },
        { key: 'predictions', label: '预测排名', children: <RecordTable rows={data?.predictions || []} maxColumns={12} /> },
        { key: 'evaluations', label: '滚动评估', children: <RecordTable rows={data?.evaluations || []} maxColumns={12} /> },
        { key: 'requests', label: '重训请求', children: <RecordTable rows={data?.trainingRequests || []} maxColumns={12} /> },
      ]} />
    </LoadState>
  </div>
}
// AI_GENERATE_END -
