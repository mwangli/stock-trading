// AI_GENERATE_START --
import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Space, Tag, Typography } from 'antd';
import { CheckCircleOutlined, MessageOutlined, ReloadOutlined, WarningOutlined } from '@ant-design/icons';
import request from '../../utils/request';
import { getApiErrorMessage, readApiData, type ApiResponse } from '../../utils/api';
import { localizedText } from '../../utils/localizedText';

const { Text } = Typography;

interface SentimentHealth {
  status: string;
  modelLoaded: boolean;
  mengziClassifierStatus?: string;
  lastError?: string;
}

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
        <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <Tag color={health?.modelLoaded ? 'success' : 'error'} icon={health?.modelLoaded ? <CheckCircleOutlined /> : <WarningOutlined />}>
                {health?.modelLoaded ? localizedText('模型已加载', 'Model Loaded') : localizedText('模型未加载', 'Model Not Loaded')}
              </Tag>
              <Tag color={health?.mengziClassifierStatus === 'CLASSIFIER_LOADED' ? 'success' : 'default'}>
                Mengzi: {health?.mengziClassifierStatus === 'CLASSIFIER_LOADED'
                  ? localizedText('已加载', 'Loaded')
                  : localizedText('未加载', 'Not Loaded')}
              </Tag>
            </div>
            <div className="mt-3 text-xs leading-5 text-[var(--text-secondary)]">
              {health?.lastError || localizedText('当前没有加载错误。', 'No model loading error is currently recorded.')}
            </div>
        </div>
      )}
    </Card>
  );
};

export default SentimentModelStatusPanel;
// AI_GENERATE_END --
