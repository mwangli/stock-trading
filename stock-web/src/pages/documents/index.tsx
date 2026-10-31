// AI_GENERATE_START --
import { Button, Drawer, Input, List, Typography } from 'antd'
import { MenuOutlined } from '@ant-design/icons'
import { useEffect, useMemo, useState } from 'react'
import { getData, type DataRecord } from '../../api/client'
import { MarkdownContent } from '../../components/MarkdownContent'
import { LoadState, PageHeader } from '../../components/Page'
import { useRequest } from '../../hooks/useRequest'
import { useMediaQuery } from '../../hooks/useMediaQuery'

interface DocumentData extends DataRecord { id: string; title: string; path: string; content: string }

export default function DocumentsPage() {
  const mobile = useMediaQuery('(max-width: 480px)')
  const documents = useRequest(() => getData<DataRecord[]>('/documents'), [])
  const [selected, setSelected] = useState('')
  const [content, setContent] = useState<DocumentData | null>(null)
  const [keyword, setKeyword] = useState('')
  const [listOpen, setListOpen] = useState(false)
  const filtered = useMemo(() => (documents.data || []).filter((item) => `${item.title} ${item.path}`.toLowerCase().includes(keyword.toLowerCase())), [documents.data, keyword])
  useEffect(() => {
    if (!selected && filtered[0]?.id) setSelected(String(filtered[0].id))
  }, [filtered, selected])
  useEffect(() => {
    if (!selected) return
    getData<DocumentData>('/documents/content', { id: selected }).then(setContent).catch(() => setContent(null))
  }, [selected])
  const documentList = <><Input.Search allowClear placeholder="筛选文档" onChange={(event) => setKeyword(event.target.value)} />
    <LoadState loading={documents.loading} error={documents.error}><List dataSource={filtered} renderItem={(item) => <List.Item className={selected === item.id ? 'selected-row' : ''} onClick={() => { setSelected(String(item.id)); setListOpen(false) }}><div><strong>{String(item.title)}</strong><Typography.Text type="secondary">{String(item.path)}</Typography.Text></div></List.Item>} /></LoadState></>

  return <div className="page document-page">
    <PageHeader title="文档工作台" description="与 Agent 共用的只读 Markdown 文档源" onReload={documents.reload} actions={mobile && <Button icon={<MenuOutlined />} onClick={() => setListOpen(true)}>文档</Button>} />
    <div className="document-layout">
      {!mobile && <aside className="panel document-list">{documentList}</aside>}
      <section className="panel document-content">{content ? <MarkdownContent content={content.content} /> : <div className="empty-chart">请选择文档</div>}</section>
    </div>
    <Drawer title="选择文档" placement="bottom" height="78dvh" open={listOpen} onClose={() => setListOpen(false)} rootClassName="page-drawer"><div className="document-list drawer-list">{documentList}</div></Drawer>
  </div>
}
// AI_GENERATE_END --
