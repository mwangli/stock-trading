// AI_GENERATE_START ------------------------------------------
import './styles.css';
import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Col, Descriptions, Form, Input, InputNumber, Row, Select, Space, Statistic, Switch, Table, Tag, Tooltip, Typography, message } from 'antd';
import { BulbOutlined, InfoCircleOutlined, RobotOutlined, SendOutlined, SettingOutlined } from '@ant-design/icons';
import request from '../../utils/request';
import { getApiErrorMessage, readApiData, type ApiResponse } from '../../utils/api';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import { useTranslation } from 'react-i18next';
import { localizedText } from '../../utils/localizedText';

const { Text } = Typography;

type StrategyMode = 'CONSERVATIVE' | 'BALANCED' | 'AGGRESSIVE';

interface StrategyConfigValues {
  id?: number;
  configId?: string;
  version?: string;
  mode: StrategyMode;
  lstmWeight: number;
  sentimentWeight: number;
  topN: number;
  minScore: number;
  minimumListingYears: number;
  trailingStopWeight: number;
  trailingStopTolerance: number;
  rsiWeight: number;
  rsiOverboughtThreshold: number;
  volumeWeight: number;
  volumeShrinkThreshold: number;
  bollingerWeight: number;
  bollingerBreakoutThreshold: number;
  highReturnThreshold: number;
  normalReturnThreshold: number;
  lowReturnThreshold: number;
  lossReturnThreshold: number;
  trailingStopEnabled: boolean;
  rsiEnabled: boolean;
  volumeEnabled: boolean;
  bollingerEnabled: boolean;
  enabled: boolean;
  updateTime?: string;
}

interface StrategyState {
  enabled: boolean;
  currentMode?: StrategyMode;
  disabledIndicators?: string[];
  lastSwitchTime?: string;
  lastSwitchReason?: string;
  configVersion?: string;
  autoSwitchEnabled?: boolean;
  lastAutoSwitchTime?: string;
}

interface Ranking {
  stockCode: string;
  stockName: string;
  currentPrice?: number;
  lstmScore: number;
  newsImpactScore: number;
  totalScore: number;
  rank: number;
  reason: string;
  held?: boolean;
}

interface TradingStatus {
  realWriteEnabled: boolean;
  brokerAuthenticated: boolean;
  automaticExecutionEnabled: boolean;
}

interface TradingCandidate extends Ranking {
  held: boolean;
}

interface TradingCandidateBatch {
  candidateBatchId: string;
  predictionBatchId: string;
  modelVersion: string;
  sentimentModelVersion: string;
  strategyConfigVersion: string;
  baseTradeDate: string;
  targetTradeDate: string;
  informationCutoffAt: string;
  positionSnapshotAvailable: boolean;
  items: TradingCandidate[];
}

const defaultValues: StrategyConfigValues = {
  mode: 'BALANCED', lstmWeight: 0.5, sentimentWeight: 0.5, topN: 10, minScore: 0.5, minimumListingYears: 0.5,
  trailingStopWeight: 0.25, trailingStopTolerance: 0.02, rsiWeight: 0.25, rsiOverboughtThreshold: 70,
  volumeWeight: 0.25, volumeShrinkThreshold: 0.5, bollingerWeight: 0.25, bollingerBreakoutThreshold: 1,
  highReturnThreshold: 3, normalReturnThreshold: 2, lowReturnThreshold: 1, lossReturnThreshold: 0,
  trailingStopEnabled: true, rsiEnabled: true, volumeEnabled: true, bollingerEnabled: true, enabled: true,
};

interface AgentSessionResponse {
  sessionId: string;
}

interface AgentTaskResponse {
  resultSummary?: string;
  evidenceSummary?: string;
  errorMessage?: string;
}

interface AgentMessage {
  role: 'user' | 'assistant';
  content: string;
  evidenceSummary?: string;
}

interface RuntimeStatus {
  enabled: boolean;
  provider: string;
  ragEnabled: boolean;
  memoryEnabled: boolean;
  deepseekEnabled: boolean;
  deepseekConfigured: boolean;
  deepseekStatus?: string;
  deepseekModel?: string;
  ollamaEnabled: boolean;
  ollamaConfigured: boolean;
  ollamaStatus?: string;
  ollamaModel?: string;
  embeddingEnabled: boolean;
  embeddingProvider?: string;
  embeddingReady: boolean;
  embeddingConfigured: boolean;
  embeddingModel?: string;
  embeddingModelVersion?: string;
  embeddingDimension: number;
  embeddingStatus?: string;
  deepseekCircuitState?: string;
  ollamaCircuitState?: string;
  deepseekAvailable: boolean;
  ollamaAvailable: boolean;
}

