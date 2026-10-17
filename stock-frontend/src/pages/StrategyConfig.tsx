// AI_GENERATE_START --------
import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Descriptions, Form, InputNumber, Row, Select, Space, Statistic, Switch, Table, Tag, Tooltip, Typography, message } from 'antd';
import { InfoCircleOutlined, PlayCircleOutlined, ReloadOutlined, RollbackOutlined, SaveOutlined, SettingOutlined } from '@ant-design/icons';
import request from '../utils/request';
import { getApiErrorMessage, readApiData, type ApiResponse } from '../utils/api';
import { usePageHeader } from '../layouts/PageHeaderContext';
import { useTranslation } from 'react-i18next';
import { localizedText } from '../utils/localizedText';

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
  sentimentScore: number;
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
  mode: 'BALANCED', lstmWeight: 0.5, sentimentWeight: 0.5, topN: 10, minScore: 0.5,
  trailingStopWeight: 0.25, trailingStopTolerance: 0.02, rsiWeight: 0.25, rsiOverboughtThreshold: 70,
  volumeWeight: 0.25, volumeShrinkThreshold: 0.5, bollingerWeight: 0.25, bollingerBreakoutThreshold: 1,
  highReturnThreshold: 3, normalReturnThreshold: 2, lowReturnThreshold: 1, lossReturnThreshold: 0,
  trailingStopEnabled: true, rsiEnabled: true, volumeEnabled: true, bollingerEnabled: true, enabled: true,
};

interface StrategyModeEvaluation {
  mode: StrategyMode;
  modeName: string;
  status: string;
  sourceType: string;
  windowStart?: string;
  windowEnd?: string;
  validationStart?: string;
  validationEnd?: string;
  sampleCount: number;
  batchCount: number;
  grossReturn?: number;
  maxDrawdown?: number;
  winRate?: number;
  profitLossRatio?: number;
  stabilityScore?: number;
  outOfSample: boolean;
  dataComplete: boolean;
  sampleSufficient: boolean;
  conclusion: string;
}

interface StrategyEvaluation {
  evaluationRunId?: string;
  calculatedAt?: string;
  modes: StrategyModeEvaluation[];
  summary: string;
}

const formatDate = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : localizedText('未记录', 'Not recorded');
const percent = (value: number) => `${(value * 100).toFixed(1)}%`;

