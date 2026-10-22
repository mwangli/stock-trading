// AI_GENERATE_START ---------------
import React, { useCallback, useState } from 'react';
import {
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
import request from '../../utils/request';
import { getApiErrorMessage, readApiData, type ApiResponse } from '../../utils/api';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import { useTranslation } from 'react-i18next';
import { localizedText } from '../../utils/localizedText';
import { allSentimentTestCases, pickRandomTestCase, type SentimentTestCase } from '../../mock/sentiment/sentimentTestCases';

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
  eventType?: string;
  impactDirection?: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
  impactMagnitude?: number;
  newsImpactScore?: number;
  effectiveHorizonHours?: number;
  actionable?: boolean;
  strategyAction?: 'ENHANCE' | 'REDUCE' | 'NO_ACTION' | 'RISK_BLOCK';
  strongNegative?: boolean;
}

interface NewsSample {
  title?: string;
  content?: string;
  stockCode?: string;
  stockName?: string;
  source?: string;
  category?: string;
  publishTime?: string;
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

const formatPercent = (value?: number | null) => value === undefined || value === null
  ? localizedText('未提供', 'Not provided')
  : `${(value * 100).toFixed(2)}%`;

interface CompactInfoItemProps {
  label: React.ReactNode;
  value: React.ReactNode;
  copyableText?: string;
  className?: string;
}

const CompactInfoItem: React.FC<CompactInfoItemProps> = ({ label, value, copyableText, className = '' }) => (
  <div className={`min-w-0 ${className}`}>
    <div className="mb-1 text-xs leading-4 text-[var(--text-muted)]">{label}</div>
    <Text
      className="!block !max-w-full text-sm leading-5"
      copyable={copyableText ? { text: copyableText } : false}
      ellipsis={{ tooltip: value }}
    >
      {value}
    </Text>
  </div>
);

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

const eventTypeText: Record<string, string> = {
  EARNINGS: '业绩',
  MATERIAL_BUSINESS: '重大业务',
  SHAREHOLDER_OR_EXECUTIVE: '股东/高管',
  REGULATORY_OR_LITIGATION: '监管/诉讼',
  CAPITAL_RISK: '资本风险',
  GENERAL_ANNOUNCEMENT: '一般公告',
  GENERAL_NEWS: '一般新闻',
};

const directionText: Record<string, { text: string; color: string }> = {
  BULLISH: { text: '偏多', color: 'green' },
  BEARISH: { text: '偏空', color: 'red' },
  NEUTRAL: { text: '无明显方向', color: 'blue' },
};

const actionText: Record<string, { text: string; color: string }> = {
  ENHANCE: { text: '增强', color: 'green' },
  REDUCE: { text: '减弱', color: 'orange' },
  NO_ACTION: { text: '不参与', color: 'default' },
  RISK_BLOCK: { text: '风险阻断', color: 'red' },
};

const SentimentModel: React.FC = () => {
  const { t } = useTranslation();
  const [health, setHealth] = useState<SentimentHealth | null>(null);
  const [, setHealthError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [sampleLoading, setSampleLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [text, setText] = useState('');
  const [analysis, setAnalysis] = useState<SentimentAnalyzeResult | null>(null);
  const [selectedCaseId, setSelectedCaseId] = useState<string | null>(null);
  const [sampleSource, setSampleSource] = useState<'database' | 'mock' | null>(null);
  const [, setSampleError] = useState<string | null>(null);

  const loadHealth = useCallback(async () => {
    setLoading(true);
    setHealthError(null);
    try {
      const healthResponse = await request.get('/modelSentiment/health') as ApiResponse<SentimentHealth>;
      setHealth(readApiData(healthResponse, '读取情感模型状态失败'));
    } catch (cause) {
      setHealthError(getApiErrorMessage(cause, '情感模型状态接口不可用'));
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void loadHealth();
  }, [loadHealth]);

  const applyMockTestCase = useCallback((testCase: SentimentTestCase) => {
    setText(testCase.text);
    setAnalysis(null);
    setSelectedCaseId(testCase.id);
    setSampleSource('mock');
  }, []);

  const loadRandomNewsSample = useCallback(async () => {
    setSampleLoading(true);
    setSampleError(null);
    try {
      const response = await request.get('/news/randomSample', { timeout: 10000 }) as ApiResponse<NewsSample | null>;
      const sample = readApiData(response, localizedText('读取信息表样本失败', 'Failed to load information-table sample'));
      const sampleText = [sample?.title, sample?.content]
        .filter((value): value is string => Boolean(value?.trim()))
        .join('\n\n')
        .trim();
      if (!sampleText) {
        setSampleError(localizedText('数据库暂无可用新闻样本，已切换为页面 Mock 样例', 'No usable database news sample was found; switched to a page Mock sample'));
        applyMockTestCase(pickRandomTestCase());
        return;
      }
      setText(sampleText);
      setAnalysis(null);
      setSelectedCaseId(null);
      setSampleSource('database');
    } catch (cause) {
      setSampleError(getApiErrorMessage(cause, localizedText('数据库新闻信息暂时不可用，已切换为页面 Mock 样例', 'Database news is temporarily unavailable; switched to a page Mock sample')));
      applyMockTestCase(pickRandomTestCase());
    } finally {
      setSampleLoading(false);
    }
  }, [applyMockTestCase]);

  React.useEffect(() => {
    void loadRandomNewsSample();
  }, [loadRandomNewsSample]);

  const analyzeText = async (contentOverride?: string) => {
    const content = (contentOverride ?? text).trim();
    if (!content) {
      message.warning(localizedText('请输入需要分析的新闻或公告文本', 'Enter news or announcement text to analyze'));
      return;
    }
    setAnalyzing(true);
    try {
      const response = await request.post('/modelSentiment/analyze', { text: content }) as ApiResponse<SentimentAnalyzeResult>;
      setAnalysis(readApiData(response, localizedText('情感分析失败', 'Sentiment analysis failed')));
      void loadHealth();
    } catch (cause) {
      message.error(getApiErrorMessage(cause, localizedText('情感分析失败', 'Sentiment analysis failed')));
    } finally {
      setAnalyzing(false);
    }
  };

  usePageHeader(t('sentimentPage.subtitle'));

  const currentLabel = analysis?.label ? labelMeta[analysis.label] : undefined;
  const probabilities = Object.entries(analysis?.probabilities ?? {}).sort((left, right) => right[1] - left[1]);
  const selectedCase = allSentimentTestCases.find((testCase) => testCase.id === selectedCaseId);
  const expectedMatch = Boolean(analysis?.label && selectedCase?.expectedLabel === analysis.label);
  const modelUnavailable = health?.status === 'NOT_READY' && !health.modelLoaded;

  return (
    <div className="space-y-4">
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
      </section>

      <Row gutter={[12, 12]}>
        <Col xs={24} xl={15}>
          <Card
            size="small"
            title={<Space size={8}><SafetyCertificateOutlined />{t('sentimentPage.artifact')}</Space>}
            loading={loading}
            className="h-full [&_.ant-card-body]:!p-4"
          >
            <div className="grid grid-cols-1 gap-x-5 gap-y-3 sm:grid-cols-2 xl:grid-cols-4">
              <CompactInfoItem label={localizedText('服务状态', 'Service Status')} value={health?.status ?? localizedText('未知', 'Unknown')} />
              <CompactInfoItem label={localizedText('模型版本', 'Model Version')} value={health?.modelVersion ?? localizedText('未配置', 'Not configured')} />
              <CompactInfoItem label={localizedText('制品大小', 'Artifact Size')} value={formatBytes(health?.totalSizeBytes)} />
              <CompactInfoItem label={localizedText('最近加载', 'Last Loaded')} value={formatDateTime(health?.lastLoadedTime)} />
              <CompactInfoItem label="Manifest" value={health?.manifestPresent ? localizedText('存在且可解析', 'Present and readable') : localizedText('缺失或无效', 'Missing or invalid')} />
              <CompactInfoItem
                className="sm:col-span-1 xl:col-span-3"
                label="Manifest SHA"
                value={health?.manifestSha256 ?? localizedText('未提供', 'Not provided')}
                copyableText={health?.manifestSha256}
              />
              <CompactInfoItem
                className="sm:col-span-2 xl:col-span-4"
                label={localizedText('制品目录', 'Artifact Directory')}
                value={health?.artifactPath ?? localizedText('未配置', 'Not configured')}
                copyableText={health?.artifactPath}
              />
            </div>
          </Card>
        </Col>
        <Col xs={24} xl={9}>
          <Card
            size="small"
            title={<Space size={8}><ExperimentOutlined />{t('sentimentPage.offline')}</Space>}
            loading={loading}
            className="h-full [&_.ant-card-body]:!p-4 [&_.ant-statistic-content]:!text-xl [&_.ant-statistic-content]:!leading-7"
          >
            <Row gutter={[12, 8]}>
              <Col xs={12} sm={6} xl={12} xxl={6}><Statistic title="Macro-F1" value={formatPercent(health?.macroF1)} /></Col>
              <Col xs={12} sm={6} xl={12} xxl={6}><Statistic title={localizedText('负面召回率', 'Negative Recall')} value={formatPercent(health?.negativeRecall)} /></Col>
              <Col xs={12} sm={6} xl={12} xxl={6}><Statistic title="ECE" value={formatPercent(health?.expectedCalibrationError)} /></Col>
              <Col xs={12} sm={6} xl={12} xxl={6}><Statistic title={localizedText('P95 延迟', 'P95 Latency')} value={health?.p95LatencyMs ?? localizedText('未提供', 'Not provided')} suffix={health?.p95LatencyMs === undefined ? undefined : 'ms'} /></Col>
            </Row>
            <div className="mt-3 border-t border-[var(--border-color)] pt-3 text-sm">
              <div className="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 xl:grid-cols-1 xxl:grid-cols-2">
                <div className="flex min-w-0 justify-between gap-3"><Text type="secondary">{localizedText('评估集', 'Evaluation Set')}</Text><Text ellipsis={{ tooltip: health?.evaluationDatasetVersion }} className="!max-w-[70%]">{health?.evaluationDatasetVersion ?? localizedText('未提供', 'Not provided')}</Text></div>
                <div className="flex justify-between gap-3"><Text type="secondary">{localizedText('样本数', 'Samples')}</Text><Text>{health?.evaluationSampleCount ?? localizedText('未提供', 'Not provided')}</Text></div>
              </div>
              <Paragraph ellipsis={{ rows: 2, tooltip: true }} className="!mb-0 !mt-2 text-xs leading-5" type="secondary">{health?.evaluationConclusion ?? localizedText('当前制品没有提供离线评估结论。', 'The current artifact has no offline evaluation conclusion.')}</Paragraph>
            </div>
          </Card>
        </Col>
      </Row>

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
              placeholder={localizedText('选择页面 Mock 样例', 'Select a page Mock sample')}
              options={allSentimentTestCases.map((testCase) => ({
                value: testCase.id,
                label: `${testCase.title} · ${localizedText(labelMeta[testCase.expectedLabel].text, labelMetaEn[testCase.expectedLabel])}`,
              }))}
              onChange={(caseId: string) => {
                const testCase = allSentimentTestCases.find((item) => item.id === caseId);
                if (testCase) {
                  applyMockTestCase(testCase);
                }
              }}
            />
            <Button icon={<SwapOutlined />} loading={sampleLoading} onClick={() => void loadRandomNewsSample()}>{localizedText('信息表随机换一条', 'Randomize from Information Table')}</Button>
          </Space>
          {sampleSource && (
            <Tag color={sampleSource === 'database' ? 'green' : 'gold'}>
              {sampleSource === 'database' ? localizedText('信息表真实样本', 'Database sample') : localizedText('页面 Mock 样例', 'Page Mock sample')}
            </Tag>
          )}
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
          autoSize={{ minRows: 8 }}
          maxLength={10000}
          showCount
          disabled={sampleLoading && !text}
          className="sentiment-textarea !min-h-[220px]"
          placeholder={localizedText('输入新闻、公告或事件描述', 'Enter a news item, announcement or event description')}
        />
        <div className="mt-5 flex flex-col items-stretch gap-3 sm:flex-row sm:items-center sm:justify-between">
          <Text type="secondary" className="text-xs leading-5">{localizedText('样例仅用于快速验证，不替代正式评估集', 'Samples are for quick validation and do not replace the formal evaluation set')}</Text>
          <Button className="self-end sm:shrink-0" type="primary" icon={<ExperimentOutlined />} loading={analyzing} disabled={modelUnavailable} onClick={() => void analyzeText()}>
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
            <Descriptions className="mt-4" bordered size="small" column={{ xs: 1, sm: 2, md: 4 }}>
              <Descriptions.Item label={localizedText('事件类型', 'Event Type')}>
                {analysis.eventType ? eventTypeText[analysis.eventType] ?? analysis.eventType : localizedText('未识别', 'Unknown')}
              </Descriptions.Item>
              <Descriptions.Item label={localizedText('影响方向', 'Impact Direction')}>
                {analysis.impactDirection && directionText[analysis.impactDirection]
                  ? <Tag color={directionText[analysis.impactDirection].color}>{directionText[analysis.impactDirection].text}</Tag>
                  : localizedText('未评估', 'Not evaluated')}
              </Descriptions.Item>
              <Descriptions.Item label={localizedText('影响强度', 'Impact Magnitude')}>
                {formatPercent(analysis.impactMagnitude)}
              </Descriptions.Item>
              <Descriptions.Item label={localizedText('有效窗口', 'Effective Window')}>
                {analysis.effectiveHorizonHours === undefined
                  ? localizedText('未评估', 'Not evaluated')
                  : `${analysis.effectiveHorizonHours} 小时`}
              </Descriptions.Item>
              <Descriptions.Item label={localizedText('新闻影响分数', 'News Impact Score')}>
                {analysis.newsImpactScore?.toFixed(3) ?? localizedText('未评估', 'Not evaluated')}
              </Descriptions.Item>
              <Descriptions.Item label={localizedText('策略动作', 'Strategy Action')}>
                {analysis.strategyAction && actionText[analysis.strategyAction]
                  ? <Tag color={actionText[analysis.strategyAction].color}>{actionText[analysis.strategyAction].text}</Tag>
                  : localizedText('未评估', 'Not evaluated')}
              </Descriptions.Item>
              <Descriptions.Item label={localizedText('参与决策', 'Decision Relevant')}>
                {analysis.actionable === undefined
                  ? localizedText('未评估', 'Not evaluated')
                  : analysis.actionable ? localizedText('是', 'Yes') : localizedText('否', 'No')}
              </Descriptions.Item>
              <Descriptions.Item label={localizedText('风险状态', 'Risk Status')}>
                <Tag color={analysis.strongNegative ? 'red' : 'default'}>
                  {analysis.strongNegative ? localizedText('强负面阻断', 'Risk Block') : localizedText('正常', 'Normal')}
                </Tag>
              </Descriptions.Item>
            </Descriptions>
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
// AI_GENERATE_END ---------------