const formatDate = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : localizedText('未记录', 'Not recorded');
const percent = (value: number) => `${(value * 100).toFixed(1)}%`;
const DEFAULT_PROVIDER_MODELS: Record<'deepseek' | 'ollama', string> = {
  deepseek: 'deepseek-chat',
  ollama: 'qwen2.5:0.5b',
};

const StrategyConfig: React.FC = () => {
  const { t } = useTranslation();
  const [form] = Form.useForm<StrategyConfigValues>();
  const [config, setConfig] = useState<StrategyConfigValues | null>(null);
  const [state, setState] = useState<StrategyState | null>(null);
  const [rankings, setRankings] = useState<Ranking[]>([]);
  const [tradingStatus, setTradingStatus] = useState<TradingStatus | null>(null);
  const [candidateBatch, setCandidateBatch] = useState<TradingCandidateBatch | null>(null);
  const [tradingCandidates, setTradingCandidates] = useState<TradingCandidate[]>([]);
  const [tradingLoading, setTradingLoading] = useState(false);
  const [, setTradingError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [agentOpen, setAgentOpen] = useState(false);
  const [agentSessionId, setAgentSessionId] = useState<string | null>(null);
  const [agentQuestion, setAgentQuestion] = useState('');
  const [agentMessages, setAgentMessages] = useState<AgentMessage[]>([]);
  const [agentLoading, setAgentLoading] = useState(false);
  const [runtime, setRuntime] = useState<RuntimeStatus | null>(null);
  const [, setRuntimeLoading] = useState(false);
  const [workspaceTab, setWorkspaceTab] = useState<'chat' | 'runtime' | 'config' | 'candidates'>('chat');

  const loadRuntime = useCallback(async () => {
    setRuntimeLoading(true);
    try {
      const [statusResponse, healthResponse] = await Promise.all([
        request.get('/agent/runtime/status') as Promise<ApiResponse<RuntimeStatus>>,
        request.get('/agent/runtime/health') as Promise<ApiResponse<Pick<RuntimeStatus, 'deepseekAvailable' | 'ollamaAvailable'>>>,
      ]);
      const status = readApiData(statusResponse, localizedText('读取模型连接状态失败', 'Failed to load model status'));
      const health = readApiData(healthResponse, localizedText('检查模型连接失败', 'Failed to check model health'));
      setRuntime({ ...status, ...health });
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('读取模型连接状态失败', 'Failed to load model status')));
    } finally {
      setRuntimeLoading(false);
    }
  }, []);
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [configResponse, stateResponse, rankingsResponse] = await Promise.all([
        request.get('/strategy/config') as Promise<ApiResponse<StrategyConfigValues>>,
        request.get('/strategy/state') as Promise<ApiResponse<StrategyState>>,
        request.get('/strategy/rankings') as Promise<ApiResponse<Ranking[]>>,
      ]);
      const nextConfig = readApiData(configResponse, localizedText('读取策略配置失败', 'Failed to load strategy configuration')) || defaultValues;
      form.setFieldsValue(nextConfig);
      setConfig(nextConfig);
      setState(readApiData(stateResponse, localizedText('读取策略状态失败', 'Failed to load strategy state')));
      setRankings(readApiData(rankingsResponse, localizedText('读取选股排名失败', 'Failed to load stock rankings')) || []);
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('读取策略配置失败', 'Failed to load strategy configuration')));
      form.setFieldsValue(defaultValues);
    } finally {
      setLoading(false);
    }
  }, [form]);

  const loadTradingData = useCallback(async () => {
    setTradingLoading(true);
    setTradingError(null);
    try {
      const [statusResponse, candidateResponse] = await Promise.all([
        request.get('/realTrading/status') as Promise<ApiResponse<TradingStatus>>,
        request.get('/realTrading/candidates') as Promise<ApiResponse<TradingCandidateBatch>>,
      ]);
      const nextStatus = readApiData(statusResponse, '读取真实交易状态失败');
      const nextBatch = readApiData(candidateResponse, '读取交易候选失败');
      setTradingStatus(nextStatus);
      setCandidateBatch(nextBatch);
      setTradingCandidates(nextBatch?.items ?? []);
    } catch (cause) {
      setTradingStatus(null);
      setCandidateBatch(null);
      setTradingCandidates([]);
      setTradingError(getApiErrorMessage(cause, '读取真实交易数据失败'));
    } finally {
      setTradingLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadRuntime();
    void loadData();
    void loadTradingData();
  }, [loadData, loadTradingData, loadRuntime]);

  const toggleAutoSwitch = async (enabled: boolean) => {
    try {
      const response = await request.post(`/strategy/autoSwitch?enabled=${enabled}`) as ApiResponse<StrategyState>;
      setState(readApiData(response, '更新自动切换开关失败'));
      message.success(enabled ? localizedText('规则自动切换已开启', 'Rule-based auto switch enabled') : localizedText('规则自动切换已关闭', 'Rule-based auto switch disabled'));
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('更新自动切换开关失败', 'Failed to update auto-switch setting')));
    }
  };

  const openAgent = async (): Promise<string | null> => {
    setAgentOpen(true);
    if (agentSessionId) {
      return agentSessionId;
    }
    try {
      const response = await request.post('/agent/sessions', {
        title: localizedText('策略智能决策会话', 'Strategy Decision Session'),
      }) as ApiResponse<AgentSessionResponse>;
      const session = readApiData(response, localizedText('创建 Agent 会话失败', 'Failed to create Agent session'));
      setAgentSessionId(session.sessionId);
      return session.sessionId;
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('创建 Agent 会话失败', 'Failed to create Agent session')));
      return null;
    }
  };

  const askAgent = async () => {
    const question = agentQuestion.trim();
    if (!question || agentLoading) {
      return;
    }
    const sessionId = agentSessionId || await openAgent();
    if (!sessionId) {
      return;
    }
    setAgentMessages((current) => [...current, { role: 'user', content: question }]);
    setAgentQuestion('');
    setAgentLoading(true);
    try {
      const response = await request.post('/agent/tasks/research', {
        sessionId,
        question,
        candidateBatchId: candidateBatch?.candidateBatchId,
        strategyVersion: candidateBatch?.strategyConfigVersion,
      }) as ApiResponse<AgentTaskResponse>;
      const task = readApiData(response, localizedText('Agent 分析失败', 'Agent analysis failed'));
      const content = task.resultSummary || task.errorMessage || localizedText('Agent 未返回结果', 'Agent returned no result');
      setAgentMessages((current) => [...current, {
        role: 'assistant',
        content,
        evidenceSummary: task.evidenceSummary,
      }]);
    } catch (cause) {
      setAgentMessages((current) => [...current, {
        role: 'assistant',
        content: getApiErrorMessage(cause, localizedText('Agent 分析失败', 'Agent analysis failed')),
      }]);
    } finally {
      setAgentLoading(false);
    }
  };

  const capabilityHealth = (status: 'available' | 'disabled' | 'pending' | 'unavailable') => {
    const text = status === 'available'
      ? localizedText('\u53ef\u7528', 'Available')
      : status === 'pending'
        ? localizedText('\u7b49\u5f85\u4f9d\u8d56', 'Pending')
        : status === 'disabled'
          ? localizedText('\u672a\u542f\u7528', 'Disabled')
          : localizedText('\u4e0d\u53ef\u7528', 'Unavailable');
    return <span className={`capability-health capability-health-${status}`}><span className="capability-health-dot" aria-hidden="true" />{text}</span>;
  };
  const activeProviderAvailable = runtime?.provider === 'deepseek'
    ? runtime.deepseekAvailable
    : runtime?.provider === 'ollama'
      ? runtime.ollamaAvailable
      : runtime?.deepseekAvailable === true || runtime?.ollamaAvailable === true;
  const agentCapabilityStatus: 'available' | 'disabled' | 'unavailable' = !runtime?.enabled
    ? 'disabled'
    : activeProviderAvailable ? 'available' : 'unavailable';
  const embeddingCapabilityStatus: 'available' | 'disabled' | 'unavailable' = !runtime?.embeddingEnabled
    ? 'disabled'
    : runtime.embeddingReady ? 'available' : 'unavailable';
  const ragCapabilityStatus: 'available' | 'disabled' | 'pending' = !runtime?.ragEnabled
    ? 'disabled'
    : runtime.embeddingReady ? 'available' : 'pending';
  const memoryCapabilityStatus: 'available' | 'disabled' | 'pending' = !runtime?.memoryEnabled
    ? 'disabled'
    : runtime.embeddingReady ? 'available' : 'pending';
  const activeModel = runtime?.provider === 'deepseek' ? runtime.deepseekModel : runtime?.ollamaModel;
  usePageHeader(localizedText('智能决策', 'Intelligent Decision'));

  const capabilityLight = (label: string, status: 'available' | 'disabled' | 'pending' | 'unavailable') => (
    <span className={`agent-capability-light ${status === 'available' ? 'is-ready' : ''}`}>
      <span className="agent-capability-light-dot" aria-hidden="true" />
      {label}
    </span>
  );

  const number = (label: string, name: keyof StrategyConfigValues, min: number, max: number, step: number, extra: string) => (<Form.Item label={label} name={name} extra={extra} rules={[{ required: true, message: `${label}${localizedText('不能为空', ' is required')}` }]}><InputNumber min={min} max={max} step={step} className="!w-full" /></Form.Item>);

  return (<div className="app-page strategy-workbench-page">
    <nav className="strategy-workspace-tabs" aria-label={localizedText('智能决策工作区视图', 'Decision workspace views')}>
      {[
        { key: 'chat', label: localizedText('对话工作台', 'Chat workspace') },
        { key: 'runtime', label: localizedText('运行评估', 'Runtime evaluation') },
        { key: 'config', label: localizedText('策略参数', 'Strategy parameters') },
        { key: 'candidates', label: localizedText('候选列表', 'Candidate list') },
      ].map((tab) => (
        <button
          key={tab.key}
          type="button"
          className={workspaceTab === tab.key ? 'is-active' : ''}
          aria-pressed={workspaceTab === tab.key}
          onClick={() => setWorkspaceTab(tab.key as typeof workspaceTab)}
        >
          {tab.label}
        </button>
      ))}
    </nav>
    {workspaceTab === 'chat' && (
    <div className="strategy-chat-workspace">
    <div className="agent-dialog-status-preview">
      <div className="agent-dialog-status-main"><div className="agent-dialog-status-icon"><RobotOutlined /></div><div><span>Agent 模型</span><strong>{activeModel || DEFAULT_PROVIDER_MODELS.ollama}</strong><small>{capabilityHealth(agentCapabilityStatus)} · {runtime?.provider || '-'}</small></div></div>
    </div>
    <section className={`agent-command-center ${agentOpen ? 'agent-command-center-active' : ''}`}>
      <div className="agent-command-header">
        <div className="agent-command-title-wrap">
          <div className="agent-command-icon"><RobotOutlined /></div>
          <div>
            <div className="agent-command-eyebrow">{localizedText('智能决策工作区', 'Intelligent Decision Workspace')}</div>
            <h2>{localizedText('和策略 Agent 对话', 'Talk to Strategy Agent')}</h2>
            <p>{localizedText('直接询问候选原因、模型信号与风险因素，让 Agent 帮你读懂当前策略。', 'Ask about candidates, model signals and risks to understand the current strategy faster.')}</p>
          </div>
        </div>
        <div className="agent-command-header-actions">
          <div className="agent-capability-lights" aria-label={localizedText('RAG 和 Memory 可用状态', 'RAG and Memory availability')}>
            {capabilityLight('RAG', ragCapabilityStatus)}
            {capabilityLight('Memory', memoryCapabilityStatus)}
            {capabilityLight('Embedding', embeddingCapabilityStatus)}
          </div>
          <div className="agent-command-status">
            <span className={`agent-status-dot ${agentOpen ? 'is-active' : ''}`} />
            {agentOpen ? localizedText('会话已就绪', 'Session ready') : localizedText('等待提问', 'Ready for a question')}
          </div>
        </div>
      </div>
      <div className="agent-command-body">
        <div className="agent-conversation">
          <div className="agent-message-list">
            {agentMessages.length === 0 && (
              <div className="agent-empty-state">
                <BulbOutlined className="agent-empty-icon" />
                <strong>{localizedText('从一个问题开始', 'Start with a question')}</strong>
                <span>{localizedText('Agent 会结合当前候选、策略配置和执行状态给出分析。', 'Agent will use the current candidates, configuration and execution status.')}</span>
              </div>
            )}
            {agentMessages.map((item, index) => (
              <div key={`${item.role}-${index}`} className={`agent-message agent-message-${item.role}`}>
                <Text strong>{item.role === 'user' ? localizedText('你', 'You') : localizedText('策略 Agent', 'Strategy Agent')}</Text>
                <div>{item.content}</div>
                {item.evidenceSummary && (
                  <details className="agent-evidence">
                    <summary>{localizedText('查看证据摘要', 'View evidence summary')}</summary>
                    <pre>{item.evidenceSummary}</pre>
                  </details>
                )}
              </div>
            ))}
          </div>
          <div className="agent-input-row">
            <Input.TextArea
              value={agentQuestion}
              onChange={(event) => setAgentQuestion(event.target.value)}
              placeholder={localizedText('例如：为什么当前候选批次没有生成买入计划？', 'For example: why did the current candidate batch not produce a buy plan?')}
              autoSize={{ minRows: 2, maxRows: 5 }}
              onPressEnter={(event) => {
                if (!event.shiftKey) {
                  event.preventDefault();
                  void askAgent();
                }
              }}
            />
            <Button type="primary" icon={<SendOutlined />} loading={agentLoading} disabled={!agentQuestion.trim()} onClick={() => void askAgent()} aria-label={localizedText('发送问题', 'Send question')}>
              {localizedText('发送', 'Send')}
            </Button>
          </div>
        </div>
        <div className="agent-prompts">
          <div className="agent-prompts-title">{localizedText('你可以这样问', 'Try asking')}</div>
          {[
            localizedText('当前候选中最值得关注的是谁？', 'Which current candidate deserves attention?'),
            localizedText('当前策略的主要风险是什么？', 'What are the main risks in this strategy?'),
            localizedText('为什么没有生成买入计划？', 'Why was no buy plan generated?'),
            localizedText('当前模型连接是否正常？', 'Are the current model connections healthy?'),
            localizedText('哪些风险因素需要优先关注？', 'Which risk factors need attention first?'),
          ].map((prompt) => (
            <button key={prompt} type="button" className="agent-prompt" onClick={() => { setAgentQuestion(prompt); void openAgent(); }}>
              <span>{prompt}</span>
              <span aria-hidden="true">→</span>
            </button>
          ))}
          <Button className="agent-start-button" icon={<RobotOutlined />} onClick={() => void openAgent()}>
            {agentOpen ? localizedText('继续对话', 'Continue conversation') : localizedText('开始 Agent 对话', 'Start Agent chat')}
          </Button>
        </div>
      </div>
    </section>
    </div>
    )}
    {workspaceTab === 'runtime' && (
    <div className="strategy-auxiliary-panel">
    <section className="strategy-data-section">
      <h2 className="strategy-data-section-title">{localizedText('运行评估', 'Runtime Evaluation')}</h2>
      <Card className="surface-card" title={localizedText('真实执行状态', 'Live Execution Status')}>
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}><Statistic title={localizedText('券商会话', 'Broker Session')} value={tradingStatus?.brokerAuthenticated ? localizedText('有效', 'Valid') : localizedText('无效', 'Invalid')} /><Tag color={tradingStatus?.brokerAuthenticated ? 'green' : 'red'}>{tradingStatus?.brokerAuthenticated ? localizedText('运行正常', 'Healthy') : localizedText('需要登录', 'Login Required')}</Tag></Col>
        <Col xs={24} md={8}><Statistic title={localizedText('真实写入', 'Live Writes')} value={tradingStatus?.realWriteEnabled ? localizedText('已开启', 'Enabled') : localizedText('已关闭', 'Disabled')} /><Tag color={tradingStatus?.realWriteEnabled ? 'red' : 'blue'}>{tradingStatus?.realWriteEnabled ? localizedText('生产写入', 'Production Writes') : localizedText('只读观察', 'Read-only')}</Tag></Col>
        <Col xs={24} md={8}><Statistic title={localizedText('自动执行', 'Automatic Execution')} value={tradingStatus?.automaticExecutionEnabled ? localizedText('就绪', 'Ready') : localizedText('停止', 'Stopped')} /><Tag color={tradingStatus?.automaticExecutionEnabled ? 'green' : 'default'}>{tradingStatus?.automaticExecutionEnabled ? localizedText('允许执行', 'Execution Allowed') : localizedText('受控状态', 'Controlled')}</Tag></Col>
      </Row>
      </Card>
      <Card className="surface-card" title={localizedText('候选批次追溯', 'Candidate Batch Traceability')}>
      <Descriptions column={{ xs: 1, sm: 2, lg: 4 }} size="small">
        <Descriptions.Item label="候选批次">{candidateBatch?.candidateBatchId ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="预测批次">{candidateBatch?.predictionBatchId ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="涨跌模型">{candidateBatch?.modelVersion ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="情感模型">{candidateBatch?.sentimentModelVersion ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="策略版本">{candidateBatch?.strategyConfigVersion ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="基准交易日">{candidateBatch?.baseTradeDate ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="目标交易日">{candidateBatch?.targetTradeDate ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="信息截止时间">{candidateBatch?.informationCutoffAt ?? '-'}</Descriptions.Item>
      </Descriptions>
      </Card>
    </section>
    </div>
    )}
    {workspaceTab === 'config' && (
    <div className="strategy-auxiliary-panel">
    <section className="strategy-data-section">
      <h2 className="strategy-data-section-title">{localizedText('策略参数状态', 'Strategy Parameter Status')}</h2>
    <div className="strategy-auto-tuning-note">
      <div className="strategy-auto-tuning-note-icon"><RobotOutlined /></div>
      <div>
        <strong>{localizedText('参数由系统自动评估', 'Parameters are evaluated automatically')}</strong>
        <span>{localizedText('强化学习根据历史收益、回撤、胜率和交易成本反馈更新参数；当前页面仅展示生效版本，不能手动覆盖。', 'Reinforcement learning updates parameters from return, drawdown, win-rate and trading-cost feedback. This page shows the active version and does not allow manual overrides.')}</span>
      </div>
      <Tag color="blue">{localizedText('自动调整', 'Auto-tuned')}</Tag>
    </div>
    <Form<StrategyConfigValues> form={form} layout="vertical" initialValues={defaultValues} disabled>
      <Card className="surface-card" title={localizedText('模式与选股参数', 'Mode and Stock Selection Parameters')}><Row gutter={[24, 8]}>
        <Col xs={24} md={8}><Form.Item label={localizedText('策略模式', 'Strategy Mode')} name="mode"><Select options={[{ value: 'CONSERVATIVE', label: localizedText('保守模式', 'Conservative') }, { value: 'BALANCED', label: localizedText('均衡模式', 'Balanced') }, { value: 'AGGRESSIVE', label: localizedText('激进模式', 'Aggressive') }]} /></Form.Item></Col>
        <Col xs={24} md={8}>{number(localizedText('LSTM 因子权重', 'LSTM Factor Weight'), 'lstmWeight', 0, 1, 0.05, localizedText('与情感权重合计必须为 1', 'Must total 1 with sentiment weight'))}</Col>
        <Col xs={24} md={8}>{number(localizedText('情感因子权重', 'Sentiment Factor Weight'), 'sentimentWeight', 0, 1, 0.05, localizedText('与 LSTM 权重合计必须为 1', 'Must total 1 with LSTM weight'))}</Col>
        <Col xs={24} md={8}>{number(localizedText('选股数量', 'Stock Count'), 'topN', 1, 100, 1, localizedText('候选股票最大数量', 'Maximum candidate stocks'))}</Col>
        <Col xs={24} md={8}>{number(localizedText('最低综合得分', 'Minimum Composite Score'), 'minScore', 0, 1, 0.01, localizedText('低于该分数的候选将被过滤', 'Candidates below this score are filtered'))}</Col>
        <Col xs={24} md={8}>{number(localizedText('最小上市年限（年）', 'Minimum Listing Years'), 'minimumListingYears', 0, 100, 0.5, localizedText('默认 0.5 年，即 6 个月；0 表示不启用', 'Default 0.5 years, or 6 months; 0 disables this filter'))}</Col>
        <Col xs={24} md={8}><Form.Item label={localizedText('策略总开关', 'Strategy Master Switch')} name="enabled" valuePropName="checked"><Switch checkedChildren={localizedText('开', 'On')} unCheckedChildren={localizedText('关', 'Off')} /></Form.Item></Col>
      </Row></Card>
    <Card className="surface-card" title={localizedText('技术指标参数', 'Technical Indicator Parameters')} extra={<Space size={8}><Tag color="blue">{localizedText('系统内置', 'System managed')}</Tag><Text type="secondary">{localizedText('只读 · 由系统统一管理', 'Read-only · centrally managed')}</Text></Space>}>
      <div className="indicator-grid">
        <div className="indicator-panel indicator-panel-stop">
          <div className="indicator-panel-head">
            <div>
              <div className="indicator-title"><span>移动止损</span><Tooltip title="跟踪当日最高价，限制价格回撤幅度"><InfoCircleOutlined /></Tooltip></div>
              <div className="indicator-purpose">保护已有收益，按回撤幅度动态触发。</div>
            </div>
            <Tag color="blue">系统内置</Tag>
          </div>
          <div className="indicator-fields">
            {number('权重', 'trailingStopWeight', 0, 1, 0.05, '')}
            {number('回撤容忍度', 'trailingStopTolerance', 0, 1, 0.005, '0.02 = 允许回撤 2%')}
          </div>
        </div>
        <div className="indicator-panel indicator-panel-rsi">
          <div className="indicator-panel-head">
            <div>
              <div className="indicator-title"><span>RSI 指标</span><Tooltip title="衡量近期上涨与下跌动能的相对强弱"><InfoCircleOutlined /></Tooltip></div>
              <div className="indicator-purpose">识别短线超买，辅助判断卖出时机。</div>
            </div>
            <Tag color="blue">系统内置</Tag>
          </div>
          <div className="indicator-fields">
            {number('权重', 'rsiWeight', 0, 1, 0.05, '')}
            {number('超买阈值', 'rsiOverboughtThreshold', 50, 100, 1, '通常 70 附近代表超买')}
          </div>
        </div>
        <div className="indicator-panel indicator-panel-volume">
          <div className="indicator-panel-head">
            <div>
              <div className="indicator-title"><span>成交量指标</span><Tooltip title="比较当前成交量与近期平均成交量"><InfoCircleOutlined /></Tooltip></div>
              <div className="indicator-purpose">识别量价背离，辅助判断动能是否衰减。</div>
            </div>
            <Tag color="blue">系统内置</Tag>
          </div>
          <div className="indicator-fields">
            {number('权重', 'volumeWeight', 0, 1, 0.05, '')}
            {number('萎缩阈值', 'volumeShrinkThreshold', 0, 1, 0.05, '0.50 = 当前量低于均量 50%')}
          </div>
        </div>
        <div className="indicator-panel indicator-panel-bollinger">
          <div className="indicator-panel-head">
            <div>
              <div className="indicator-title"><span>布林带指标</span><Tooltip title="用均线和波动率描述价格的正常区间"><InfoCircleOutlined /></Tooltip></div>
              <div className="indicator-purpose">识别价格突破波动区间，辅助判断短线过热。</div>
            </div>
            <Tag color="blue">系统内置</Tag>
          </div>
          <div className="indicator-fields">
            {number('权重', 'bollingerWeight', 0, 1, 0.05, '')}
            {number('突破系数', 'bollingerBreakoutThreshold', 0, 2, 0.001, '1.005 = 高于上轨 0.5%')}
          </div>
        </div>
      </div>
    </Card>
      <Card className="surface-card" title={localizedText('收益退出阈值', 'Return Exit Thresholds')} extra={<Text type="secondary">{localizedText('只读 · 按收益场景分层', 'Read-only · tiered by return scenario')}</Text>}>
        <Row className="return-threshold-grid" gutter={[24, 8]}>
          <Col xs={24} sm={12} xl={6}>{number(localizedText('高收益阈值', 'High Return Threshold'), 'highReturnThreshold', -100, 100, 1, localizedText('高收益场景退出阈值', 'Exit threshold for high-return scenarios'))}</Col>
          <Col xs={24} sm={12} xl={6}>{number(localizedText('正常收益阈值', 'Normal Return Threshold'), 'normalReturnThreshold', -100, 100, 1, localizedText('正常收益场景退出阈值', 'Exit threshold for normal-return scenarios'))}</Col>
          <Col xs={24} sm={12} xl={6}>{number(localizedText('低收益阈值', 'Low Return Threshold'), 'lowReturnThreshold', -100, 100, 1, localizedText('低收益场景退出阈值', 'Exit threshold for low-return scenarios'))}</Col>
          <Col xs={24} sm={12} xl={6}>{number(localizedText('亏损阈值', 'Loss Threshold'), 'lossReturnThreshold', -100, 100, 1, localizedText('亏损场景退出阈值', 'Exit threshold for loss scenarios'))}</Col>
        </Row>
      </Card>
    </Form>
    </section>
    </div>
    )}
    {workspaceTab === 'candidates' && (
    <div className="strategy-auxiliary-panel">
    <Row gutter={[16, 16]}>
      <Col xs={24} md={6}><Card className="surface-card compact-card"><Statistic title={localizedText('当前策略模式', 'Current Strategy Mode')} value={state?.currentMode || config?.mode || 'BALANCED'} prefix={<SettingOutlined />} /><Text type="secondary">{localizedText('配置版本', 'Config Version')}：{config?.version || state?.configVersion || localizedText('未标记', 'Unmarked')}</Text></Card></Col>
      <Col xs={24} md={8}><Card className="surface-card compact-card"><Statistic title={localizedText('策略状态', 'Strategy Status')} value={config?.enabled ? localizedText('已启用', 'Enabled') : localizedText('已停用', 'Disabled')} /><Text type="secondary">{localizedText('最近切换', 'Last Switch')}：{formatDate(state?.lastSwitchTime)}</Text></Card></Col>
      <Col xs={24} md={6}><Card className="surface-card compact-card"><Statistic title={localizedText('当前候选', 'Current Candidates')} value={tradingCandidates.length || rankings.length} suffix={config ? `/ ${config.topN}` : ''} /><Text type="secondary">{localizedText('最近更新时间', 'Last Updated')}：{formatDate(config?.updateTime)}</Text></Card></Col>
      <Col xs={24} md={4}><Card className="surface-card compact-card"><Text strong>{localizedText('自动切换', 'Auto Switch')}</Text><div className="mt-3"><Switch checked={state?.autoSwitchEnabled ?? false} onChange={(checked) => void toggleAutoSwitch(checked)} checkedChildren={localizedText('开', 'On')} unCheckedChildren={localizedText('关', 'Off')} /></div><Text type="secondary">{localizedText('最近', 'Last')}：{formatDate(state?.lastAutoSwitchTime)}</Text></Card></Col>
    </Row>
    <Card className="surface-card" title={t('strategyPage.candidates')}><Table<Ranking> rowKey="stockCode" loading={loading || tradingLoading} dataSource={rankings} pagination={{ defaultPageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100], showTotal: (total) => `共 ${total} 只` }} columns={[{ title: '排名', dataIndex: 'rank', width: 72 }, { title: '股票', dataIndex: 'stockName', render: (value: string, row: Ranking) => `${value} (${row.stockCode})` }, { title: '价格', dataIndex: 'currentPrice', width: 100, render: (value?: number) => value === undefined || value === null ? '-' : `¥${value.toFixed(2)}` }, { title: 'LSTM', dataIndex: 'lstmScore', render: (value: number) => percent(value) }, { title: '新闻影响', dataIndex: 'newsImpactScore', render: (value: number) => value.toFixed(3) }, { title: '综合得分', dataIndex: 'totalScore', render: (value: number) => <Tag color={value >= 0.7 ? 'green' : value >= 0.5 ? 'blue' : 'default'}>{value.toFixed(3)}</Tag> }, { title: '持仓', dataIndex: 'held', render: (value?: boolean) => value === undefined ? <Tag>未知</Tag> : <Tag color={value ? 'blue' : 'default'}>{value ? '已持有' : '未持有'}</Tag> }, { title: '说明', dataIndex: 'reason' }]} locale={{ emptyText: '暂无交易候选' }} /></Card>
    </div>
    )}
  </div>);
};

export default StrategyConfig;
// AI_GENERATE_END -----------------------------------------
