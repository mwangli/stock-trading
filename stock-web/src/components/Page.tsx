// AI_GENERATE_START --------
import { Alert, Button, Empty, Skeleton, Space, Table, Tag, Tooltip, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import type { ReactNode } from 'react'
import type { DataRecord } from '../api/client'
import { useMediaQuery } from '../hooks/useMediaQuery'

const labels: Record<string, string> = {
  id: 'ID', code: '代码', instrument: '代码', stock_name: '名称', name: '名称', market: '市场',
  status: '状态', trade_status: '交易状态', business_date: '业务日期', prediction_date: '预测日期',
  created_at: '创建时间', updated_at: '更新时间', captured_at: '采集时间', requested_at: '请求时间',
  quantity: '数量', available_quantity: '可用数量', frozen_quantity: '冻结数量', price: '价格',
  current_price: '现价', average_cost: '成本', market_value: '市值', rank: '排名', score: '评分',
  client_order_id: '幂等订单号', broker_order_id: '券商委托号', side: '方向', amount: '金额',
  model_version: '模型版本', reason: '原因', message: '信息', task_name: '任务', started_at: '开始时间',
  finished_at: '结束时间', as_of_date: '数据日期', title: '标题', role: '角色', content: '内容',
}

function renderValue(value: unknown, key: string) {
  if (value === null || value === undefined || value === '') return <span className="muted">-</span>
  if (typeof value === 'boolean') return <Tag color={value ? 'success' : 'error'}>{value ? '是' : '否'}</Tag>
  if (typeof value === 'object') return <Tooltip title={JSON.stringify(value)}><span>{JSON.stringify(value)}</span></Tooltip>
  const text = String(value)
  if (key.toLowerCase().includes('status')) return <Tag>{text}</Tag>
  return text
}

export function PageHeader({ title, description, onReload, actions }: { title: string; description: string; onReload?: () => void; actions?: ReactNode }) {
  return <header className="page-header">
    <div><Typography.Title level={2}>{title}</Typography.Title><Typography.Text type="secondary">{description}</Typography.Text></div>
    {(actions || onReload) && <Space className="page-header-actions">{actions}{onReload && <Button aria-label="刷新" icon={<ReloadOutlined />} onClick={onReload} />}</Space>}
  </header>
}

export function LoadState({ loading, error, children }: { loading: boolean; error: string | null; children: ReactNode }) {
  if (loading) return <Skeleton active paragraph={{ rows: 8 }} />
  if (error) return <Alert type="error" showIcon message="数据读取失败" description={error} />
  return <>{children}</>
}

export function ContractAlert({ contract }: { contract?: DataRecord }) {
  if (!contract || contract.available === true) return null
  return <Alert type="warning" showIcon message="Python 数据契约不可用" description={String(contract?.reason || '版本不兼容或数据文件缺失')} />
}

export function RecordTable({ rows, title, maxColumns = 10, forceCards = false }: { rows: DataRecord[]; title?: string; maxColumns?: number; forceCards?: boolean }) {
  const mobile = useMediaQuery('(max-width: 480px)')
  if (!rows.length) return <section className="panel"><Space direction="vertical"><strong>{title}</strong><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} /></Space></section>
  const keys = Array.from(new Set(rows.flatMap((row) => Object.keys(row)))).slice(0, maxColumns)
  const columns: ColumnsType<DataRecord> = keys.map((key) => ({
    title: labels[key] || key,
    dataIndex: key,
    key,
    ellipsis: true,
    render: (value: unknown) => renderValue(value, key),
  }))
  const titleKeys = ['stock_name', 'name', 'code', 'instrument', 'task_name', 'model_version', 'client_order_id', 'id']
  const preferredKeys = ['status', 'side', 'business_date', 'prediction_date', 'created_at', 'quantity', 'price', 'amount', 'score']
  return <section className="panel record-panel">
    {title && <h3>{title}</h3>}
    {mobile || forceCards ? <div className="record-card-list">{rows.map((row) => {
      const titleKey = titleKeys.find((key) => row[key] !== undefined) || keys[0] || 'id'
      const statusKey = keys.find((key) => key.toLowerCase().includes('status'))
      const metaKeys = preferredKeys.filter((key) => key !== statusKey && key !== titleKey && row[key] !== undefined).slice(0, 3)
      const detailKeys = keys.filter((key) => key !== statusKey && key !== titleKey)
      return <details className="record-card" key={String(row.id ?? row.client_order_id ?? row.code ?? JSON.stringify(row))}>
        <summary>
          <div className="record-card-heading">
            <div className="record-card-title"><strong>{String(row[titleKey] ?? '-')}</strong>{statusKey && renderValue(row[statusKey], statusKey)}</div>
            <div className="record-card-meta">{metaKeys.map((key) => <span key={key}><small>{labels[key] || key}</small>{renderValue(row[key], key)}</span>)}</div>
          </div>
          <span className="record-card-toggle" aria-hidden="true">查看</span>
        </summary>
        <dl>{detailKeys.map((key) => <div key={key}><dt>{labels[key] || key}</dt><dd>{renderValue(row[key], key)}</dd></div>)}</dl>
      </details>
    })}</div> : <Table rowKey={(row) => String(row.id ?? row.client_order_id ?? row.code ?? JSON.stringify(row))} columns={columns} dataSource={rows} pagination={{ pageSize: 20, showSizeChanger: false }} scroll={{ x: 'max-content' }} size="small" />}
  </section>
}
// AI_GENERATE_END --------