const StrategyConfig: React.FC = () => {
  const { t } = useTranslation();
  const [form] = Form.useForm<StrategyConfigValues>();
  const [config, setConfig] = useState<StrategyConfigValues | null>(null);
  const [state, setState] = useState<StrategyState | null>(null);
  const [rankings, setRankings] = useState<Ranking[]>([]);
  const [evaluation, setEvaluation] = useState<StrategyEvaluation | null>(null);
  const [tradingStatus, setTradingStatus] = useState<TradingStatus | null>(null);
  const [candidateBatch, setCandidateBatch] = useState<TradingCandidateBatch | null>(null);
  const [tradingCandidates, setTradingCandidates] = useState<TradingCandidate[]>([]);
  const [tradingLoading, setTradingLoading] = useState(false);
  const [tradingError, setTradingError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [running, setRunning] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [configResponse, stateResponse, rankingsResponse, evaluationResponse] = await Promise.all([
        request.get('/strategy/config') as Promise<ApiResponse<StrategyConfigValues>>,
        request.get('/strategy/state') as Promise<ApiResponse<StrategyState>>,
        request.get('/strategy/rankings') as Promise<ApiResponse<Ranking[]>>,
        request.get('/strategy/evaluation/latest') as Promise<ApiResponse<StrategyEvaluation>>,
      ]);
      const nextConfig = readApiData(configResponse, localizedText('读取策略配置失败', 'Failed to load strategy configuration')) || defaultValues;
      form.setFieldsValue(nextConfig);
      setConfig(nextConfig);
      setState(readApiData(stateResponse, localizedText('读取策略状态失败', 'Failed to load strategy state')));
      setRankings(readApiData(rankingsResponse, localizedText('读取选股排名失败', 'Failed to load stock rankings')) || []);
      setEvaluation(readApiData(evaluationResponse, localizedText('读取策略评估失败', 'Failed to load strategy evaluation')));
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
        request.get('/real-trading/status') as Promise<ApiResponse<TradingStatus>>,
        request.get('/real-trading/candidates') as Promise<ApiResponse<TradingCandidateBatch>>,
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
    void loadData();
    void loadTradingData();
  }, [loadData, loadTradingData]);

  const saveConfig = async (values: StrategyConfigValues) => {
    setSaving(true);
    try {
      const response = await request.put('/strategy/config', values) as ApiResponse<StrategyConfigValues>;
      const saved = readApiData(response, '保存策略配置失败');
      setConfig(saved);
      form.setFieldsValue(saved);
      message.success(`${localizedText('策略配置已保存，当前版本', 'Strategy configuration saved, current version')} ${saved.version || localizedText('未标记', 'Unmarked')}`);
      await Promise.all([loadData(), loadTradingData()]);
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('保存策略配置失败', 'Failed to save strategy configuration')));
    } finally {
      setSaving(false);
    }
  };

  const resetConfig = async () => {
    try {
      const response = await request.post('/strategy/config/reset') as ApiResponse<StrategyConfigValues>;
      const reset = readApiData(response, '恢复默认策略失败');
      form.setFieldsValue(reset);
      setConfig(reset);
      message.success(localizedText('策略配置已恢复默认值', 'Strategy configuration restored to defaults'));
      await Promise.all([loadData(), loadTradingData()]);
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('恢复默认策略失败', 'Failed to restore default strategy')));
    }
  };

  const runSelection = async () => {
    const topN = form.getFieldValue('topN') || 10;
    setRunning(true);
    try {
      const response = await request.post(`/strategy/select?n=${topN}`) as ApiResponse<{ success: boolean; topN: Ranking[] }> ;
      const result = readApiData(response, '执行选股失败');
      message.success(`${localizedText('选股完成，生成', 'Selection completed, generated')} ${result.topN?.length || 0} ${localizedText('条候选', 'candidates')}`);
      await Promise.all([loadData(), loadTradingData()]);
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('执行选股失败', 'Failed to run stock selection')));
    } finally {
      setRunning(false);
    }
  };

  const toggleAutoSwitch = async (enabled: boolean) => {
    try {
      const response = await request.post(`/strategy/auto-switch?enabled=${enabled}`) as ApiResponse<StrategyState>;
      setState(readApiData(response, '更新自动切换开关失败'));
      message.success(enabled ? localizedText('规则自动切换已开启', 'Rule-based auto switch enabled') : localizedText('规则自动切换已关闭', 'Rule-based auto switch disabled'));
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('更新自动切换开关失败', 'Failed to update auto-switch setting')));
    }
  };

  usePageHeader(t('strategyPage.subtitle'));

  const number = (label: string, name: keyof StrategyConfigValues, min: number, max: number, step: number, extra: string) => (<Form.Item label={label} name={name} extra={extra} rules={[{ required: true, message: `${label}${localizedText('不能为空', ' is required')}` }]}><InputNumber min={min} max={max} step={step} className="!w-full" /></Form.Item>);

  return (<div className="app-page">
    <div className="flex flex-wrap justify-end gap-2">
      <Button icon={<ReloadOutlined />} loading={loading || tradingLoading} onClick={() => { void loadData(); void loadTradingData(); }}>{t('strategyPage.refresh')}</Button>
      <Button icon={<RollbackOutlined />} onClick={() => void resetConfig()}>{t('strategyPage.reset')}</Button>
      <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => form.submit()}>{t('strategyPage.save')}</Button>
    </div>
    <Row gutter={[16, 16]}>      <Col xs={24} md={6}><Card className="surface-card compact-card"><Statistic title={localizedText('当前策略模式', 'Current Strategy Mode')} value={state?.currentMode || config?.mode || 'BALANCED'} prefix={<SettingOutlined />} /><Text type="secondary">{localizedText('配置版本', 'Config Version')}：{config?.version || state?.configVersion || localizedText('未标记', 'Unmarked')}</Text></Card></Col>
      <Col xs={24} md={8}><Card className="surface-card compact-card"><Statistic title={localizedText('策略状态', 'Strategy Status')} value={config?.enabled ? localizedText('已启用', 'Enabled') : localizedText('已停用', 'Disabled')} /><Text type="secondary">{localizedText('最近切换', 'Last Switch')}：{formatDate(state?.lastSwitchTime)}</Text></Card></Col>
      <Col xs={24} md={6}><Card className="surface-card compact-card"><Statistic title={localizedText('当前候选', 'Current Candidates')} value={tradingCandidates.length || rankings.length} suffix={config ? `/ ${config.topN}` : ''} /><Text type="secondary">{localizedText('最近更新时间', 'Last Updated')}：{formatDate(config?.updateTime)}</Text></Card></Col>
      <Col xs={24} md={4}><Card className="surface-card compact-card"><Text strong>{localizedText('自动切换', 'Auto Switch')}</Text><div className="mt-3"><Switch checked={state?.autoSwitchEnabled ?? false} onChange={(checked) => void toggleAutoSwitch(checked)} checkedChildren={localizedText('开', 'On')} unCheckedChildren={localizedText('关', 'Off')} /></div><Text type="secondary">{localizedText('最近', 'Last')}：{formatDate(state?.lastAutoSwitchTime)}</Text></Card></Col></Row>
    <Card className="surface-card" title={localizedText('真实执行状态', 'Live Execution Status')}>
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}><Statistic title={localizedText('券商会话', 'Broker Session')} value={tradingStatus?.brokerAuthenticated ? localizedText('有效', 'Valid') : localizedText('无效', 'Invalid')} /><Tag color={tradingStatus?.brokerAuthenticated ? 'green' : 'red'}>{tradingStatus?.brokerAuthenticated ? localizedText('运行正常', 'Healthy') : localizedText('需要登录', 'Login Required')}</Tag></Col>
        <Col xs={24} md={8}><Statistic title={localizedText('真实写入', 'Live Writes')} value={tradingStatus?.realWriteEnabled ? localizedText('已开启', 'Enabled') : localizedText('已关闭', 'Disabled')} /><Tag color={tradingStatus?.realWriteEnabled ? 'red' : 'blue'}>{tradingStatus?.realWriteEnabled ? localizedText('生产写入', 'Production Writes') : localizedText('只读观察', 'Read-only')}</Tag></Col>
        <Col xs={24} md={8}><Statistic title={localizedText('自动执行', 'Automatic Execution')} value={tradingStatus?.automaticExecutionEnabled ? localizedText('就绪', 'Ready') : localizedText('停止', 'Stopped')} /><Tag color={tradingStatus?.automaticExecutionEnabled ? 'green' : 'default'}>{tradingStatus?.automaticExecutionEnabled ? localizedText('允许执行', 'Execution Allowed') : localizedText('受控状态', 'Controlled')}</Tag></Col>
      </Row>
      {tradingError && <Alert className="mt-4" type="warning" showIcon message={tradingError} />}
      <Alert className="mt-4" type={tradingStatus?.automaticExecutionEnabled ? 'error' : 'info'} showIcon message={tradingStatus?.automaticExecutionEnabled ? localizedText('自动任务具备进入真实下单流程的基础条件，最终仍以交易日和风控校验为准', 'Automatic execution can enter the live order flow, subject to trading-day and risk checks') : localizedText('自动交易未就绪，不会提交真实委托', 'Automatic trading is not ready; no live orders will be submitted')} />
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
    <Alert className="mb-4" type="info" showIcon message={localizedText('配置边界', 'Configuration Boundary')} description={localizedText('参数表单只影响策略评分和候选生成；自动切换开关仅控制策略自动切换任务，不修改系统采集、模型训练、券商凭据或真实委托写入开关。', 'The form affects strategy scoring and candidate generation only. Auto switch controls the strategy switching job and does not change data collection, training, broker credentials or live order writes.')} />
    <Card className="surface-card" title={localizedText('策略模式样本外评估', 'Out-of-sample Strategy Evaluation')} extra={<Text type="secondary">{formatDate(evaluation?.calculatedAt)}</Text>}>
      <Alert className="mb-4" type={evaluation?.modes.some((item) => item.status === 'AVAILABLE') ? 'success' : 'warning'} showIcon message={evaluation?.summary || localizedText('尚未执行策略评估', 'Strategy evaluation has not run')} />
      <Table<StrategyModeEvaluation>
        rowKey="mode"
        loading={loading}
        dataSource={evaluation?.modes || []}
        pagination={false}
        scroll={{ x: 1180 }}
        columns={[
          { title: localizedText('模式', 'Mode'), dataIndex: 'modeName', width: 110 },
          { title: localizedText('结论状态', 'Conclusion Status'), dataIndex: 'status', width: 130, render: (value: string) => <Tag color={value === 'AVAILABLE' ? 'green' : value === 'NOT_EVALUATED' ? 'default' : 'orange'}>{value === 'AVAILABLE' ? localizedText('可评估', 'Available') : value === 'NOT_EVALUATED' ? localizedText('未评估', 'Not Evaluated') : localizedText('样本不足', 'Insufficient Data')}</Tag> },
          { title: localizedText('批次/候选', 'Batches / Candidates'), width: 120, render: (_, row) => row.batchCount + '/' + row.sampleCount },
          { title: localizedText('毛收益率', 'Gross Return'), dataIndex: 'grossReturn', width: 100, render: (value?: number) => value === undefined || value === null ? '-' : percent(value) },
          { title: localizedText('最大回撤', 'Max Drawdown'), dataIndex: 'maxDrawdown', width: 100, render: (value?: number) => value === undefined || value === null ? '-' : percent(value) },
          { title: localizedText('胜率', 'Win Rate'), dataIndex: 'winRate', width: 100, render: (value?: number) => value === undefined || value === null ? '-' : percent(value) },
          { title: localizedText('盈亏比', 'Profit / Loss Ratio'), dataIndex: 'profitLossRatio', width: 90, render: (value?: number) => value === undefined || value === null ? '-' : value.toFixed(2) },
          { title: localizedText('稳定性', 'Stability'), dataIndex: 'stabilityScore', width: 100, render: (value?: number) => value === undefined || value === null ? '-' : percent(value) },
          { title: localizedText('数据门禁', 'Data Gate'), width: 150, render: (_, row) => <Space size={4}><Tag color={row.dataComplete ? 'green' : 'orange'}>{row.dataComplete ? localizedText('数据完整', 'Complete') : localizedText('数据不完整', 'Incomplete')}</Tag><Tag color={row.sampleSufficient ? 'green' : 'orange'}>{row.sampleSufficient ? localizedText('样本充分', 'Sufficient') : localizedText('样本不足', 'Insufficient')}</Tag></Space> },
          { title: localizedText('结论', 'Conclusion'), dataIndex: 'conclusion', width: 360 },
        ]}
        locale={{ emptyText: localizedText('暂无策略评估结果', 'No strategy evaluation results') }}
      />
    </Card>
    <Form<StrategyConfigValues> form={form} layout="vertical" initialValues={defaultValues} onFinish={(values) => void saveConfig(values)}>
      <Card className="surface-card" title={localizedText('模式与选股参数', 'Mode and Stock Selection Parameters')}><Row gutter={[24, 8]}>
        <Col xs={24} md={8}><Form.Item label={localizedText('策略模式', 'Strategy Mode')} name="mode"><Select options={[{ value: 'CONSERVATIVE', label: localizedText('保守模式', 'Conservative') }, { value: 'BALANCED', label: localizedText('均衡模式', 'Balanced') }, { value: 'AGGRESSIVE', label: localizedText('激进模式', 'Aggressive') }]} /></Form.Item></Col>
        <Col xs={24} md={8}>{number(localizedText('LSTM 因子权重', 'LSTM Factor Weight'), 'lstmWeight', 0, 1, 0.05, localizedText('与情感权重合计必须为 1', 'Must total 1 with sentiment weight'))}</Col>
        <Col xs={24} md={8}>{number(localizedText('情感因子权重', 'Sentiment Factor Weight'), 'sentimentWeight', 0, 1, 0.05, localizedText('与 LSTM 权重合计必须为 1', 'Must total 1 with LSTM weight'))}</Col>
        <Col xs={24} md={8}>{number(localizedText('选股数量', 'Stock Count'), 'topN', 1, 100, 1, localizedText('候选股票最大数量', 'Maximum candidate stocks'))}</Col>
        <Col xs={24} md={8}>{number(localizedText('最低综合得分', 'Minimum Composite Score'), 'minScore', 0, 1, 0.01, localizedText('低于该分数的候选将被过滤', 'Candidates below this score are filtered'))}</Col>
        <Col xs={24} md={8}><Form.Item label={localizedText('策略总开关', 'Strategy Master Switch')} name="enabled" valuePropName="checked"><Switch checkedChildren={localizedText('开', 'On')} unCheckedChildren={localizedText('关', 'Off')} /></Form.Item></Col>
      </Row></Card>
    <Card className="surface-card" title={localizedText('技术指标参数', 'Technical Indicator Parameters')} extra={<Text type="secondary">{localizedText('权重用于计算综合卖出评分', 'Weights calculate the composite sell score')}</Text>}>
      <Alert className="indicator-guide" type="info" showIcon message={localizedText('指标说明', 'Indicator Guide')} description={localizedText('各指标独立判断市场状态，再按权重累加为综合评分；移动止损一旦触发会直接进入卖出判断。', 'Each indicator evaluates market conditions independently and contributes by weight; a triggered trailing stop enters the sell decision directly.')} />
      <div className="indicator-grid">
        <div className="indicator-panel indicator-panel-stop">
          <div className="indicator-panel-head">
            <div>
              <div className="indicator-title"><span>移动止损</span><Tooltip title="跟踪当日最高价，限制价格回撤幅度"><InfoCircleOutlined /></Tooltip></div>
              <Text type="secondary">止损线 = 当日最高价 × (1 - 回撤容忍度)</Text>
            </div>
            <Form.Item name="trailingStopEnabled" valuePropName="checked" noStyle><Switch checkedChildren="启用" unCheckedChildren="停用" /></Form.Item>
          </div>
          <div className="indicator-purpose">作用：价格跌破动态止损线时优先保护已有收益。</div>
          {number('指标权重', 'trailingStopWeight', 0, 1, 0.05, '参与综合卖出评分')}
          {number('回撤容忍度', 'trailingStopTolerance', 0, 1, 0.005, '0.02 表示允许回撤 2%')}
        </div>
        <div className="indicator-panel indicator-panel-rsi">
          <div className="indicator-panel-head">
            <div>
              <div className="indicator-title"><span>RSI 指标</span><Tooltip title="衡量近期上涨与下跌动能的相对强弱"><InfoCircleOutlined /></Tooltip></div>
              <Text type="secondary">RSI = 100 - 100 ÷ (1 + 平均涨幅 ÷ 平均跌幅)</Text>
            </div>
            <Form.Item name="rsiEnabled" valuePropName="checked" noStyle><Switch checkedChildren="启用" unCheckedChildren="停用" /></Form.Item>
          </div>
          <div className="indicator-purpose">作用：识别短线超买，RSI 高于阈值且满足涨幅条件时提示卖出。</div>
          {number('指标权重', 'rsiWeight', 0, 1, 0.05, '参与综合卖出评分')}
          {number('超买阈值', 'rsiOverboughtThreshold', 50, 100, 1, '通常 70 附近代表超买')}
        </div>
        <div className="indicator-panel indicator-panel-volume">
          <div className="indicator-panel-head">
            <div>
              <div className="indicator-title"><span>成交量指标</span><Tooltip title="比较当前成交量与近期平均成交量"><InfoCircleOutlined /></Tooltip></div>
              <Text type="secondary">量比 = 当前成交量 ÷ 近期平均成交量</Text>
            </div>
            <Form.Item name="volumeEnabled" valuePropName="checked" noStyle><Switch checkedChildren="启用" unCheckedChildren="停用" /></Form.Item>
          </div>
          <div className="indicator-purpose">作用：识别价格上涨但量能跟不上等量价背离，辅助判断动能衰减。</div>
          {number('指标权重', 'volumeWeight', 0, 1, 0.05, '参与综合卖出评分')}
          {number('萎缩阈值', 'volumeShrinkThreshold', 0, 1, 0.05, '0.50 表示当前量低于均量 50%')}
        </div>
        <div className="indicator-panel indicator-panel-bollinger">
          <div className="indicator-panel-head">
            <div>
              <div className="indicator-title"><span>布林带指标</span><Tooltip title="用均线和波动率描述价格的正常区间"><InfoCircleOutlined /></Tooltip></div>
              <Text type="secondary">上轨 = 20 周期均线 + 2 × 标准差</Text>
            </div>
            <Form.Item name="bollingerEnabled" valuePropName="checked" noStyle><Switch checkedChildren="启用" unCheckedChildren="停用" /></Form.Item>
          </div>
          <div className="indicator-purpose">作用：识别价格持续突破波动区间上沿，辅助判断短线过热。</div>
          {number('指标权重', 'bollingerWeight', 0, 1, 0.05, '参与综合卖出评分')}
          {number('突破系数', 'bollingerBreakoutThreshold', 0, 2, 0.001, '1.005 表示需高于上轨 0.5%')}
        </div>
      </div>
    </Card>
      <Card className="surface-card" title={localizedText('收益退出阈值', 'Return Exit Thresholds')}><Row gutter={[24, 8]}>{number(localizedText('高收益阈值', 'High Return Threshold'), 'highReturnThreshold', -100, 100, 1, localizedText('高收益场景退出阈值', 'Exit threshold for high-return scenarios'))}{number(localizedText('正常收益阈值', 'Normal Return Threshold'), 'normalReturnThreshold', -100, 100, 1, localizedText('正常收益场景退出阈值', 'Exit threshold for normal-return scenarios'))}{number(localizedText('低收益阈值', 'Low Return Threshold'), 'lowReturnThreshold', -100, 100, 1, localizedText('低收益场景退出阈值', 'Exit threshold for low-return scenarios'))}{number(localizedText('亏损阈值', 'Loss Threshold'), 'lossReturnThreshold', -100, 100, 1, localizedText('亏损场景退出阈值', 'Exit threshold for loss scenarios'))}</Row></Card>
    </Form>
    <Card className="surface-card" title={t('strategyPage.candidates')} extra={<Button type="primary" icon={<PlayCircleOutlined />} loading={running} onClick={() => void runSelection()}>{t('strategyPage.select')}</Button>}><Table<Ranking> rowKey="stockCode" loading={loading || tradingLoading} dataSource={rankings} pagination={{ defaultPageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100], showTotal: (total) => `共 ${total} 只` }} columns={[{ title: '排名', dataIndex: 'rank', width: 72 }, { title: '股票', dataIndex: 'stockName', render: (value: string, row: Ranking) => `${value} (${row.stockCode})` }, { title: '价格', dataIndex: 'currentPrice', width: 100, render: (value?: number) => value === undefined || value === null ? '-' : `¥${value.toFixed(2)}` }, { title: 'LSTM', dataIndex: 'lstmScore', render: (value: number) => percent(value) }, { title: '情感', dataIndex: 'sentimentScore', render: (value: number) => value.toFixed(3) }, { title: '综合得分', dataIndex: 'totalScore', render: (value: number) => <Tag color={value >= 0.7 ? 'green' : value >= 0.5 ? 'blue' : 'default'}>{value.toFixed(3)}</Tag> }, { title: '持仓', dataIndex: 'held', render: (value?: boolean) => value === undefined ? <Tag>未知</Tag> : <Tag color={value ? 'blue' : 'default'}>{value ? '已持有' : '未持有'}</Tag> }, { title: '说明', dataIndex: 'reason' }]} locale={{ emptyText: '暂无交易候选' }} /></Card>
  </div>);
};

export default StrategyConfig;
// AI_GENERATE_END --------
