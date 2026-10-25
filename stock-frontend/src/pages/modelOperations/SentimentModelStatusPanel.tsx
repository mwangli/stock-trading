// AI_GENERATE_START -
import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Col, Row, Space, Statistic, Tag, Typography } from 'antd';
import { CheckCircleOutlined, MessageOutlined, ReloadOutlined, WarningOutlined } from '@ant-design/icons';
import request from '../../utils/request';
import { getApiErrorMessage, readApiData, type ApiResponse } from '../../utils/api';
import { localizedText } from '../../utils/localizedText';

const { Text } = Typography;

interface SentimentHealth {
  status: string;
  modelLoaded: boolean;
  artifactAvailable: boolean;
  checksumsVerified: boolean;
  modelVersion?: string;
  evaluationDatasetVersion?: string;
  evaluationSampleCount?: number;
  macroF1?: number;
  negativeRecall?: number;
  p95LatencyMs?: number;
  evaluationConclusion?: string;
  lastError?: string;
}

const formatPercent = (value?: number) => value === undefined
  ? localizedText('未提供', 'Not provided')
  : `${(value * 100).toFixed(2)}%`;

const SentimentModelStatusPanel: React.FC = () => {
  const [health, setHealth] = useState<SentimentHealth | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const loadHealth = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const response = await request.get('/modelSentiment/health') as ApiResponse<SentimentHealth>;
      setHealth(readApiData(response, localizedText('读取新闻模型状态失败', 'Failed to load news model status')));
    } catch (cause) {
      setHealth(null);
      setError(getApiErrorMessage(cause, localizedText('新闻模型状态接口不可用', 'News model status is unavailable')));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadHealth();
  }, [loadHealth]);

  return (
    <Card
      className="surface-card"
      loading={loading}
      title={<Space size={8}><MessageOutlined />{localizedText('新闻模型状态', 'News Model Status')}</Space>}
      extra={<Button type="text" icon={<ReloadOutlined />} aria-label={localizedText('刷新新闻模型状态', 'Refresh news model status')} onClick={() => void loadHealth()} />}
    >
      {error ? (
        <div className="border-l-2 border-amber-500 pl-3 text-sm text-amber-700">{error}</div>
      ) : (
        <div className="grid gap-5 xl:grid-cols-[minmax(260px,0.8fr)_minmax(0,1.2fr)]">
          <div className="min-w-0 border-r border-[var(--border-color)] pr-5 max-xl:border-b max-xl:border-r-0 max-xl:pb-5 max-xl:pr-0">
            <div className="flex flex-wrap items-center gap-2">
              <Tag color={health?.modelLoaded ? 'success' : 'error'} icon={health?.modelLoaded ? <CheckCircleOutlined /> : <WarningOutlined />}>
                {health?.modelLoaded ? localizedText('模型已加载', 'Model Loaded') : localizedText('模型未加载', 'Model Not Loaded')}
              </Tag>
              <Tag color={health?.artifactAvailable ? 'processing' : 'warning'}>
                {health?.artifactAvailable ? localizedText('制品可用', 'Artifact Available') : localizedText('制品未就绪', 'Artifact Not Ready')}
              </Tag>
              <Tag color={health?.checksumsVerified ? 'success' : 'warning'}>
                {health?.checksumsVerified ? localizedText('校验通过', 'Checksum Verified') : localizedText('校验未通过', 'Checksum Unverified')}
              </Tag>
            </div>
            <div className="mt-4">
              <Text type="secondary">{localizedText('模型版本', 'Model Version')}</Text>
              <div className="mt-1 truncate text-base font-semibold text-[var(--text-primary)]">{health?.modelVersion || localizedText('未配置', 'Not Configured')}</div>
            </div>
            <div className="mt-3 text-xs leading-5 text-[var(--text-secondary)]">
              {health?.lastError || health?.evaluationConclusion || localizedText('当前没有补充运行说明。', 'No additional runtime note is available.')}
            </div>
          </div>

          <Row gutter={[16, 12]}>
            <Col xs={12} lg={6}><Statistic title="Macro-F1" value={formatPercent(health?.macroF1)} /></Col>
            <Col xs={12} lg={6}><Statistic title={localizedText('负面召回率', 'Negative Recall')} value={formatPercent(health?.negativeRecall)} /></Col>
            <Col xs={12} lg={6}><Statistic title={localizedText('评估样本', 'Evaluation Samples')} value={health?.evaluationSampleCount ?? 0} /></Col>
            <Col xs={12} lg={6}><Statistic title={localizedText('P95 延迟', 'P95 Latency')} value={health?.p95LatencyMs ?? '-'} suffix={health?.p95LatencyMs === undefined ? undefined : 'ms'} /></Col>
            <Col span={24}>
              <Text type="secondary" className="text-xs">
                {localizedText('评估集', 'Evaluation Set')}：{health?.evaluationDatasetVersion || localizedText('未提供', 'Not provided')}
              </Text>
            </Col>
          </Row>
        </div>
      )}
    </Card>
  );
};

export default SentimentModelStatusPanel;
// AI_GENERATE_END -
