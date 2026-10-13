// AI_GENERATE_START -
import React, { useCallback, useMemo, useState } from 'react';
import axios from 'axios';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Input,
  Progress,
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
  WarningOutlined,
} from '@ant-design/icons';
import request from '../utils/request';
import { usePageHeader } from '../layouts/PageHeaderContext';

const { Paragraph, Text } = Typography;
const { TextArea } = Input;

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

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

const responseData = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (!response.success) {
    throw new Error(response.message || fallback);
  }
  return response.data;
};

const getErrorMessage = (cause: unknown, fallback: string) => {
  if (axios.isAxiosError<ApiResponse<unknown>>(cause)) {
    return cause.response?.data?.message || cause.message || fallback;
  }
  return cause instanceof Error ? cause.message : fallback;
};

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

const SentimentModel: React.FC = () => {
  const [health, setHealth] = useState<SentimentHealth | null>(null);
  const [healthError, setHealthError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [text, setText] = useState('');
  const [analysis, setAnalysis] = useState<SentimentAnalyzeResult | null>(null);

  const loadHealth = useCallback(async () => {
    setLoading(true);
    setHealthError(null);
    try {
      const response = await request.get('/model-sentiment/health') as ApiResponse<SentimentHealth>;
      setHealth(responseData(response, '读取情感模型状态失败'));
    } catch (cause) {
      setHealthError(getErrorMessage(cause, '情感模型状态接口不可用'));
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void loadHealth();
  }, [loadHealth]);

  const analyzeText = async () => {
    const content = text.trim();
    if (!content) {
      message.warning('请输入需要分析的新闻或公告文本');
      return;
    }
    setAnalyzing(true);
    try {
      const response = await request.post('/model-sentiment/analyze', { text: content }) as ApiResponse<SentimentAnalyzeResult>;
      setAnalysis(responseData(response, '情感分析失败'));
    } catch (cause) {
      message.error(getErrorMessage(cause, '情感分析失败'));
    } finally {
      setAnalyzing(false);
    }
  };

  const headerActions = useMemo(() => (
    <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadHealth()}>
      刷新状态
    </Button>
  ), [loadHealth, loading]);

  usePageHeader('情感模型制品、离线评估与在线推理验证', headerActions);

  const currentLabel = analysis?.label ? labelMeta[analysis.label] : undefined;
  const probabilities = Object.entries(analysis?.probabilities ?? {}).sort((left, right) => right[1] - left[1]);

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

      <section className="rounded-lg border border-[var(--border-color)] bg-[var(--surface-color)] p-4">
        <div className="mb-3">
          <div className="text-base font-semibold text-[var(--text-primary)]">单条文本验证</div>
          <div className="mt-1 text-sm text-[var(--text-muted)]">用于核对在线标签、置信度和真实模型加载状态，不替代正式评估集</div>
        </div>
        <TextArea
          value={text}
          onChange={(event) => setText(event.target.value)}
          rows={5}
          maxLength={4000}
          showCount
          placeholder="输入新闻、公告或事件描述"
        />
        <div className="mt-3 flex justify-end">
          <Button type="primary" icon={<ExperimentOutlined />} loading={analyzing} onClick={() => void analyzeText()}>
            执行情感分析
          </Button>
        </div>

        {analysis && (
          <div className="mt-4 border-t border-[var(--border-color)] pt-4">
            <div className="flex flex-wrap items-center gap-3">
              <Tag color={currentLabel?.color ?? 'default'}>{currentLabel?.text ?? analysis.label ?? '未知'}</Tag>
              <Tag color={analysis.modelLoaded ? 'green' : 'orange'}>{analysis.modelLoaded ? '真实模型结果' : '规则降级结果'}</Tag>
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
// AI_GENERATE_END -
