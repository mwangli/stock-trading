// AI_GENERATE_START ---
import React, { useCallback, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Input,
  List,
  Progress,
  Row,
  Space,
  Statistic,
  Tag,
  Tabs,
  Typography,
  message,
} from 'antd';
import {
  CheckCircleOutlined,
  ExperimentOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import request from '../utils/request';
import { getApiErrorMessage, readApiData, type ApiResponse } from '../utils/api';
import { usePageHeader } from '../layouts/PageHeaderContext';

const { Paragraph, Text } = Typography;
const { TextArea } = Input;

interface SentimentHealth {
  status: string;
  service: string;
  modelLoaded: boolean;
  lastLoadedTime?: string;
  artifactAvailable: boolean;
  manifestPresent: boolean;
  checksumsVerified: boolean;
  modelVersion?: string;
  artifactPath?: string;
  totalSizeBytes?: number;
  manifestSha256?: string;
  evaluationDatasetVersion?: string;
  evaluationSampleCount?: number;
  macroF1?: number;
  negativeRecall?: number;
  expectedCalibrationError?: number;
  p95LatencyMs?: number;
  evaluationConclusion?: string;
  lastError?: string;
}

interface SentimentAnalyzeResult {
  success: boolean;
  message?: string;
  label?: string;
  score?: number;
  normalizedScore?: number;
  confidence?: number;
  probabilities?: Record<string, number>;
  text?: string;
  modelLoaded?: boolean;
}

interface SentimentEvaluation {
  evaluationRunId?: string;
  modelVersion?: string;
  datasetVersion?: string;
  status: string;
  sampleCount: number;
  correctCount: number;
  accuracy?: number;
  macroF1?: number;
  negativeRecall?: number;
  expectedCalibrationError?: number;
  p95LatencyMs?: number;
  conclusion: string;
  evaluatedAt?: string;
}

type SentimentLabel = 'positive' | 'neutral' | 'negative';

interface SentimentTestCase {
  id: string;
  title: string;
  text: string;
  expectedLabel: SentimentLabel;
  focus: string;
}

interface SentimentTestGroup {
  key: string;
  label: string;
  cases: SentimentTestCase[];
}

const formatDateTime = (value?: string) => {
  if (!value) {
    return '未加载';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false });
};

