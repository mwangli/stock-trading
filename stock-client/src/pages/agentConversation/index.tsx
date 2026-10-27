// AI_GENERATE_START------------
import './styles.css';
import React, { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Button, Input, Spin, Tag, Tooltip, Typography, message, type InputRef } from 'antd';
import {
  BulbOutlined,
  ClockCircleOutlined,
  FileSearchOutlined,
  MessageOutlined,
  PlusOutlined,
  ReloadOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { useAuth } from '../../auth/AuthContext';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import { useAgentConversationStore, type AgentConversationMessage } from '../../store/agentConversationStore';
import { getApiErrorMessage, readApiData, type ApiResponse } from '../../utils/api';
import request from '../../utils/request';
import AgentMarkdown from './AgentMarkdown';

const { Text } = Typography;

interface AgentSession {
  sessionId: string;
  title: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

interface SessionListResponse {
  items: AgentSession[];
}

interface AgentTask {
  taskId: string;
  sessionId: string;
  status: string;
  question: string;
  resultSummary?: string;
  evidenceSummary?: string;
  evidenceRefs?: string;
  uncertainty?: string;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

interface SessionHistoryResponse {
  session: AgentSession;
  tasks: AgentTask[];
}

interface AgentChatResponse {
  sessionId: string;
  taskId: string;
  answer: string;
  status: string;
  evidenceRefs: string[];
  capabilities: string[];
  uncertainty?: string;
  createdAt: string;
}

interface RuntimeStatus {
  enabled: boolean;
  provider?: string;
  deepseekModel?: string;
  ollamaModel?: string;
  embeddingReady: boolean;
  ragEnabled: boolean;
  memoryEnabled: boolean;
  deepseekAvailable?: boolean;
  ollamaAvailable?: boolean;
  projectDocumentsReady: boolean;
  projectDocumentSectionCount: number;
  webSearchConfigured: boolean;
  marketDataToolAvailable: boolean;
  performanceToolAvailable: boolean;
}

interface TradingCandidateBatch {
  predictionBatchId: string;
  modelVersion: string;
  targetTradeDate: string;
  informationCutoffAt: string;
}

const parseEvidenceRefs = (value?: string): string[] => {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    return [];
  }
};

const formatDateTime = (value?: string) => value
  ? new Date(value).toLocaleString('zh-CN', { hour12: false })
  : '未记录';

const statusColor = (status?: string) => {
  if (status === 'COMPLETED') return 'success';
  if (status === 'FAILED') return 'error';
  if (status === 'DEGRADED' || status === 'NEEDS_REVIEW') return 'warning';
  return 'processing';
};

const summarizeConversationTitle = (content: string): string => {
  const normalized = content
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^\s{0,3}(?:#{1,6}|>|[-*+]\s|\d+[.)]\s)\s*/gm, '')
    .replace(/[*_~]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  if (!normalized) return '新对话';
  const firstSentence = normalized.match(/^.*?[。！？!?]/)?.[0]?.trim() || normalized;
  const characters = Array.from(firstSentence);
  return characters.length > 28 ? `${characters.slice(0, 28).join('')}…` : firstSentence;
};

const AgentConversation: React.FC = () => {
  usePageHeader('智能对话');
  const { user } = useAuth();
  const ownerId = String(user?.id ?? 'anonymous');
  const currentUserName = user?.displayName?.trim() || user?.username?.trim() || '当前用户';
  const inputRef = useRef<InputRef>(null);
  const restoredOwnerRef = useRef<string | null>(null);
  const [sessions, setSessions] = useState<AgentSession[]>([]);
  const [sessionsLoading, setSessionsLoading] = useState(true);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [runtime, setRuntime] = useState<RuntimeStatus | null>(null);
  const [candidateBatch, setCandidateBatch] = useState<TradingCandidateBatch | null>(null);

  const sessionId = useAgentConversationStore((state) => state.sessionId);
  const question = useAgentConversationStore((state) => state.question);
  const messages = useAgentConversationStore((state) => state.messages);
  const loading = useAgentConversationStore((state) => state.loading);
  const activateOwner = useAgentConversationStore((state) => state.activateOwner);
  const setSessionId = useAgentConversationStore((state) => state.setSessionId);
  const setQuestion = useAgentConversationStore((state) => state.setQuestion);
  const appendMessage = useAgentConversationStore((state) => state.appendMessage);
  const setLoading = useAgentConversationStore((state) => state.setLoading);
  const replaceConversation = useAgentConversationStore((state) => state.replaceConversation);
  const resetConversation = useAgentConversationStore((state) => state.resetConversation);

  useLayoutEffect(() => {
    activateOwner(ownerId);
  }, [activateOwner, ownerId]);

  const loadContext = useCallback(async () => {
    const [runtimeResult, healthResult, candidateResult] = await Promise.allSettled([
      request.get('/agent/runtime/status') as Promise<ApiResponse<RuntimeStatus>>,
      request.get('/agent/runtime/health') as Promise<ApiResponse<Pick<RuntimeStatus, 'deepseekAvailable' | 'ollamaAvailable'>>>,
      request.get('/realTrading/candidates') as Promise<ApiResponse<TradingCandidateBatch>>,
    ]);
    if (runtimeResult.status === 'fulfilled' && runtimeResult.value.success) {
      const health = healthResult.status === 'fulfilled' && healthResult.value.success
        ? healthResult.value.data
        : {};
      setRuntime({ ...runtimeResult.value.data, ...health });
    } else {
      setRuntime(null);
    }
    if (candidateResult.status === 'fulfilled' && candidateResult.value.success) {
      setCandidateBatch(candidateResult.value.data);
    } else {
      setCandidateBatch(null);
    }
  }, []);

  const toMessages = useCallback((tasks: AgentTask[]): AgentConversationMessage[] => tasks.flatMap((task) => {
    const assistantContent = task.resultSummary || task.errorMessage
      || (['RUNNING', 'WAITING_MODEL', 'WAITING_TOOL', 'CREATED'].includes(task.status)
        ? '任务仍在处理中。' : '任务未返回可展示结果。');
    return [
      { role: 'user' as const, content: task.question, taskId: task.taskId, createdAt: task.createdAt },
      {
        role: 'assistant' as const,
        content: assistantContent,
        taskId: task.taskId,
        status: task.status,
        createdAt: task.updatedAt,
        evidenceSummary: task.evidenceSummary,
        evidenceRefs: parseEvidenceRefs(task.evidenceRefs),
        uncertainty: task.uncertainty,
      },
    ];
  }), []);

  const loadHistory = useCallback(async (targetSessionId: string) => {
    setHistoryLoading(true);
    try {
      const response = await request.get(`/agent/sessions/history/${targetSessionId}`) as ApiResponse<SessionHistoryResponse>;
      const history = readApiData(response, '读取会话历史失败');
      replaceConversation(ownerId, history.session.sessionId, toMessages(history.tasks));
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '读取会话历史失败'));
    } finally {
      setHistoryLoading(false);
    }
  }, [ownerId, replaceConversation, toMessages]);

  const loadSessions = useCallback(async () => {
    setSessionsLoading(true);
    try {
      const response = await request.get('/agent/sessions') as ApiResponse<SessionListResponse>;
      const data = readApiData(response, '读取会话列表失败');
      setSessions(data.items ?? []);
      const shouldRestoreHistory = restoredOwnerRef.current !== ownerId;
      restoredOwnerRef.current = ownerId;
      const preferred = data.items.find((item) => item.sessionId === sessionId) ?? data.items[0];
      if (preferred && shouldRestoreHistory && messages.length === 0) {
        await loadHistory(preferred.sessionId);
      }
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '读取会话列表失败'));
    } finally {
      setSessionsLoading(false);
    }
  }, [loadHistory, messages.length, sessionId]);

  useEffect(() => {
    void Promise.all([loadSessions(), loadContext()]);
  }, [loadContext, loadSessions]);

  const activeSession = sessions.find((item) => item.sessionId === sessionId);
  const activeModel = useMemo(() => {
    if (!runtime) return '运行状态不可用';
    if (runtime.provider === 'deepseek') return runtime.deepseekModel || 'DeepSeek';
    if (runtime.provider === 'ollama') return runtime.ollamaModel || 'Ollama';
    return runtime.provider || '自动路由';
  }, [runtime]);

  const createSession = async (initialQuestion: string): Promise<string | null> => {
    try {
      const response = await request.post('/agent/sessions', {
        title: summarizeConversationTitle(initialQuestion),
      }) as ApiResponse<AgentSession>;
      const created = readApiData(response, '创建 Agent 会话失败');
      setSessionId(ownerId, created.sessionId);
      setSessions((current) => [created, ...current]);
      return created.sessionId;
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '创建 Agent 会话失败'));
      return null;
    }
  };

  const askAgent = async () => {
    const normalizedQuestion = question.trim();
    if (!normalizedQuestion || loading) return;
    const activeSessionId = sessionId || await createSession(normalizedQuestion);
    if (!activeSessionId) return;
    appendMessage(ownerId, { role: 'user', content: normalizedQuestion, createdAt: new Date().toISOString() });
    setQuestion(ownerId, '');
    setLoading(ownerId, true);
    try {
      const response = await request.post('/agent/chat', {
        sessionId: activeSessionId,
        question: normalizedQuestion,
        predictionBatchId: candidateBatch?.predictionBatchId,
        modelVersion: candidateBatch?.modelVersion,
        targetTradeDate: candidateBatch?.targetTradeDate,
        pageContext: 'agentConversation',
      }) as ApiResponse<AgentChatResponse>;
      const result = readApiData(response, 'Agent 分析失败');
      appendMessage(ownerId, {
        role: 'assistant',
        content: result.answer || 'Agent 未返回可展示结果。',
        taskId: result.taskId,
        status: result.status,
        createdAt: result.createdAt,
        evidenceRefs: result.evidenceRefs,
        capabilities: result.capabilities,
        uncertainty: result.uncertainty,
      });
      void loadSessions();
    } catch (cause) {
      appendMessage(ownerId, {
        role: 'assistant',
        content: getApiErrorMessage(cause, 'Agent 分析失败'),
        status: 'FAILED',
        createdAt: new Date().toISOString(),
      });
    } finally {
      setLoading(ownerId, false);
    }
  };

  const quickPrompts = [
    '你有什么能力？',
    '北京今天天气怎么样？',
    'Model 模块是怎么设计的？',
    '今天上涨的股票有哪些？',
    '上个月收益如何，主要亏损来自哪里？',
  ];

  return (
    <div className="app-page agent-conversation-page min-h-0">
      <div className="agent-conversation-layout grid gap-4 xl:grid-cols-[224px_minmax(0,1fr)_292px]">
        <aside className="agent-panel agent-session-panel min-h-0 border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900">
          <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3 dark:border-slate-700">
            <div>
              <div className="text-sm font-semibold text-slate-900 dark:text-slate-100">会话记录</div>
              <div className="mt-1 text-xs text-slate-500">服务端保存</div>
            </div>
            <Tooltip title="新建会话">
              <Button
                type="text"
                icon={<PlusOutlined />}
                aria-label="新建会话"
                onClick={() => {
                  resetConversation(ownerId);
                  inputRef.current?.focus();
                }}
              />
            </Tooltip>
          </div>
          <div className="max-h-[calc(100vh-220px)] overflow-y-auto p-2">
            {sessionsLoading ? (
              <div className="flex justify-center py-8"><Spin /></div>
            ) : sessions.length === 0 ? (
              <div className="px-3 py-8 text-center text-sm text-slate-500">暂无历史会话</div>
            ) : sessions.map((item) => (
              <button
                key={item.sessionId}
                type="button"
                onClick={() => void loadHistory(item.sessionId)}
                className={`mb-1 w-full border-l-2 px-3 py-3 text-left transition-colors ${
                  item.sessionId === sessionId
                    ? 'border-emerald-500 bg-emerald-50 text-emerald-900 dark:bg-emerald-950/30 dark:text-emerald-100'
                    : 'agent-session-item border-transparent'
                }`}
              >
                <div className="truncate text-sm font-medium">{item.title}</div>
                <div className="mt-1 flex items-center gap-1 text-xs text-slate-500">
                  <ClockCircleOutlined />{formatDateTime(item.updatedAt)}
                </div>
              </button>
            ))}
          </div>
        </aside>

        <main className="agent-panel agent-chat-panel flex min-h-0 flex-col border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900">
          <header className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4 dark:border-slate-700">
            <div className="flex min-w-0 items-center gap-3">
              <div className="flex size-10 shrink-0 items-center justify-center bg-emerald-600 text-white"><RobotOutlined /></div>
              <div className="min-w-0">
                <h2 className="truncate text-base font-semibold text-slate-900 dark:text-slate-100">{activeSession?.title || '新对话'}</h2>
                <div className="mt-1 text-xs text-slate-500">只读研究、解释与诊断，不控制真实交易</div>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Tag color={runtime?.enabled ? 'success' : 'default'}>{runtime?.enabled ? 'Agent 可用' : 'Agent 未启用'}</Tag>
              <Tooltip title="刷新会话与运行上下文">
                <Button icon={<ReloadOutlined />} aria-label="刷新" onClick={() => void Promise.all([loadSessions(), loadContext()])} />
              </Tooltip>
            </div>
          </header>

          <div className="agent-message-scroll min-h-[360px] flex-1 overflow-y-auto px-5 py-5">
            {historyLoading ? (
              <div className="flex h-full items-center justify-center"><Spin /></div>
            ) : messages.length === 0 ? (
              <div className="flex h-full min-h-[320px] flex-col items-center justify-center text-center">
                <BulbOutlined className="text-3xl text-amber-500" />
                <div className="mt-3 text-base font-semibold text-slate-900 dark:text-slate-100">从当前策略事实开始提问</div>
                <div className="mt-2 max-w-md text-sm leading-6 text-slate-500">回答会携带候选批次、模型版本与数据截止时间；缺少事实时会明确标注不确定性。</div>
                <div className="agent-starter-grid mt-6 grid w-full max-w-2xl gap-2 md:grid-cols-3">
                  {quickPrompts.slice(0, 3).map((prompt) => (
                    <button
                      key={prompt}
                      type="button"
                      onClick={() => {
                        setQuestion(ownerId, prompt);
                        inputRef.current?.focus();
                      }}
                      className="agent-starter-question border px-3 py-3 text-left text-xs leading-5"
                    >
                      <MessageOutlined className="mr-2" />{prompt}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <div className="space-y-4">
                {messages.map((item, index) => (
                  <div key={`${item.taskId ?? index}-${item.role}`} className={`flex ${item.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                    <div className={`agent-message-bubble max-w-[84%] border px-4 py-3 ${
                      item.role === 'user'
                        ? 'border-emerald-600 bg-emerald-600 text-white'
                        : 'border-slate-200 bg-slate-50 text-slate-800 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100'
                    }`}>
                      <div className="mb-2 flex flex-wrap items-center gap-2 text-xs">
                        <strong>{item.role === 'user' ? currentUserName : '策略 Agent'}</strong>
                        {item.status && <Tag color={statusColor(item.status)}>{item.status}</Tag>}
                        {item.createdAt && <span className={item.role === 'user' ? 'text-emerald-100' : 'text-slate-500'}>{formatDateTime(item.createdAt)}</span>}
                      </div>
                      {item.role === 'assistant' ? (
                        <AgentMarkdown content={item.content} />
                      ) : (
                        <div className="whitespace-pre-wrap text-sm leading-6">{item.content}</div>
                      )}
                      {item.uncertainty && (
                        <div className="mt-3 border-t border-current/20 pt-2 text-xs leading-5">不确定性：{item.uncertainty}</div>
                      )}
                      {item.role === 'assistant' && ((item.evidenceRefs?.length ?? 0) > 0
                        || (item.capabilities?.length ?? 0) > 0 || item.evidenceSummary) && (
                        <details className="mt-3 text-xs">
                          <summary className="cursor-pointer font-medium">查看证据摘要</summary>
                          {item.evidenceSummary && <AgentMarkdown content={item.evidenceSummary} compact />}
                          {(item.capabilities?.length ?? 0) > 0 && <div className="mt-2 break-all text-slate-500">使用能力：{item.capabilities?.join('、')}</div>}
                          {(item.evidenceRefs?.length ?? 0) > 0 && <div className="mt-2 break-all text-slate-500">证据编号：{item.evidenceRefs?.join('、')}</div>}
                        </details>
                      )}
                    </div>
                  </div>
                ))}
                {loading && (
                  <div className="flex justify-start">
                    <div className="agent-message-bubble border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300">
                      <Spin size="small" /> <span className="ml-2">正在读取证据并生成回答</span>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          <footer className="agent-composer border-t border-slate-200 p-4 dark:border-slate-700">
            <div className="flex items-end gap-3">
              <Input.TextArea
                ref={inputRef}
                value={question}
                onChange={(event) => setQuestion(ownerId, event.target.value)}
                placeholder="询问候选原因、风险因素、模型信号或运行异常"
                autoSize={{ minRows: 2, maxRows: 5 }}
                onPressEnter={(event) => {
                  if (!event.shiftKey) {
                    event.preventDefault();
                    void askAgent();
                  }
                }}
              />
              <Button type="primary" icon={<SendOutlined />} loading={loading} disabled={!question.trim()} onClick={() => void askAgent()}>
                发送
              </Button>
            </div>
          </footer>
        </main>

        <aside className="agent-context-rail space-y-4">
          <section className="agent-panel border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
            <div className="flex items-center gap-2 text-sm font-semibold text-slate-900 dark:text-slate-100"><SafetyCertificateOutlined />能力状态</div>
            <div className="mt-4 space-y-3 text-xs">
              <div className="flex items-center justify-between gap-3"><span className="text-slate-500">当前模型</span><strong className="truncate">{activeModel}</strong></div>
              <div className="flex items-center justify-between"><span className="text-slate-500">RAG</span><Tag color={runtime?.ragEnabled && runtime?.embeddingReady ? 'success' : 'default'}>{runtime?.ragEnabled && runtime?.embeddingReady ? '可用' : '未就绪'}</Tag></div>
              <div className="flex items-center justify-between"><span className="text-slate-500">Memory</span><Tag color={runtime?.memoryEnabled ? 'success' : 'default'}>{runtime?.memoryEnabled ? '启用' : '未启用'}</Tag></div>
              <div className="flex items-center justify-between"><span className="text-slate-500">Embedding</span><Tag color={runtime?.embeddingReady ? 'success' : 'warning'}>{runtime?.embeddingReady ? '就绪' : '未就绪'}</Tag></div>
              <div className="flex items-center justify-between"><span className="text-slate-500">项目文档</span><Tag color={runtime?.projectDocumentsReady ? 'success' : 'warning'}>{runtime?.projectDocumentsReady ? `${runtime.projectDocumentSectionCount} 节` : '未就绪'}</Tag></div>
              <div className="flex items-center justify-between"><span className="text-slate-500">联网搜索</span><Tag color={runtime?.webSearchConfigured ? 'success' : 'warning'}>{runtime?.webSearchConfigured ? '已配置' : '不可用'}</Tag></div>
              <div className="flex items-center justify-between"><span className="text-slate-500">行情统计</span><Tag color={runtime?.marketDataToolAvailable ? 'success' : 'warning'}>{runtime?.marketDataToolAvailable ? '可用' : '不可用'}</Tag></div>
              <div className="flex items-center justify-between"><span className="text-slate-500">收益分析</span><Tag color={runtime?.performanceToolAvailable ? 'success' : 'warning'}>{runtime?.performanceToolAvailable ? '可用' : '不可用'}</Tag></div>
            </div>
          </section>

          <section className="agent-panel border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
            <div className="flex items-center gap-2 text-sm font-semibold text-slate-900 dark:text-slate-100"><FileSearchOutlined />快捷问题</div>
            <div className="mt-3 space-y-2">
              {quickPrompts.map((prompt) => (
                <button
                  key={prompt}
                  type="button"
                  onClick={() => {
                    setQuestion(ownerId, prompt);
                    inputRef.current?.focus();
                  }}
                  className="w-full border border-slate-200 px-3 py-2 text-left text-xs leading-5 text-slate-700 hover:border-emerald-500 hover:text-emerald-700 dark:border-slate-700 dark:text-slate-300"
                >
                  <MessageOutlined className="mr-2" />{prompt}
                </button>
              ))}
            </div>
          </section>
          <Text type="secondary" className="block px-1 text-xs">Agent 输出仅用于研究、解释和独立建议，Java 风控与交易执行保持权威。</Text>
        </aside>
      </div>
    </div>
  );
};

export default AgentConversation;
// AI_GENERATE_END------------
