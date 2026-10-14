// AI_GENERATE_START ----
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Col, Form, InputNumber, Row, Select, Space, Statistic, Switch, Table, Tag, Typography, message } from 'antd';
import { PlayCircleOutlined, ReloadOutlined, RollbackOutlined, SaveOutlined, SettingOutlined } from '@ant-design/icons';
import request from '../utils/request';
import { getApiErrorMessage, readApiData, type ApiResponse } from '../utils/api';
import { usePageHeader } from '../layouts/PageHeaderContext';

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
  lstmScore: number;
  sentimentScore: number;
  totalScore: number;
  rank: number;
  reason: string;
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
  netReturn?: number;
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

const formatDate = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '未记录';
const percent = (value: number) => `${(value * 100).toFixed(1)}%`;

const StrategyConfig: React.FC = () => {
  const [form] = Form.useForm<StrategyConfigValues>();
  const [config, setConfig] = useState<StrategyConfigValues | null>(null);
  const [state, setState] = useState<StrategyState | null>(null);
  const [rankings, setRankings] = useState<Ranking[]>([]);
  const [evaluation, setEvaluation] = useState<StrategyEvaluation | null>(null);
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
      const nextConfig = readApiData(configResponse, '读取策略配置失败') || defaultValues;
      form.setFieldsValue(nextConfig);
      setConfig(nextConfig);
      setState(readApiData(stateResponse, '读取策略状态失败'));
      setRankings(readApiData(rankingsResponse, '读取选股排名失败') || []);
      setEvaluation(readApiData(evaluationResponse, '读取策略评估失败'));
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '读取策略配置失败'));
      form.setFieldsValue(defaultValues);
    } finally {
      setLoading(false);
    }
  }, [form]);

  useEffect(() => { void loadData(); }, [loadData]);

  const saveConfig = async (values: StrategyConfigValues) => {
    setSaving(true);
    try {
      const response = await request.put('/strategy/config', values) as ApiResponse<StrategyConfigValues>;
      const saved = readApiData(response, '保存策略配置失败');
      setConfig(saved);
      form.setFieldsValue(saved);
      message.success(`策略配置已保存，当前版本 ${saved.version || '未标记'}`);
      await loadData();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '保存策略配置失败'));
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
      message.success('策略配置已恢复默认值');
      await loadData();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '恢复默认策略失败'));
    }
  };

  const runSelection = async () => {
    const topN = form.getFieldValue('topN') || 10;
    setRunning(true);
    try {
      const response = await request.post(`/strategy/select?n=${topN}`) as ApiResponse<{ success: boolean; topN: Ranking[] }> ;
      const result = readApiData(response, '执行选股失败');
      message.success(`选股完成，生成 ${result.topN?.length || 0} 条候选`);
      await loadData();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '执行选股失败'));
    } finally {
      setRunning(false);
    }
  };

  const toggleAutoSwitch = async (enabled: boolean) => {
    try {
      const response = await request.post(`/strategy/auto-switch?enabled=${enabled}`) as ApiResponse<StrategyState>;
      setState(readApiData(response, '更新自动切换开关失败'));
      message.success(enabled ? '规则自动切换已开启' : '规则自动切换已关闭');
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '更新自动切换开关失败'));
    }
  };

  const headerActions = useMemo(() => (<Space wrap><Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadData()}>刷新</Button><Button icon={<RollbackOutlined />} onClick={() => void resetConfig()}>恢复默认</Button><Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => form.submit()}>保存配置</Button></Space>), [form, loadData, loading, saving]);
  usePageHeader('管理选股评分、技术指标和交易信号参数', headerActions);

  const number = (label: string, name: keyof StrategyConfigValues, min: number, max: number, step: number, extra: string) => (<Form.Item label={label} name={name} extra={extra} rules={[{ required: true, message: `${label}不能为空` }]}><InputNumber min={min} max={max} step={step} className="!w-full" /></Form.Item>);

  return (<div className="app-page">
    <Row gutter={[16, 16]}>      <Col xs={24} md={6}><Card className="surface-card compact-card"><Statistic title="当前策略模式" value={state?.currentMode || config?.mode || 'BALANCED'} prefix={<SettingOutlined />} /><Text type="secondary">配置版本：{config?.version || state?.configVersion || '未标记'}</Text></Card></Col>
      <Col xs={24} md={8}><Card className="surface-card compact-card"><Statistic title="策略状态" value={config?.enabled ? '已启用' : '已停用'} /><Text type="secondary">最近切换：{formatDate(state?.lastSwitchTime)}</Text></Card></Col>
      <Col xs={24} md={6}><Card className="surface-card compact-card"><Statistic title="当前候选" value={rankings.length} suffix={config ? `/ ${config.topN}` : ''} /><Text type="secondary">最近更新时间：{formatDate(config?.updateTime)}</Text></Card></Col>
      <Col xs={24} md={4}><Card className="surface-card compact-card"><Text strong>自动切换</Text><div className="mt-3"><Switch checked={state?.autoSwitchEnabled ?? false} onChange={(checked) => void toggleAutoSwitch(checked)} checkedChildren="开" unCheckedChildren="关" /></div><Text type="secondary">最近：{formatDate(state?.lastAutoSwitchTime)}</Text></Card></Col></Row>
    <Alert className="mb-4" type="info" showIcon message="配置边界" description="参数表单只影响策略评分和候选生成；自动切换开关仅控制策略自动切换任务，不修改系统采集、模型训练、券商凭据或真实委托写入开关。" />
    <Card className="surface-card" title="策略模式样本外评估" extra={<Text type="secondary">{formatDate(evaluation?.calculatedAt)}</Text>}>
      <Alert className="mb-4" type={evaluation?.modes.some((item) => item.status === 'AVAILABLE') ? 'success' : 'warning'} showIcon message={evaluation?.summary || '尚未执行策略评估'} />
      <Table<StrategyModeEvaluation>
        rowKey="mode"
        loading={loading}
        dataSource={evaluation?.modes || []}
        pagination={false}
        scroll={{ x: 1180 }}
        columns={[
          { title: '模式', dataIndex: 'modeName', width: 110 },
          { title: '结论状态', dataIndex: 'status', width: 130, render: (value: string) => <Tag color={value === 'AVAILABLE' ? 'green' : value === 'NOT_EVALUATED' ? 'default' : 'orange'}>{value === 'AVAILABLE' ? '可评估' : value === 'NOT_EVALUATED' ? '未评估' : '样本不足'}</Tag> },
          { title: '批次/候选', width: 120, render: (_, row) => row.batchCount + '/' + row.sampleCount },
          { title: '净收益', dataIndex: 'netReturn', width: 100, render: (value?: number) => value === undefined || value === null ? '-' : percent(value) },
          { title: '最大回撤', dataIndex: 'maxDrawdown', width: 100, render: (value?: number) => value === undefined || value === null ? '-' : percent(value) },
          { title: '胜率', dataIndex: 'winRate', width: 100, render: (value?: number) => value === undefined || value === null ? '-' : percent(value) },
          { title: '盈亏比', dataIndex: 'profitLossRatio', width: 90, render: (value?: number) => value === undefined || value === null ? '-' : value.toFixed(2) },
          { title: '稳定性', dataIndex: 'stabilityScore', width: 100, render: (value?: number) => value === undefined || value === null ? '-' : percent(value) },
          { title: '数据门禁', width: 150, render: (_, row) => <Space size={4}><Tag color={row.dataComplete ? 'green' : 'orange'}>{row.dataComplete ? '数据完整' : '数据不完整'}</Tag><Tag color={row.sampleSufficient ? 'green' : 'orange'}>{row.sampleSufficient ? '样本充分' : '样本不足'}</Tag></Space> },
          { title: '结论', dataIndex: 'conclusion', width: 360 },
        ]}
        locale={{ emptyText: '暂无策略评估结果' }}
      />
    </Card>
    <Form<StrategyConfigValues> form={form} layout="vertical" initialValues={defaultValues} onFinish={(values) => void saveConfig(values)}>
      <Card className="surface-card" title="模式与选股参数"><Row gutter={[24, 8]}>
        <Col xs={24} md={8}><Form.Item label="策略模式" name="mode"><Select options={[{ value: 'CONSERVATIVE', label: '保守模式' }, { value: 'BALANCED', label: '均衡模式' }, { value: 'AGGRESSIVE', label: '激进模式' }]} /></Form.Item></Col>
        <Col xs={24} md={8}>{number('LSTM 因子权重', 'lstmWeight', 0, 1, 0.05, '与情感权重合计必须为 1')}</Col>
        <Col xs={24} md={8}>{number('情感因子权重', 'sentimentWeight', 0, 1, 0.05, '与 LSTM 权重合计必须为 1')}</Col>
        <Col xs={24} md={8}>{number('选股数量', 'topN', 1, 100, 1, '候选股票最大数量')}</Col>
        <Col xs={24} md={8}>{number('最低综合得分', 'minScore', 0, 1, 0.01, '低于该分数的候选将被过滤')}</Col>
        <Col xs={24} md={8}><Form.Item label="策略总开关" name="enabled" valuePropName="checked"><Switch checkedChildren="开" unCheckedChildren="关" /></Form.Item></Col>
      </Row></Card>
      <Card className="surface-card" title="技术指标参数"><Row gutter={[24, 8]}>
        <Col xs={24} md={12}><Form.Item label="移动止损" name="trailingStopEnabled" valuePropName="checked"><Switch checkedChildren="启用" unCheckedChildren="停用" /></Form.Item>{number('移动止损权重', 'trailingStopWeight', 0, 1, 0.05, '技术指标权重')}</Col>
        <Col xs={24} md={12}>{number('回撤容忍度', 'trailingStopTolerance', 0, 1, 0.005, '例如 0.02 表示 2%')}{number('RSI 超买阈值', 'rsiOverboughtThreshold', 50, 100, 1, '超过阈值触发超买信号')}</Col>
        <Col xs={24} md={12}><Form.Item label="RSI 指标" name="rsiEnabled" valuePropName="checked"><Switch checkedChildren="启用" unCheckedChildren="停用" /></Form.Item>{number('RSI 权重', 'rsiWeight', 0, 1, 0.05, '技术指标权重')}</Col>
        <Col xs={24} md={12}><Form.Item label="成交量指标" name="volumeEnabled" valuePropName="checked"><Switch checkedChildren="启用" unCheckedChildren="停用" /></Form.Item>{number('成交量权重', 'volumeWeight', 0, 1, 0.05, '技术指标权重')}{number('成交量萎缩阈值', 'volumeShrinkThreshold', 0, 1, 0.05, '相对均量比例')}</Col>
        <Col xs={24} md={12}><Form.Item label="布林带指标" name="bollingerEnabled" valuePropName="checked"><Switch checkedChildren="启用" unCheckedChildren="停用" /></Form.Item>{number('布林带权重', 'bollingerWeight', 0, 1, 0.05, '技术指标权重')}{number('布林带突破阈值', 'bollingerBreakoutThreshold', 0, 2, 0.001, '价格相对上轨的触发系数')}</Col>
      </Row></Card>
      <Card className="surface-card" title="收益退出阈值"><Row gutter={[24, 8]}>{number('高收益阈值', 'highReturnThreshold', -100, 100, 1, '高收益场景退出阈值')}{number('正常收益阈值', 'normalReturnThreshold', -100, 100, 1, '正常收益场景退出阈值')}{number('低收益阈值', 'lowReturnThreshold', -100, 100, 1, '低收益场景退出阈值')}{number('亏损阈值', 'lossReturnThreshold', -100, 100, 1, '亏损场景退出阈值')}</Row></Card>
    </Form>
    <Card className="surface-card" title="最近选股排名" extra={<Button type="primary" icon={<PlayCircleOutlined />} loading={running} onClick={() => void runSelection()}>立即选股</Button>}><Table rowKey="stockCode" loading={loading} dataSource={rankings} pagination={{ pageSize: 10 }} columns={[{ title: '排名', dataIndex: 'rank', width: 72 }, { title: '股票', dataIndex: 'stockName', render: (value: string, row: Ranking) => `${value} (${row.stockCode})` }, { title: 'LSTM', dataIndex: 'lstmScore', render: (value: number) => percent(value) }, { title: '情感', dataIndex: 'sentimentScore', render: (value: number) => value.toFixed(3) }, { title: '综合得分', dataIndex: 'totalScore', render: (value: number) => <Tag color={value >= 0.7 ? 'green' : value >= 0.5 ? 'blue' : 'default'}>{value.toFixed(3)}</Tag> }, { title: '说明', dataIndex: 'reason' }]} locale={{ emptyText: '暂无选股排名' }} /></Card>
  </div>);
};

export default StrategyConfig;
// AI_GENERATE_END ----
