// AI_GENERATE_START-----------------------
import './styles.css';
import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Button, Drawer, Input, Segmented, Spin, Tag, Tooltip, message, type InputRef } from 'antd';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CodeOutlined,
  HistoryOutlined,
  LinkOutlined,
  MessageOutlined,
  PlusOutlined,
  ReloadOutlined,
  RobotOutlined,
  SendOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { useAuth } from '@shared/auth/AuthContext';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import { useAgentConversationStore, type AgentConversationMessage } from '@shared/store/agentConversationStore';
import { getApiErrorMessage, readApiData, type ApiResponse } from '@shared/utils/api';
import request from '@shared/utils/request';
import AgentMarkdown from './AgentMarkdown';


interface AgentSession {
  sessionId: string;
  title: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

interface AgentSessionPayload {
  id?: string;
  sessionId?: string;
  title?: string;
  status?: string;
  createdAt?: string;
  updatedAt?: string;
  created_at?: string;
  updated_at?: string;
}

interface SessionListResponse {
  items: AgentSessionPayload[];
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

interface AgentHistoryMessage {
  role: 'user' | 'assistant';
  content: string;
  evidenceRefs?: string | string[];
  evidence_refs?: string;
  createdAt?: string;
  created_at?: string;
}

interface AgentChatResponse {
  sessionId: string;
  taskId?: string;
  answer: string;
  status?: string;
  evidenceRefs: string[];
  capabilities?: string[];
  uncertainty?: string;
  createdAt: string;
}

type AgentMode = 'chat' | 'development';

interface DevelopmentTask {
  taskId: string;
  sessionId?: string;
  requirement: string;
  status: string;
  buildAttempt: number;
  changedFiles: string[];
  commitSha?: string;
  workflowRunId?: number;
  workflowUrl?: string;
  progress: string;
  failureMessage?: string;
  createdAt: string;
  updatedAt: string;
}

const DESKTOP_PROMPT_EXAMPLES = [
  '今天上涨的股票有哪些？',
  '当前持仓风险和剩余资金如何？',
  '上个月收益如何，主要亏损来自哪里？',
  'Model 模块是怎么设计的？',
  '最近有哪些运行异常？',
];



const parseEvidenceRefs = (value?: string): string[] => {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    return [];
  }
};

const normalizeSession = (session: AgentSessionPayload): AgentSession | null => {
  const sessionId = session.sessionId || session.id;
  if (!sessionId) return null;
  const createdAt = session.createdAt || session.created_at || new Date().toISOString();
  return {
    sessionId,
    title: session.title || '新对话',
    status: session.status || 'ACTIVE',
    createdAt,
    updatedAt: session.updatedAt || session.updated_at || createdAt,
  };
};

const normalizeHistoryMessages = (history: AgentHistoryMessage[]): AgentConversationMessage[] => history.map((item) => ({
  role: item.role,
  content: item.content,
  createdAt: item.createdAt || item.created_at,
  evidenceRefs: Array.isArray(item.evidenceRefs)
    ? item.evidenceRefs
    : parseEvidenceRefs(item.evidenceRefs || item.evidence_refs),
}));

const formatDateTime = (value?: string) => value
  ? new Date(value).toLocaleString('zh-CN', { hour12: false })
  : '未记录';

const statusColor = (status?: string) => {
  if (status === 'COMPLETED' || status === 'SUCCEEDED') return 'success';
  if (status === 'FAILED') return 'error';
  if (status === 'DEGRADED' || status === 'NEEDS_REVIEW') return 'warning';
  return 'processing';
};

const DEVELOPMENT_TERMINAL_STATUSES = new Set(['SUCCEEDED', 'PLANNED', 'CANCELLED', 'FAILED']);

const DEVELOPMENT_STEPS = [
  ['ANALYZING', '理解需求'],
  ['READING_CODE', '读取代码'],
  ['GENERATING', '生成修改'],
  ['COMMITTING', '提交临时分支'],
  ['VERIFYING', '完整验证'],
  ['RELEASING', '统一发布'],
  ['DEPLOYING', '自动发布'],
] as const;

const developmentStepIndex = (status?: string) => {
  if (status === 'FIXING') return 2;
  if (status === 'BUILDING') return 4;
  if (status === 'PLANNED' || status === 'SUCCEEDED') return DEVELOPMENT_STEPS.length;
  if (status === 'CANCELLED' || status === 'FAILED') return -1;
  return DEVELOPMENT_STEPS.findIndex(([value]) => value === status);
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
  const [sessionDrawerOpen, setSessionDrawerOpen] = useState(false);
  const [mode, setMode] = useState<AgentMode>('chat');
  const [developmentTask, setDevelopmentTask] = useState<DevelopmentTask | null>(null);
  const [developmentSubmitting, setDevelopmentSubmitting] = useState(false);
  const [developmentCancelling, setDevelopmentCancelling] = useState(false);

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
      const response = await request.get(`/agent/sessions/${targetSessionId}/history`) as ApiResponse<SessionHistoryResponse | AgentHistoryMessage[]>;
      const history = readApiData(response, '读取会话历史失败');
      if (Array.isArray(history)) {
        replaceConversation(ownerId, targetSessionId, normalizeHistoryMessages(history));
      } else {
        replaceConversation(ownerId, history.session.sessionId, toMessages(history.tasks));
      }
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '读取会话历史失败'));
    } finally {
      setHistoryLoading(false);
    }
  }, [ownerId, replaceConversation, toMessages]);

  const loadSessions = useCallback(async () => {
    setSessionsLoading(true);
    try {
      const response = await request.get('/agent/sessions') as ApiResponse<SessionListResponse | AgentSessionPayload[]>;
      const data = readApiData(response, '读取会话列表失败');
      const rawSessions = Array.isArray(data) ? data : data.items ?? [];
      const items = rawSessions.map(normalizeSession).filter((item): item is AgentSession => item !== null);
      setSessions(items);
      const shouldRestoreHistory = restoredOwnerRef.current !== ownerId;
      restoredOwnerRef.current = ownerId;
      const preferred = items.find((item) => item.sessionId === sessionId) ?? items[0];
      if (preferred && shouldRestoreHistory && messages.length === 0) {
        await loadHistory(preferred.sessionId);
      }
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '读取会话列表失败'));
    } finally {
      setSessionsLoading(false);
    }
  }, [loadHistory, messages.length, ownerId, sessionId]);

  useEffect(() => {
    void loadSessions();
  }, [loadSessions]);

  const developmentStorageKey = `stock-agent-development-task:${ownerId}`;

  const loadDevelopmentTask = useCallback(async (taskId: string, silent = false) => {
    try {
      const response = await request.get(`/agent/development/tasks/${taskId}`) as ApiResponse<DevelopmentTask>;
      const task = readApiData(response, '读取自动开发任务失败');
      setDevelopmentTask(task);
      localStorage.setItem(developmentStorageKey, task.taskId);
    } catch (cause) {
      if (!silent) message.error(getApiErrorMessage(cause, '读取自动开发任务失败'));
    }
  }, [developmentStorageKey]);

  useEffect(() => {
    const taskId = localStorage.getItem(developmentStorageKey);
    if (taskId) void loadDevelopmentTask(taskId);
  }, [developmentStorageKey, loadDevelopmentTask]);

  useEffect(() => {
    if (!developmentTask || DEVELOPMENT_TERMINAL_STATUSES.has(developmentTask.status)) return undefined;
    const timer = window.setInterval(() => void loadDevelopmentTask(developmentTask.taskId, true), 5000);
    return () => window.clearInterval(timer);
  }, [developmentTask, loadDevelopmentTask]);

  const activeSession = sessions.find((item) => item.sessionId === sessionId);

  const createSession = async (initialQuestion: string): Promise<string | null> => {
    try {
      const response = await request.post('/agent/sessions', {
        title: summarizeConversationTitle(initialQuestion),
      }) as ApiResponse<AgentSessionPayload>;
      const created = normalizeSession(readApiData(response, '创建 Agent 会话失败'));
      if (!created) throw new Error('创建 Agent 会话失败');
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

  const startDevelopment = async () => {
    const requirement = question.trim();
    if (!requirement || developmentSubmitting) return;
    const activeSessionId = sessionId || await createSession(requirement);
    if (!activeSessionId) return;
    setDevelopmentSubmitting(true);
    try {
      const response = await request.post('/agent/development/tasks', {
        sessionId: activeSessionId,
        requirement,
      }) as ApiResponse<DevelopmentTask>;
      const task = readApiData(response, '创建自动开发任务失败');
      setDevelopmentTask(task);
      localStorage.setItem(developmentStorageKey, task.taskId);
      setQuestion(ownerId, '');
      message.success('自动开发任务已启动');
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '创建自动开发任务失败'));
    } finally {
      setDevelopmentSubmitting(false);
    }
  };

  const cancelDevelopment = async () => {
    if (!developmentTask || DEVELOPMENT_TERMINAL_STATUSES.has(developmentTask.status)) return;
    setDevelopmentCancelling(true);
    try {
      const response = await request.post(`/agent/development/tasks/${developmentTask.taskId}/cancel`) as ApiResponse<DevelopmentTask>;
      setDevelopmentTask(readApiData(response, '停止自动开发任务失败'));
      message.success('自动开发任务已停止');
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '停止自动开发任务失败'));
    } finally {
      setDevelopmentCancelling(false);
    }
  };

  const submit = () => mode === 'development' ? startDevelopment() : askAgent();


  return (
    <div className="app-page agent-conversation-page min-h-0">
      <div className="agent-conversation-layout">
        <aside className="agent-panel agent-session-panel border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900">
          <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3 dark:border-slate-700">
            <div>
              <div className="text-sm font-semibold text-slate-900 dark:text-slate-100">会话记录</div>
              <div className="mt-1 text-xs text-slate-500">当前会话保存</div>
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
          <div className="overflow-y-auto p-2">
            {sessionsLoading ? (
              <div className="flex justify-center py-8"><Spin /></div>
            ) : sessions.length === 0 ? (
              <div className="px-3 py-8 text-center text-sm text-slate-500">暂无历史会话</div>
            ) : sessions.map((item) => (
              <button
                key={item.sessionId}
                type="button"
                onClick={() => void loadHistory(item.sessionId)}
                className={`agent-session-item ${item.sessionId === sessionId ? 'is-active' : ''}`}
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
              <Tooltip title="会话记录">
                <Button
                  className="agent-history-button"
                  type="text"
                  icon={<HistoryOutlined />}
                  aria-label="打开会话记录"
                  onClick={() => setSessionDrawerOpen(true)}
                />
              </Tooltip>
              <div className="flex size-10 shrink-0 items-center justify-center bg-blue-600 text-white"><RobotOutlined /></div>
              <div className="min-w-0">
                <h2 className="truncate text-base font-semibold text-slate-900 dark:text-slate-100">{activeSession?.title || '新对话'}</h2>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Segmented<AgentMode>
                value={mode}
                options={[
                  { label: '业务问答', value: 'chat', icon: <MessageOutlined /> },
                  { label: '自动开发', value: 'development', icon: <CodeOutlined /> },
                ]}
                onChange={setMode}
              />
              <Tooltip title={mode === 'development' ? '刷新开发任务' : '刷新会话'}>
                <Button
                  icon={<ReloadOutlined />}
                  aria-label="刷新"
                  onClick={() => developmentTask && mode === 'development'
                    ? void loadDevelopmentTask(developmentTask.taskId)
                    : void loadSessions()}
                />
              </Tooltip>
            </div>
          </header>

          <div className="agent-message-scroll min-h-[360px] flex-1 overflow-y-auto px-5 py-5">
            {mode === 'development' ? (
              developmentTask ? (
                <div className="development-task-view">
                  <div className="development-task-heading">
                    <div>
                      <div className="text-base font-semibold text-slate-900">自动开发任务</div>
                      <div className="mt-1 text-sm text-slate-500">{developmentTask.requirement}</div>
                    </div>
                    <div className="flex items-center gap-2">
                      {!DEVELOPMENT_TERMINAL_STATUSES.has(developmentTask.status) && (
                        <Tooltip title="停止自动开发">
                          <Button danger icon={<StopOutlined />} loading={developmentCancelling} aria-label="停止自动开发" onClick={() => void cancelDevelopment()} />
                        </Tooltip>
                      )}
                      <Tag color={statusColor(developmentTask.status)}>{developmentTask.status}</Tag>
                    </div>
                  </div>
                  <div className="development-step-list" aria-label="自动开发进度">
                    {DEVELOPMENT_STEPS.map(([status, label], index) => {
                      const current = developmentStepIndex(developmentTask.status);
                      const completed = developmentTask.status === 'SUCCEEDED' || current > index;
                      const active = current === index;
                      return (
                        <div key={status} className={`development-step ${completed ? 'is-complete' : ''} ${active ? 'is-active' : ''}`}>
                          <span>{completed ? <CheckCircleOutlined /> : index + 1}</span>
                          <strong>{label}</strong>
                        </div>
                      );
                    })}
                  </div>
                  <div className="development-task-summary">
                    <div><span>当前进度</span><strong>{developmentTask.progress}</strong></div>
                    <div><span>构建次数</span><strong>{developmentTask.buildAttempt || 0}</strong></div>
                    <div><span>任务编号</span><strong>{developmentTask.taskId}</strong></div>
                    {developmentTask.commitSha && <div><span>提交</span><strong>{developmentTask.commitSha.slice(0, 12)}</strong></div>}
                  </div>
                  {(developmentTask.changedFiles?.length ?? 0) > 0 && (
                    <div className="development-file-list">
                      <div className="text-sm font-semibold">修改文件</div>
                      {developmentTask.changedFiles.map((path) => <code key={path}>{path}</code>)}
                    </div>
                  )}
                  {developmentTask.workflowUrl && (
                    <a className="development-workflow-link" href={developmentTask.workflowUrl} target="_blank" rel="noreferrer">
                      <LinkOutlined /> 查看 GitHub Actions
                    </a>
                  )}
                  {developmentTask.failureMessage && (
                    <div className="development-failure">{developmentTask.failureMessage}</div>
                  )}
                </div>
              ) : (
                <div className="flex h-full min-h-[320px] flex-col items-center justify-center text-center">
                  <CodeOutlined className="text-3xl text-[var(--text-muted)]" />
                  <div className="mt-3 text-sm text-slate-500">描述业务目标，Agent 将修改代码并自动构建发布</div>
                </div>
              )
            ) : historyLoading ? (
              <div className="flex h-full items-center justify-center"><Spin /></div>
            ) : messages.length === 0 ? (
              <div className="flex h-full min-h-[320px] flex-col items-center justify-center text-center">
                <MessageOutlined className="text-3xl text-[var(--text-muted)]" />
                <div className="mt-3 text-sm text-slate-500">发送消息开始新对话</div>
              </div>
            ) : (
              <div className="space-y-4">
                {messages.map((item, index) => (
                  <div key={`${item.taskId ?? index}-${item.role}`} className={`flex ${item.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                    <div className={`agent-message-bubble max-w-[84%] border px-4 py-3 ${
                      item.role === 'user'
                        ? 'border-blue-600 bg-blue-600 text-white'
                        : 'border-slate-200 bg-slate-50 text-slate-800 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100'
                    }`}>
                      <div className="mb-2 flex flex-wrap items-center gap-2 text-xs">
                        <strong>{item.role === 'user' ? currentUserName : '策略 Agent'}</strong>
                        {item.status && <Tag color={statusColor(item.status)}>{item.status}</Tag>}
                        {item.createdAt && <span className={item.role === 'user' ? 'text-blue-100' : 'text-slate-500'}>{formatDateTime(item.createdAt)}</span>}
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
            <div className="agent-composer-row flex items-end gap-3">
              <Input.TextArea
                ref={inputRef}
                value={question}
                onChange={(event) => setQuestion(ownerId, event.target.value)}
                autoSize={{ minRows: 2, maxRows: 5 }}
                onPressEnter={(event) => {
                  if (!event.shiftKey) {
                    event.preventDefault();
                    void submit();
                  }
                }}
                placeholder={mode === 'development'
                  ? '描述要实现或修复的业务目标、期望行为和验收结果'
                  : '询问行情、收益、交易记录、模型预测或运行异常'}
              />
              <Button
                type="primary"
                icon={mode === 'development' ? <CodeOutlined /> : <SendOutlined />}
                loading={mode === 'development' ? developmentSubmitting : loading}
                disabled={!question.trim() || (mode === 'development' && Boolean(developmentTask && !DEVELOPMENT_TERMINAL_STATUSES.has(developmentTask.status)))}
                onClick={() => void submit()}
              >
                {mode === 'development' ? '执行' : '发送'}
              </Button>
            </div>
          </footer>
        </main>

        <aside className="agent-example-rail">
          <section className="agent-panel agent-example-panel border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900">
            <header className="border-b border-slate-200 px-4 py-4 dark:border-slate-700">
              <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">问题示例</h2>
            </header>
            <div className="agent-example-list">
              {DESKTOP_PROMPT_EXAMPLES.map((prompt) => (
                <button
                  key={prompt}
                  type="button"
                  className="agent-example-item"
                  onClick={() => {
                    setQuestion(ownerId, prompt);
                    inputRef.current?.focus();
                  }}
                >
                  <MessageOutlined aria-hidden="true" />
                  <span>{prompt}</span>
                </button>
              ))}
            </div>
          </section>
        </aside>

      </div>

      <Drawer
        className="agent-session-drawer"
        title="会话记录"
        placement="left"
        width="min(88vw, 340px)"
        open={sessionDrawerOpen}
        onClose={() => setSessionDrawerOpen(false)}
        afterOpenChange={(open) => {
          if (!open) inputRef.current?.focus();
        }}
        extra={(
          <Tooltip title="新建会话">
            <Button
              type="text"
              icon={<PlusOutlined />}
              aria-label="新建会话"
              onClick={() => {
                resetConversation(ownerId);
                setSessionDrawerOpen(false);
              }}
            />
          </Tooltip>
        )}
      >
        <div className="agent-session-drawer-list">
          {sessionsLoading ? (
            <div className="flex justify-center py-8"><Spin /></div>
          ) : sessions.length === 0 ? (
            <div className="px-3 py-8 text-center text-sm text-slate-500">暂无历史会话</div>
          ) : sessions.map((item) => (
            <button
              key={item.sessionId}
              type="button"
              onClick={() => {
                setSessionDrawerOpen(false);
                void loadHistory(item.sessionId);
              }}
              className={`agent-session-drawer-item ${item.sessionId === sessionId ? 'is-active' : ''}`}
            >
              <div className="truncate text-sm font-medium">{item.title}</div>
              <div className="mt-1 flex items-center gap-1 text-xs text-slate-500">
                <ClockCircleOutlined />{formatDateTime(item.updatedAt)}
              </div>
            </button>
          ))}
        </div>
      </Drawer>
    </div>
  );
};

export default AgentConversation;
// AI_GENERATE_END-----------------------