const formatBytes = (value?: number) => {
  if (value === undefined || value < 0) {
    return '未知';
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  if (value < 1024 * 1024 * 1024) {
    return `${(value / 1024 / 1024).toFixed(1)} MB`;
  }
  return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`;
};

const formatPercent = (value?: number) => value === undefined ? '未提供' : `${(value * 100).toFixed(2)}%`;

const labelMeta: Record<string, { text: string; color: string }> = {
  positive: { text: '正面', color: 'green' },
  neutral: { text: '中性', color: 'blue' },
  negative: { text: '负面', color: 'red' },
};

const sentimentTestGroups: SentimentTestGroup[] = [
  {
    key: 'positive',
    label: '正面样例',
    cases: [
      { id: 'positive-profit', title: '业绩增长', text: '公司发布公告称，预计今年净利润同比增长80%至100%，主要受主营业务销量提升带动。', expectedLabel: 'positive', focus: '利润与主营业务增长' },
      { id: 'positive-order', title: '长期订单', text: '公司核心产品获得海外客户长期订单，预计未来三年新增收入超过50亿元。', expectedLabel: 'positive', focus: '订单与收入增量' },
      { id: 'positive-buyback', title: '股份回购', text: '公司拟回购股份用于员工持股计划，回购金额不低于2亿元。', expectedLabel: 'positive', focus: '回购利好' },
      { id: 'positive-product', title: '产品突破', text: '公司成功完成新产品量产，产品已获得多家头部客户认证。', expectedLabel: 'positive', focus: '量产与客户认证' },
      { id: 'positive-increase', title: '股东增持', text: '公司控股股东计划增持公司股份，拟增持金额不超过1亿元。', expectedLabel: 'positive', focus: '控股股东增持' },
    ],
  },
  {
    key: 'neutral',
    label: '中性样例',
    cases: [
      { id: 'neutral-operation', title: '经营正常', text: '公司公告称，近期生产经营正常，未发生影响公司股票交易价格的重大事件。', expectedLabel: 'neutral', focus: '常规经营说明' },
      { id: 'neutral-board', title: '董事会公告', text: '公司召开董事会会议，审议通过了年度报告和内部管理制度。', expectedLabel: 'neutral', focus: '例行公司治理' },
      { id: 'neutral-flat', title: '收入持平', text: '公司披露季度报告，营业收入同比基本持平，主营业务未发生重大变化。', expectedLabel: 'neutral', focus: '经营平稳' },
      { id: 'neutral-volatility', title: '异常波动澄清', text: '公司发布股票交易异常波动公告，表示不存在应披露而未披露的重大事项。', expectedLabel: 'neutral', focus: '波动澄清' },
      { id: 'neutral-no-lawsuit', title: '风险排除', text: '公司公告称，当前没有涉及公司业务的重大诉讼、仲裁或资产重组事项。', expectedLabel: 'neutral', focus: '否定风险事项' },
      { id: 'neutral-mixed-profit', title: '增长伴随成本压力', text: '公司前三季度净利润预计增长，但同时受到原材料价格上涨影响，全年利润仍存在不确定性。', expectedLabel: 'neutral', focus: '利好与利空并存' },
      { id: 'neutral-mixed-order', title: '订单持续性风险', text: '公司获得大额订单，但公告同时提示客户集中度较高、订单能否持续存在不确定性。', expectedLabel: 'neutral', focus: '订单利好与集中度风险' },
      { id: 'neutral-price-risk', title: '涨幅风险提示', text: '公司股价连续上涨，公司提示投资者注意短期涨幅较大及市场交易风险。', expectedLabel: 'neutral', focus: '股价上涨不代表基本面利好' },
      { id: 'neutral-rumor', title: '重组传闻澄清', text: '公司发布澄清公告，否认市场关于资产重组和借壳上市的传闻。', expectedLabel: 'neutral', focus: '传闻否认' },
      { id: 'neutral-quality', title: '一次性收益', text: '公司利润同比增长，但主要来自一次性资产处置收益，主营业务盈利能力未明显改善。', expectedLabel: 'neutral', focus: '利润质量' },
      { id: 'neutral-unconfirmed', title: '未确认风险报道', text: '公司被媒体报道存在财务问题，但公司表示相关报道与事实不符，监管部门尚未立案。', expectedLabel: 'neutral', focus: '未经确认的风险信息' },
    ],
  },
  {
    key: 'negative',
    label: '负面样例',
    cases: [
      { id: 'negative-investigation', title: '立案调查', text: '公司因涉嫌信息披露违法违规，被监管机构立案调查。', expectedLabel: 'negative', focus: '监管调查' },
      { id: 'negative-inquiry', title: '监管问询', text: '公司收到交易所监管工作函，要求就收入确认和客户集中度问题进行说明。', expectedLabel: 'negative', focus: '财务与经营问询' },
      { id: 'negative-loss', title: '大额亏损', text: '公司预计本年度净利润亏损15亿元至20亿元，同比大幅下降。', expectedLabel: 'negative', focus: '业绩亏损' },
      { id: 'negative-orders', title: '订单下降停产', text: '公司主要客户订单明显减少，部分生产线暂时停产，经营压力进一步加大。', expectedLabel: 'negative', focus: '订单与生产风险' },
      { id: 'negative-freeze', title: '股份冻结', text: '公司控股股东所持股份被司法冻结，相关事项可能影响公司控制权稳定。', expectedLabel: 'negative', focus: '控制权风险' },
      { id: 'negative-debt', title: '债务逾期', text: '公司债务逾期金额持续增加，部分金融机构已提起诉讼。', expectedLabel: 'negative', focus: '债务与诉讼风险' },
      { id: 'negative-recall', title: '产品召回', text: '公司产品因质量问题被召回，监管部门要求限期整改。', expectedLabel: 'negative', focus: '产品质量事故' },
      { id: 'negative-delisting', title: '退市风险', text: '公司公告称，因重大违法行为可能触及退市风险警示。', expectedLabel: 'negative', focus: '重大违法与退市' },
    ],
  },
  {
    key: 'boundary',
    label: '边界样例',
    cases: [
      { id: 'boundary-positive', title: '明确正面基准', text: '公司业绩大幅增长，净利润同比上升80%，并计划持续扩大产能。', expectedLabel: 'positive', focus: '强正面表达' },
      { id: 'boundary-negative', title: '明确负面基准', text: '公司涉嫌财务造假被证监会立案调查，股票存在重大退市风险。', expectedLabel: 'negative', focus: '强负面风险事件' },
      { id: 'boundary-neutral', title: '明确中性基准', text: '公司召开董事会并发布日常经营情况公告。', expectedLabel: 'neutral', focus: '无方向性事件' },
      { id: 'boundary-negation', title: '否定语义', text: '公司并未出现亏损，经营现金流保持稳定。', expectedLabel: 'positive', focus: '识别“未亏损”而非亏损' },
      { id: 'boundary-mixed', title: '利好伴随风险', text: '公司虽然获得订单，但客户集中度过高，订单持续性存在较大不确定性。', expectedLabel: 'neutral', focus: '混合情绪与风险提示' },
    ],
  },
];

const SentimentModel: React.FC = () => {
  const [health, setHealth] = useState<SentimentHealth | null>(null);
  const [evaluation, setEvaluation] = useState<SentimentEvaluation | null>(null);
  const [healthError, setHealthError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [text, setText] = useState('');
  const [analysis, setAnalysis] = useState<SentimentAnalyzeResult | null>(null);
  const [selectedCaseId, setSelectedCaseId] = useState<string | null>(null);
  const [analyzingCaseId, setAnalyzingCaseId] = useState<string | null>(null);

  const loadHealth = useCallback(async () => {
    setLoading(true);
    setHealthError(null);
    try {
      const [healthResponse, evaluationResponse] = await Promise.all([
        request.get('/model-sentiment/health') as Promise<ApiResponse<SentimentHealth>>,
        request.get('/model-sentiment/evaluation/latest') as Promise<ApiResponse<SentimentEvaluation>>,
      ]);
      setHealth(readApiData(healthResponse, '读取情感模型状态失败'));
      setEvaluation(readApiData(evaluationResponse, '读取情感模型独立评估失败'));
    } catch (cause) {
      setHealthError(getApiErrorMessage(cause, '情感模型状态接口不可用'));
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void loadHealth();
  }, [loadHealth]);

  const analyzeText = async (contentOverride?: string, caseId?: string) => {
    const content = (contentOverride ?? text).trim();
    if (!content) {
      message.warning('请输入需要分析的新闻或公告文本');
      return;
    }
    setAnalyzing(true);
    setAnalyzingCaseId(caseId ?? null);
    try {
      const response = await request.post('/model-sentiment/analyze', { text: content }) as ApiResponse<SentimentAnalyzeResult>;
      setAnalysis(readApiData(response, '情感分析失败'));
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '情感分析失败'));
    } finally {
      setAnalyzing(false);
      setAnalyzingCaseId(null);
    }
  };

  const analyzeTestCase = (testCase: SentimentTestCase) => {
    setText(testCase.text);
    setAnalysis(null);
    setSelectedCaseId(testCase.id);
    void analyzeText(testCase.text, testCase.id);
  };

  const headerActions = useMemo(() => (
    <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadHealth()}>
      刷新状态
    </Button>
  ), [loadHealth, loading]);

  usePageHeader('情感模型制品、离线评估与在线推理验证', headerActions);

  const currentLabel = analysis?.label ? labelMeta[analysis.label] : undefined;
  const probabilities = Object.entries(analysis?.probabilities ?? {}).sort((left, right) => right[1] - left[1]);
  const selectedCase = sentimentTestGroups
    .flatMap((group) => group.cases)
    .find((testCase) => testCase.id === selectedCaseId);
  const expectedMatch = Boolean(analysis?.label && selectedCase?.expectedLabel === analysis.label);

  return (
    <div className="space-y-4">
      {healthError && (
        <Alert type="error" showIcon message="情感模型状态读取失败" description={healthError} />
      )}

      <section className="rounded-lg border border-[var(--border-color)] bg-[var(--surface-color)] p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="text-base font-semibold text-[var(--text-primary)]">运行状态</div>
            <div className="mt-1 text-sm text-[var(--text-muted)]">区分真实模型推理、制品就绪和规则降级状态</div>
          </div>
          <Space wrap>
            <Tag color={health?.modelLoaded ? 'green' : 'default'} icon={health?.modelLoaded ? <CheckCircleOutlined /> : <WarningOutlined />}>
              {health?.modelLoaded ? '真实模型已加载' : '真实模型未加载'}
            </Tag>
            <Tag color={health?.artifactAvailable ? 'blue' : 'orange'}>
              {health?.artifactAvailable ? '制品可用' : '制品未就绪'}
            </Tag>
            <Tag color={health?.checksumsVerified ? 'green' : 'default'}>
              {health?.checksumsVerified ? 'SHA-256 已校验' : 'SHA-256 未校验'}
            </Tag>
          </Space>
        </div>
        {health?.lastError && <Alert className="mt-4" type="warning" showIcon message="最近检查异常" description={health.lastError} />}
      </section>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card title={<Space><SafetyCertificateOutlined />模型制品</Space>} loading={loading} className="h-full">
            <Descriptions column={{ xs: 1, sm: 2 }} size="small">
              <Descriptions.Item label="服务状态">{health?.status ?? '未知'}</Descriptions.Item>
              <Descriptions.Item label="模型版本">{health?.modelVersion ?? '未配置'}</Descriptions.Item>
              <Descriptions.Item label="制品大小">{formatBytes(health?.totalSizeBytes)}</Descriptions.Item>
              <Descriptions.Item label="最近加载">{formatDateTime(health?.lastLoadedTime)}</Descriptions.Item>
              <Descriptions.Item label="Manifest">{health?.manifestPresent ? '存在且可解析' : '缺失或无效'}</Descriptions.Item>
              <Descriptions.Item label="Manifest SHA">{health?.manifestSha256 ?? '未提供'}</Descriptions.Item>
              <Descriptions.Item label="制品目录" span={2}>
                <Text copyable={Boolean(health?.artifactPath)} className="break-all">{health?.artifactPath ?? '未配置'}</Text>
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
        <Col xs={24} xl={10}>
          <Card title={<Space><ExperimentOutlined />离线评估</Space>} loading={loading} className="h-full">
            <Row gutter={[12, 16]}>
              <Col span={12}><Statistic title="Macro-F1" value={formatPercent(health?.macroF1)} /></Col>
              <Col span={12}><Statistic title="负面召回率" value={formatPercent(health?.negativeRecall)} /></Col>
              <Col span={12}><Statistic title="ECE" value={formatPercent(health?.expectedCalibrationError)} /></Col>
              <Col span={12}><Statistic title="P95 延迟" value={health?.p95LatencyMs ?? '未提供'} suffix={health?.p95LatencyMs === undefined ? undefined : 'ms'} /></Col>
            </Row>
            <div className="mt-4 border-t border-[var(--border-color)] pt-3 text-sm">
              <div className="flex justify-between gap-3"><Text type="secondary">评估集</Text><Text>{health?.evaluationDatasetVersion ?? '未提供'}</Text></div>
              <div className="mt-2 flex justify-between gap-3"><Text type="secondary">样本数</Text><Text>{health?.evaluationSampleCount ?? '未提供'}</Text></div>
              <Paragraph className="!mb-0 !mt-3" type="secondary">{health?.evaluationConclusion ?? '当前制品没有提供离线评估结论。'}</Paragraph>
            </div>
          </Card>
        </Col>
      </Row>

      <Card title={<Space><ExperimentOutlined />项目独立评估</Space>} loading={loading}>
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <Tag color={evaluation?.status === 'PASSED' ? 'green' : evaluation?.status === 'OBSERVE' ? 'orange' : evaluation?.status === 'REJECTED' ? 'red' : 'default'}>{evaluation?.status || 'NOT_EVALUATED'}</Tag>
          <Text type="secondary">模型版本：{evaluation?.modelVersion || '未评估'}</Text>
          <Text type="secondary">评估集：{evaluation?.datasetVersion || '未提供'}</Text>
          <Text type="secondary">评估时间：{formatDateTime(evaluation?.evaluatedAt)}</Text>
        </div>
        <Row gutter={[12, 16]}>
          <Col xs={12} md={4}><Statistic title="样本数" value={evaluation?.sampleCount ?? 0} /></Col>
          <Col xs={12} md={4}><Statistic title="准确率" value={formatPercent(evaluation?.accuracy)} /></Col>
          <Col xs={12} md={4}><Statistic title="Macro-F1" value={formatPercent(evaluation?.macroF1)} /></Col>
          <Col xs={12} md={4}><Statistic title="负面召回率" value={formatPercent(evaluation?.negativeRecall)} /></Col>
          <Col xs={12} md={4}><Statistic title="ECE" value={formatPercent(evaluation?.expectedCalibrationError)} /></Col>
          <Col xs={12} md={4}><Statistic title="P95 延迟" value={evaluation?.p95LatencyMs ?? '未提供'} suffix={evaluation?.p95LatencyMs === undefined ? undefined : 'ms'} /></Col>
        </Row>
        <Paragraph className="!mb-0 !mt-4" type="secondary">{evaluation?.conclusion || '尚未执行项目人工标注集独立评估任务。'}</Paragraph>
      </Card>

      <section className="rounded-lg border border-[var(--border-color)] bg-[var(--surface-color)] p-4">
        <div className="mb-3">
          <div className="text-base font-semibold text-[var(--text-primary)]">单条文本验证</div>
          <div className="mt-1 text-sm text-[var(--text-muted)]">用于核对在线标签、置信度和真实模型加载状态，不替代正式评估集</div>
        </div>
        <Tabs
          className="mb-4"
          items={sentimentTestGroups.map((group) => ({
            key: group.key,
            label: `${group.label} (${group.cases.length})`,
            children: (
              <List
                className="max-h-[360px] overflow-y-auto pr-1"
                dataSource={group.cases}
                renderItem={(testCase) => (
                  <List.Item
                    key={testCase.id}
                    className={`!items-start !gap-3 rounded-md !px-3 ${selectedCaseId === testCase.id ? 'bg-blue-50 dark:bg-blue-950/30' : ''}`}
                    actions={[
                      <Button
                        key="analyze"
                        type="primary"
                        size="small"
                        icon={<PlayCircleOutlined />}
                        loading={analyzingCaseId === testCase.id}
                        onClick={() => analyzeTestCase(testCase)}
                      >
                        测试
                      </Button>,
                    ]}
                  >
                    <List.Item.Meta
                      title={(
                        <Space wrap size={6}>
                          <Text strong>{testCase.title}</Text>
                          <Tag color={labelMeta[testCase.expectedLabel].color}>
                            预期{labelMeta[testCase.expectedLabel].text}
                          </Tag>
                          <Text type="secondary" className="text-xs">{testCase.focus}</Text>
                        </Space>
                      )}
                      description={<Text className="text-sm">{testCase.text}</Text>}
                    />
                  </List.Item>
                )}
              />
            ),
          }))}
        />
        <TextArea
          value={text}
          onChange={(event) => {
            const nextText = event.target.value;
            setText(nextText);
            if (selectedCase?.text !== nextText) {
              setSelectedCaseId(null);
            }
          }}
          rows={5}
          maxLength={4000}
          showCount
          placeholder="输入新闻、公告或事件描述"
        />
        <div className="mt-3 flex justify-end">
          <Button type="primary" icon={<ExperimentOutlined />} loading={analyzing && !analyzingCaseId} onClick={() => void analyzeText()}>
            执行情感分析
          </Button>
        </div>

        {analysis && (
          <div className="mt-4 border-t border-[var(--border-color)] pt-4">
            <div className="flex flex-wrap items-center gap-3">
              <Tag color={currentLabel?.color ?? 'default'}>{currentLabel?.text ?? analysis.label ?? '未知'}</Tag>
              <Tag color={analysis.modelLoaded ? 'green' : 'orange'}>{analysis.modelLoaded ? '真实模型结果' : '规则降级结果'}</Tag>
              {selectedCase && (
                <>
                  <Tag color={labelMeta[selectedCase.expectedLabel].color}>预期{labelMeta[selectedCase.expectedLabel].text}</Tag>
                  <Tag color={expectedMatch ? 'green' : 'red'}>{expectedMatch ? '符合预期' : '与预期不一致'}</Tag>
                </>
              )}
              <Text>置信度：{formatPercent(analysis.confidence)}</Text>
              <Text>归一化得分：{analysis.normalizedScore?.toFixed(2) ?? '未提供'}</Text>
            </div>
            <Row gutter={[20, 12]} className="mt-4">
              {probabilities.map(([label, probability]) => (
                <Col xs={24} md={8} key={label}>
                  <div className="mb-1 flex justify-between text-sm">
                    <span>{labelMeta[label]?.text ?? label}</span>
                    <span>{formatPercent(probability)}</span>
                  </div>
                  <Progress percent={Number((probability * 100).toFixed(2))} showInfo={false} />
                </Col>
              ))}
            </Row>
          </div>
        )}
      </section>
    </div>
  );
};

export default SentimentModel;
// AI_GENERATE_END ---
