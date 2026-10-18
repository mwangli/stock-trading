// AI_GENERATE_START ----
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Descriptions, Input, List, Space, Tag, Typography, message } from 'antd';
import { RobotOutlined, SendOutlined } from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import request from '../../utils/request';
import { getApiErrorMessage, readApiData, type ApiResponse } from '../../utils/api';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import { localizedText } from '../../utils/localizedText';

const { Text, Title } = Typography;

interface RuntimeStatus {
  enabled: boolean;
  provider: string;
  ragEnabled: boolean;
  memoryEnabled: boolean;
  runningTasks: number;
  checkedAt?: string;
  deepseekEnabled: boolean;
  deepseekConfigured: boolean;
  deepseekStatus?: string;
  ollamaEnabled: boolean;
  ollamaConfigured: boolean;
  ollamaStatus?: string;
  embeddingEnabled: boolean;
  embeddingProvider: string;
  embeddingConfigured: boolean;
  embeddingReady: boolean;
  embeddingModel?: string;
  embeddingModelVersion?: string;
  embeddingDimension: number;
  embeddingStatus?: string;
  embeddingFallback: boolean;
}

interface SessionResponse {
  sessionId: string;
}

interface TaskResponse {
  taskId: string;
  status: string;
  resultSummary?: string;
  evidenceSummary?: string;
  uncertainty?: string;
  errorMessage?: string;
}

interface TradePlanResponse {
  planId: string;
  stockCode: string;
  direction: string;
  status: string;
  decisionMessage?: string;
  riskCode?: string;
  orderId?: string;
  executionTraceId?: string;
  executedAt?: string;
  updatedAt?: string;
}

interface AgentMessage {
  role: 'user' | 'assistant';
  content: string;
  status?: string;
}

const AgentPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const [runtime, setRuntime] = useState<RuntimeStatus | null>(null);
  const [runtimeLoading, setRuntimeLoading] = useState(false);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<AgentMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [tradePlan, setTradePlan] = useState<TradePlanResponse | null>(null);

  const context = useMemo(() => ({
    candidateBatchId: searchParams.get('candidateBatchId') || undefined,
    predictionBatchId: searchParams.get('predictionBatchId') || undefined,
    stockCode: searchParams.get('stockCode') || undefined,
    strategyVersion: searchParams.get('strategyVersion') || undefined,
    targetTradeDate: searchParams.get('targetTradeDate') || undefined,
  }), [searchParams]);

  usePageHeader(localizedText('智能决策', 'Intelligent Decision'));

  const loadRuntime = useCallback(async () => {
    setRuntimeLoading(true);
    try {
      const response = await request.get('/agent/runtime/status') as ApiResponse<RuntimeStatus>;
      setRuntime(readApiData(response, localizedText('读取模型连接状态失败', 'Failed to load model status')));
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('读取模型连接状态失败', 'Failed to load model status')));
    } finally {
      setRuntimeLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadRuntime();
  }, [loadRuntime]);

  useEffect(() => {
    const planId = searchParams.get('planId');
    if (!planId) {
      setTradePlan(null);
      return;
    }
    void request.get(`/agent/tradePlans/${encodeURIComponent(planId)}`)
      .then((response) => setTradePlan(readApiData(response as ApiResponse<TradePlanResponse>, localizedText('读取交易计划失败', 'Failed to load trade plan'))))
      .catch((cause: unknown) => message.error(getApiErrorMessage(cause, localizedText('读取交易计划失败', 'Failed to load trade plan'))));
  }, [searchParams]);

  const ensureSession = async (): Promise<string | null> => {
    if (sessionId) {
      return sessionId;
    }
    const response = await request.post('/agent/sessions', {
      title: localizedText('智能决策会话', 'Intelligent Decision Session'),
    }) as ApiResponse<SessionResponse>;
    const session = readApiData(response, localizedText('创建 Agent 会话失败', 'Failed to create Agent session'));
    setSessionId(session.sessionId);
    return session.sessionId;
  };

  const askAgent = async () => {
    const content = question.trim();
    if (!content || loading) {
      return;
    }
    setQuestion('');
    setMessages((current) => [...current, { role: 'user', content }]);
    setLoading(true);
    try {
      const currentSessionId = await ensureSession();
      if (!currentSessionId) {
        return;
      }
      const response = await request.post('/agent/tasks/research', {
        sessionId: currentSessionId,
        question: content,
        ...context,
      }) as ApiResponse<TaskResponse>;
      const task = readApiData(response, localizedText('Agent 分析失败', 'Agent analysis failed'));
      const result = [
        task.resultSummary,
        task.evidenceSummary ? `${localizedText('证据摘要', 'Evidence')}: ${task.evidenceSummary}` : '',
        task.uncertainty ? `${localizedText('不确定性', 'Uncertainty')}: ${task.uncertainty}` : '',
      ].filter(Boolean).join('\n\n') || task.errorMessage || localizedText('Agent 未返回结果', 'Agent returned no result');
      setMessages((current) => [...current, { role: 'assistant', content: result, status: task.status }]);
    } catch (cause) {
      setMessages((current) => [...current, {
        role: 'assistant',
        content: getApiErrorMessage(cause, localizedText('Agent 分析失败', 'Agent analysis failed')),
        status: 'FAILED',
      }]);
    } finally {
      setLoading(false);
    }
  };

  const providerTag = (enabled: boolean, configured: boolean, status?: string) => {
    if (!enabled) {
      return <Tag>已关闭</Tag>;
    }
    if (!configured) {
      return <Tag color="warning">配置不完整</Tag>;
    }
    return <Tag color={status === 'READY' ? 'success' : 'processing'}>{status || '已配置'}</Tag>;
  };

  return (
    <div className="app-page">
      <Card className="surface-card" title={localizedText('模型连接状态', 'Model Provider Status')}>
        <Descriptions column={{ xs: 1, sm: 2, lg: 5 }} size="small">
          <Descriptions.Item label={localizedText('选择策略', 'Provider Mode')}>{runtime?.provider || '-'}</Descriptions.Item>
          <Descriptions.Item label="DeepSeek">{providerTag(runtime?.deepseekEnabled === true, runtime?.deepseekConfigured === true, runtime?.deepseekStatus)}</Descriptions.Item>
          <Descriptions.Item label="Ollama">{providerTag(runtime?.ollamaEnabled === true, runtime?.ollamaConfigured === true, runtime?.ollamaStatus)}</Descriptions.Item>
          <Descriptions.Item label="Embedding">{providerTag(runtime?.embeddingEnabled === true, runtime?.embeddingConfigured === true, runtime?.embeddingStatus)}</Descriptions.Item>
          <Descriptions.Item label={localizedText('向量维度', 'Vector Dimension')}>{runtime?.embeddingDimension || '-'}</Descriptions.Item>
          <Descriptions.Item label={localizedText('运行任务', 'Running Tasks')}>{runtime?.runningTasks || 0}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card className="surface-card" title={<Space><RobotOutlined /><Title level={4} className="!mb-0">{localizedText('智能决策工作台', 'Agent Workspace')}</Title></Space>}>
        <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 5 }}>
          <Descriptions.Item label="candidateBatchId">{context.candidateBatchId || '-'}</Descriptions.Item>
          <Descriptions.Item label="predictionBatchId">{context.predictionBatchId || '-'}</Descriptions.Item>
          <Descriptions.Item label="stockCode">{context.stockCode || '-'}</Descriptions.Item>
          <Descriptions.Item label="strategyVersion">{context.strategyVersion || '-'}</Descriptions.Item>
          <Descriptions.Item label="targetTradeDate">{context.targetTradeDate || '-'}</Descriptions.Item>
        </Descriptions>
        <List className="mt-6" bordered dataSource={messages} locale={{ emptyText: localizedText('暂无会话消息', 'No messages yet') }} renderItem={(item) => (
          <List.Item>
            <div className="w-full whitespace-pre-wrap">
              <Space className="mb-2"><Tag color={item.role === 'user' ? 'blue' : 'green'}>{item.role === 'user' ? 'USER' : 'AGENT'}</Tag>{item.status && <Text type="secondary">{item.status}</Text>}</Space>
              <div>{item.content}</div>
            </div>
          </List.Item>
        )} />
        <Space.Compact className="mt-4 w-full">
          <Input value={question} onChange={(event) => setQuestion(event.target.value)} onPressEnter={() => void askAgent()} placeholder={localizedText('输入研究问题', 'Ask a research question')} disabled={loading} />
          <Button type="primary" icon={<SendOutlined />} loading={loading} onClick={() => void askAgent()}>{localizedText('发送', 'Send')}</Button>
        </Space.Compact>
      </Card>

      {tradePlan && <Card className="surface-card" title={localizedText('交易计划执行状态', 'Trade Plan Execution')}>
        <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 4 }}>
          <Descriptions.Item label="planId">{tradePlan.planId}</Descriptions.Item>
          <Descriptions.Item label={localizedText('股票', 'Stock')}>{tradePlan.stockCode}</Descriptions.Item>
          <Descriptions.Item label={localizedText('方向', 'Direction')}>{tradePlan.direction}</Descriptions.Item>
          <Descriptions.Item label={localizedText('状态', 'Status')}><Tag color={tradePlan.status === 'EXECUTED' ? 'success' : tradePlan.status === 'FAILED' ? 'error' : 'processing'}>{tradePlan.status}</Tag></Descriptions.Item>
          <Descriptions.Item label={localizedText('风险码', 'Risk Code')}>{tradePlan.riskCode || '-'}</Descriptions.Item>
          <Descriptions.Item label={localizedText('订单号', 'Order ID')}>{tradePlan.orderId || '-'}</Descriptions.Item>
          <Descriptions.Item label="executionTraceId">{tradePlan.executionTraceId || '-'}</Descriptions.Item>
          <Descriptions.Item label={localizedText('更新时间', 'Updated At')}>{tradePlan.updatedAt || '-'}</Descriptions.Item>
        </Descriptions>
      </Card>}
    </div>
  );
};

export default AgentPage;
// AI_GENERATE_END ----
