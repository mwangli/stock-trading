// AI_GENERATE_START ---
import { Button, Drawer, Input, List, Space, Tag, Typography } from 'antd'
import { MenuOutlined, PlusOutlined, SendOutlined } from '@ant-design/icons'
import { useEffect, useState } from 'react'
import { getData, postData, type DataRecord } from '../../api/client'
import { MarkdownContent } from '../../components/MarkdownContent'
import { LoadState, PageHeader } from '../../components/Page'
import { useRequest } from '../../hooks/useRequest'
import { useMediaQuery } from '../../hooks/useMediaQuery'

export default function AgentPage() {
  const mobile = useMediaQuery('(max-width: 480px)')
  const sessions = useRequest(() => getData<DataRecord[]>('/agent/sessions'), [])
  const [sessionId, setSessionId] = useState('')
  const [messages, setMessages] = useState<DataRecord[]>([])
  const [question, setQuestion] = useState('')
  const [sending, setSending] = useState(false)
  const [sessionsOpen, setSessionsOpen] = useState(false)

  useEffect(() => {
    const first = sessions.data?.[0]?.id
    if (!sessionId && first) setSessionId(String(first))
  }, [sessionId, sessions.data])
  useEffect(() => {
    if (!sessionId) { setMessages([]); return }
    getData<DataRecord[]>(`/agent/sessions/${sessionId}/history`).then(setMessages).catch(() => setMessages([]))
  }, [sessionId])

  const create = async () => {
    const session = await postData<DataRecord>('/agent/sessions', { title: '新对话' })
    await sessions.reload()
    setSessionId(String(session.id))
    setSessionsOpen(false)
  }
  const send = async () => {
    const text = question.trim()
    if (!text || sending) return
    setSending(true)
    setQuestion('')
    setMessages((current) => [...current, { role: 'user', content: text }])
    try {
      const response = await postData<DataRecord>('/agent/chat', { sessionId, question: text })
      setSessionId(String(response.sessionId))
      setMessages((current) => [...current, { role: 'assistant', content: response.answer, evidence_refs: JSON.stringify(response.evidenceRefs) }])
      await sessions.reload()
    } finally {
      setSending(false)
    }
  }

  const sessionList = <><Button type="primary" icon={<PlusOutlined />} onClick={() => void create()} block>新建对话</Button>
    <LoadState loading={sessions.loading} error={sessions.error}><List dataSource={sessions.data || []} renderItem={(item) => <List.Item className={sessionId === item.id ? 'selected-row' : ''} onClick={() => { setSessionId(String(item.id)); setSessionsOpen(false) }}>{String(item.title || '未命名对话')}</List.Item>} /></LoadState></>
  const activeSession = sessions.data?.find((item) => String(item.id) === sessionId)

  return <div className="page agent-page">
    <PageHeader title="Agent" description="只读交易事实问答与项目文档检索" actions={mobile && <Button icon={<MenuOutlined />} onClick={() => setSessionsOpen(true)}>会话</Button>} />
    <div className="agent-layout">
      {!mobile && <aside className="panel agent-sessions">{sessionList}</aside>}
      <section className="agent-chat">
        <div className="agent-context-strip"><span>会话：{String(activeSession?.title || '新对话')}</span><Tag color="green">只读模式</Tag></div>
        <div className="message-list">{messages.map((message, index) => <div className={`message ${message.role === 'user' ? 'message-user' : 'message-assistant'}`} key={String(message.id ?? index)}>
          <Space><Typography.Text strong>{message.role === 'user' ? '你' : 'Agent'}</Typography.Text>{message.role !== 'user' && <Tag color="green">只读证据</Tag>}</Space>
          {message.role === 'assistant' ? <MarkdownContent content={String(message.content || '')} /> : <p>{String(message.content || '')}</p>}
          {message.evidence_refs && <Typography.Text type="secondary" className="evidence">来源：{String(message.evidence_refs)}</Typography.Text>}
        </div>)}</div>
        <div className="chat-input"><Input.TextArea value={question} onChange={(event) => setQuestion(event.target.value)} autoSize={{ minRows: 2, maxRows: 5 }} placeholder="询问账户、交易、模型、任务或项目文档" onPressEnter={(event) => { if (!event.shiftKey) { event.preventDefault(); void send() } }} /><Button type="primary" aria-label="发送" icon={<SendOutlined />} loading={sending} onClick={() => void send()} /></div>
      </section>
    </div>
    <Drawer title="对话列表" placement="bottom" height="72dvh" open={sessionsOpen} onClose={() => setSessionsOpen(false)} rootClassName="page-drawer"><div className="agent-sessions drawer-list">{sessionList}</div></Drawer>
  </div>
}
// AI_GENERATE_END ---
