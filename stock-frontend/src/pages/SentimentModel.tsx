// AI_GENERATE_START -----
import React, { useCallback, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Input,
  Progress,
  Select,
  Row,
  Space,
  Statistic,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  CheckCircleOutlined,
  ExperimentOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SwapOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import request from '../utils/request';
import { getApiErrorMessage, readApiData, type ApiResponse } from '../utils/api';
import { usePageHeader } from '../layouts/PageHeaderContext';
import { useTranslation } from 'react-i18next';
import { localizedText } from '../utils/localizedText';

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
    return localizedText('未加载', 'Not loaded');
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false });
};

const formatBytes = (value?: number) => {
  if (value === undefined || value < 0) {
    return localizedText('未知', 'Unknown');
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  if (value < 1024 * 1024 * 1024) {
    return `${(value / 1024 / 1024).toFixed(1)} MB`;
  }
  return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`;
};

const formatPercent = (value?: number) => value === undefined ? localizedText('未提供', 'Not provided') : `${(value * 100).toFixed(2)}%`;

const labelMeta: Record<string, { text: string; color: string }> = {
  positive: { text: '正面', color: 'green' },
  neutral: { text: '中性', color: 'blue' },
  negative: { text: '负面', color: 'red' },
};

const labelMetaEn: Record<string, string> = {
  positive: 'Positive',
  neutral: 'Neutral',
  negative: 'Negative',
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

const allSentimentTestCases = sentimentTestGroups.flatMap((group) => group.cases);

const pickRandomTestCase = () => allSentimentTestCases[Math.floor(Math.random() * allSentimentTestCases.length)];

const SentimentModel: React.FC = () => {
  const { t } = useTranslation();
  const [health, setHealth] = useState<SentimentHealth | null>(null);
  const [evaluation, setEvaluation] = useState<SentimentEvaluation | null>(null);
  const [healthError, setHealthError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [initialTestCase] = useState(pickRandomTestCase);
  const [text, setText] = useState(initialTestCase.text);
  const [analysis, setAnalysis] = useState<SentimentAnalyzeResult | null>(null);
  const [selectedCaseId, setSelectedCaseId] = useState<string | null>(initialTestCase.id);

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

  const analyzeText = async (contentOverride?: string) => {
    const content = (contentOverride ?? text).trim();
    if (!content) {
      message.warning(localizedText('请输入需要分析的新闻或公告文本', 'Enter news or announcement text to analyze'));
      return;
    }
    setAnalyzing(true);
    try {
      const response = await request.post('/model-sentiment/analyze', { text: content }) as ApiResponse<SentimentAnalyzeResult>;
      setAnalysis(readApiData(response, localizedText('情感分析失败', 'Sentiment analysis failed')));
      void loadHealth();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('情感分析失败', 'Sentiment analysis failed')));
    } finally {
      setAnalyzing(false);
    }
  };

  const applyTestCase = (testCase: SentimentTestCase) => {
    setText(testCase.text);
    setAnalysis(null);
    setSelectedCaseId(testCase.id);
  };

  const randomizeTestCase = () => {
    const candidates = allSentimentTestCases.filter((testCase) => testCase.id !== selectedCaseId);
    applyTestCase(candidates[Math.floor(Math.random() * candidates.length)] ?? allSentimentTestCases[0]);
  };

  usePageHeader(t('sentimentPage.subtitle'));

  const currentLabel = analysis?.label ? labelMeta[analysis.label] : undefined;
  const probabilities = Object.entries(analysis?.probabilities ?? {}).sort((left, right) => right[1] - left[1]);
  const selectedCase = allSentimentTestCases.find((testCase) => testCase.id === selectedCaseId);
  const expectedMatch = Boolean(analysis?.label && selectedCase?.expectedLabel === analysis.label);

  return (
    <div className="space-y-4">
      {healthError && (
        <Alert type="error" showIcon message={localizedText('情感模型状态读取失败', 'Failed to load sentiment model status')} description={healthError} />
      )}

      <section className="rounded-lg border border-[var(--border-color)] bg-[var(--surface-color)] p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="text-base font-semibold text-[var(--text-primary)]">{t('sentimentPage.runStatus')}</div>
            <div className="mt-1 text-sm text-[var(--text-muted)]">{localizedText('区分真实模型推理、制品就绪和规则降级状态', 'Distinguish real model inference, artifact readiness and rule fallback')}</div>
          </div>
          <Space wrap>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadHealth()}>
              {localizedText('刷新状态', 'Refresh Status')}
            </Button>
            <Tag color={health?.modelLoaded ? 'green' : 'default'} icon={health?.modelLoaded ? <CheckCircleOutlined /> : <WarningOutlined />}>
              {health?.modelLoaded ? localizedText('真实模型已加载', 'Real model loaded') : localizedText('真实模型未加载', 'Real model not loaded')}
            </Tag>
            <Tag color={health?.artifactAvailable ? 'blue' : 'orange'}>
              {health?.artifactAvailable ? localizedText('制品可用', 'Artifact available') : localizedText('制品未就绪', 'Artifact not ready')}
            </Tag>
            <Tag color={health?.checksumsVerified ? 'green' : 'default'}>
              {health?.checksumsVerified ? 'SHA-256 verified' : 'SHA-256 not verified'}
            </Tag>
          </Space>
        </div>
        {health?.lastError && <Alert className="mt-4" type="warning" showIcon message={localizedText('最近检查异常', 'Latest check reported an error')} description={health.lastError} />}
      </section>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card title={<Space><SafetyCertificateOutlined />{t('sentimentPage.artifact')}</Space>} loading={loading} className="h-full">
            <Descriptions column={{ xs: 1, sm: 2 }} size="small">
              <Descriptions.Item label={localizedText('服务状态', 'Service Status')}>{health?.status ?? localizedText('未知', 'Unknown')}</Descriptions.Item>
              <Descriptions.Item label={localizedText('模型版本', 'Model Version')}>{health?.modelVersion ?? localizedText('未配置', 'Not configured')}</Descriptions.Item>
              <Descriptions.Item label={localizedText('制品大小', 'Artifact Size')}>{formatBytes(health?.totalSizeBytes)}</Descriptions.Item>
              <Descriptions.Item label={localizedText('最近加载', 'Last Loaded')}>{formatDateTime(health?.lastLoadedTime)}</Descriptions.Item>
              <Descriptions.Item label="Manifest">{health?.manifestPresent ? localizedText('存在且可解析', 'Present and readable') : localizedText('缺失或无效', 'Missing or invalid')}</Descriptions.Item>
              <Descriptions.Item label="Manifest SHA">{health?.manifestSha256 ?? localizedText('未提供', 'Not provided')}</Descriptions.Item>
              <Descriptions.Item label={localizedText('制品目录', 'Artifact Directory')} span={2}>
                <Text copyable={Boolean(health?.artifactPath)} className="break-all">{health?.artifactPath ?? localizedText('未配置', 'Not configured')}</Text>
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
        <Col xs={24} xl={10}>
          <Card title={<Space><ExperimentOutlined />{t('sentimentPage.offline')}</Space>} loading={loading} className="h-full">
            <Row gutter={[12, 16]}>
              <Col span={12}><Statistic title="Macro-F1" value={formatPercent(health?.macroF1)} /></Col>
              <Col span={12}><Statistic title={localizedText('负面召回率', 'Negative Recall')} value={formatPercent(health?.negativeRecall)} /></Col>
              <Col span={12}><Statistic title="ECE" value={formatPercent(health?.expectedCalibrationError)} /></Col>
              <Col span={12}><Statistic title={localizedText('P95 延迟', 'P95 Latency')} value={health?.p95LatencyMs ?? localizedText('未提供', 'Not provided')} suffix={health?.p95LatencyMs === undefined ? undefined : 'ms'} /></Col>
            </Row>
            <div className="mt-4 border-t border-[var(--border-color)] pt-3 text-sm">
              <div className="flex justify-between gap-3"><Text type="secondary">{localizedText('评估集', 'Evaluation Set')}</Text><Text>{health?.evaluationDatasetVersion ?? localizedText('未提供', 'Not provided')}</Text></div>
              <div className="mt-2 flex justify-between gap-3"><Text type="secondary">{localizedText('样本数', 'Samples')}</Text><Text>{health?.evaluationSampleCount ?? localizedText('未提供', 'Not provided')}</Text></div>
              <Paragraph className="!mb-0 !mt-3" type="secondary">{health?.evaluationConclusion ?? localizedText('当前制品没有提供离线评估结论。', 'The current artifact has no offline evaluation conclusion.')}</Paragraph>
            </div>
          </Card>
        </Col>
      </Row>

      <Card title={<Space><ExperimentOutlined />{localizedText('项目独立评估', 'Independent Project Evaluation')}</Space>} loading={loading}>
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <Tag color={evaluation?.status === 'PASSED' ? 'green' : evaluation?.status === 'OBSERVE' ? 'orange' : evaluation?.status === 'REJECTED' ? 'red' : 'default'}>{evaluation?.status || 'NOT_EVALUATED'}</Tag>
          <Text type="secondary">{localizedText('模型版本', 'Model Version')}：{evaluation?.modelVersion || localizedText('未评估', 'Not evaluated')}</Text>
          <Text type="secondary">{localizedText('评估集', 'Evaluation Set')}：{evaluation?.datasetVersion || localizedText('未提供', 'Not provided')}</Text>
          <Text type="secondary">{localizedText('评估时间', 'Evaluated At')}：{formatDateTime(evaluation?.evaluatedAt)}</Text>
        </div>
        <Row gutter={[12, 16]}>
          <Col xs={12} md={4}><Statistic title={localizedText('样本数', 'Samples')} value={evaluation?.sampleCount ?? 0} /></Col>
          <Col xs={12} md={4}><Statistic title={localizedText('准确率', 'Accuracy')} value={formatPercent(evaluation?.accuracy)} /></Col>
          <Col xs={12} md={4}><Statistic title="Macro-F1" value={formatPercent(evaluation?.macroF1)} /></Col>
          <Col xs={12} md={4}><Statistic title={localizedText('负面召回率', 'Negative Recall')} value={formatPercent(evaluation?.negativeRecall)} /></Col>
          <Col xs={12} md={4}><Statistic title="ECE" value={formatPercent(evaluation?.expectedCalibrationError)} /></Col>
          <Col xs={12} md={4}><Statistic title={localizedText('P95 延迟', 'P95 Latency')} value={evaluation?.p95LatencyMs ?? localizedText('未提供', 'Not provided')} suffix={evaluation?.p95LatencyMs === undefined ? undefined : 'ms'} /></Col>
        </Row>
        <Paragraph className="!mb-0 !mt-4" type="secondary">{evaluation?.conclusion || localizedText('尚未执行项目人工标注集独立评估任务。', 'No independent evaluation has been run on the project-labeled set.')}</Paragraph>
      </Card>

      <section className="rounded-lg border border-[var(--border-color)] bg-[var(--surface-color)] p-4">
        <div className="mb-3">
          <div className="text-base font-semibold text-[var(--text-primary)]">{t('sentimentPage.single')}</div>
          <div className="mt-1 text-sm text-[var(--text-muted)]">{localizedText('输入新闻、公告或事件描述，快速核对在线标签、置信度和真实模型加载状态', 'Enter a news item, announcement or event description to inspect the online label, confidence and model status')}</div>
        </div>
        <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <Space wrap size={8}>
            <Text type="secondary">{localizedText('快速样例', 'Quick Sample')}</Text>
            <Select
              className="min-w-[220px]"
              value={selectedCaseId ?? undefined}
              placeholder={localizedText('选择一条测试文本', 'Select a test text')}
              options={allSentimentTestCases.map((testCase) => ({
                value: testCase.id,
                label: `${testCase.title} · ${localizedText(labelMeta[testCase.expectedLabel].text, labelMetaEn[testCase.expectedLabel])}`,
              }))}
              onChange={(caseId: string) => {
                const testCase = allSentimentTestCases.find((item) => item.id === caseId);
                if (testCase) {
                  applyTestCase(testCase);
                }
              }}
            />
            <Button icon={<SwapOutlined />} onClick={randomizeTestCase}>{localizedText('随机换一条', 'Randomize')}</Button>
          </Space>
          {selectedCase && (
            <Text type="secondary" className="text-xs">{selectedCase.focus}</Text>
          )}
        </div>
        <TextArea
          value={text}
          onChange={(event) => {
            const nextText = event.target.value;
            setText(nextText);
            if (selectedCase?.text !== nextText) {
              setSelectedCaseId(null);
            }
          }}
          autoSize={{ minRows: 8, maxRows: 14 }}
          maxLength={4000}
          showCount
          className="!min-h-[220px]"
          placeholder={localizedText('输入新闻、公告或事件描述', 'Enter a news item, announcement or event description')}
        />
        <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
          <Text type="secondary" className="text-xs">{localizedText('样例仅用于快速验证，不替代正式评估集', 'Samples are for quick validation and do not replace the formal evaluation set')}</Text>
          <Button type="primary" icon={<ExperimentOutlined />} loading={analyzing} onClick={() => void analyzeText()}>
            {localizedText('执行情感分析', 'Run Sentiment Analysis')}
          </Button>
        </div>

        {analysis && (
          <div className="mt-4 border-t border-[var(--border-color)] pt-4">
            <div className="flex flex-wrap items-center gap-3">
              <Tag color={currentLabel?.color ?? 'default'}>{currentLabel ? localizedText(currentLabel.text, labelMetaEn[analysis.label ?? '']) : analysis.label ?? localizedText('未知', 'Unknown')}</Tag>
              <Tag color={analysis.modelLoaded ? 'green' : 'orange'}>{analysis.modelLoaded ? localizedText('真实模型结果', 'Real Model Result') : localizedText('规则降级结果', 'Rule Fallback Result')}</Tag>
              {selectedCase && (
                <>
                  <Tag color={labelMeta[selectedCase.expectedLabel].color}>{localizedText(`预期${labelMeta[selectedCase.expectedLabel].text}`, `Expected ${labelMetaEn[selectedCase.expectedLabel]}`)}</Tag>
                  <Tag color={expectedMatch ? 'green' : 'red'}>{expectedMatch ? localizedText('符合预期', 'Matches Expected') : localizedText('与预期不一致', 'Does Not Match')}</Tag>
                </>
              )}
              <Text>{localizedText('置信度', 'Confidence')}：{formatPercent(analysis.confidence)}</Text>
              <Text>{localizedText('归一化得分', 'Normalized Score')}：{analysis.normalizedScore?.toFixed(2) ?? localizedText('未提供', 'Not provided')}</Text>
            </div>
            <Row gutter={[20, 12]} className="mt-4">
              {probabilities.map(([label, probability]) => (
                <Col xs={24} md={8} key={label}>
                  <div className="mb-1 flex justify-between text-sm">
                    <span>{labelMeta[label] ? localizedText(labelMeta[label].text, labelMetaEn[label] ?? label) : label}</span>
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
// AI_GENERATE_END -----
