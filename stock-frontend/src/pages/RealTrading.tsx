// AI_GENERATE_START ---
import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Empty, Table, Tag, Typography } from 'antd';
import { ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import request from '../utils/request';

const { Title, Text } = Typography;

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

interface TradingStatus {
  realWriteEnabled: boolean;
  brokerAuthenticated: boolean;
  automaticExecutionEnabled: boolean;
}

interface TradingCandidate {
  stockCode: string;
  stockName: string;
  lstmScore: number;
  sentimentScore: number;
  totalScore: number;
  rank: number;
  reason: string;
  held: boolean;
}

interface CandidateList {
  items: TradingCandidate[];
}

/**
 * 自动真实交易监控页面。
 * 页面只展示运行状态和模型候选，不提供人工下单入口。
 */
const RealTrading: React.FC = () => {
  const [status, setStatus] = useState<TradingStatus | null>(null);
  const [candidates, setCandidates] = useState<TradingCandidate[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const statusResponse = await request.get('/real-trading/status') as ApiResponse<TradingStatus>;
      if (!statusResponse.success) {
        throw new Error(statusResponse.message || '读取自动交易状态失败');
      }
      setStatus(statusResponse.data);
      if (!statusResponse.data.brokerAuthenticated) {
        setCandidates([]);
        setError('券商会话尚未建立，自动交易不会执行');
        return;
      }
      const candidateResponse = await request.get('/real-trading/candidates') as ApiResponse<CandidateList>;
      if (!candidateResponse.success) {
        throw new Error(candidateResponse.message || '读取模型候选失败');
      }
      setCandidates(candidateResponse.data?.items ?? []);
    } catch (cause) {
      setCandidates([]);
      setError(cause instanceof Error ? cause.message : '读取自动交易数据失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const columns = [
    { title: '排名', dataIndex: 'rank', key: 'rank', width: 72 },
    { title: '股票', key: 'stock', render: (_: unknown, item: TradingCandidate) => item.stockCode + ' ' + (item.stockName ?? '') },
    { title: 'LSTM', dataIndex: 'lstmScore', key: 'lstmScore', render: (value: number) => value.toFixed(4) },
    { title: '情感', dataIndex: 'sentimentScore', key: 'sentimentScore', render: (value: number) => value.toFixed(4) },
    { title: '综合', dataIndex: 'totalScore', key: 'totalScore', render: (value: number) => value.toFixed(4) },
    { title: '持仓', dataIndex: 'held', key: 'held', render: (held: boolean) => <Tag color={held ? 'blue' : 'default'}>{held ? '已持有' : '未持有'}</Tag> },
    { title: '依据', dataIndex: 'reason', key: 'reason' },
  ];

  return (
    <div className="app-page">
      <div className="page-heading">
        <div>
          <Title level={2} className="page-title"><SafetyCertificateOutlined className="mr-2 text-blue-500" />自动真实交易</Title>
          <Text className="page-subtitle">模型自动选股、风控校验、真实委托和结果留痕</Text>
        </div>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadData()}>刷新</Button>
      </div>

      {error && <Alert type="warning" showIcon message={error} />}
      <div className="metric-strip">
        <div className="metric-strip-item"><div className="metric-label">券商会话</div><div className="metric-value">{status?.brokerAuthenticated ? '有效' : '无效'}</div><Tag color={status?.brokerAuthenticated ? 'green' : 'red'}>{status?.brokerAuthenticated ? '运行正常' : '需要登录'}</Tag></div>
        <div className="metric-strip-item"><div className="metric-label">真实写入</div><div className="metric-value">{status?.realWriteEnabled ? '已开启' : '已关闭'}</div><Tag color={status?.realWriteEnabled ? 'red' : 'blue'}>{status?.realWriteEnabled ? '生产写入' : '只读观察'}</Tag></div>
        <div className="metric-strip-item"><div className="metric-label">自动执行</div><div className="metric-value">{status?.automaticExecutionEnabled ? '就绪' : '停止'}</div><Tag color={status?.automaticExecutionEnabled ? 'green' : 'default'}>{status?.automaticExecutionEnabled ? '允许执行' : '受控状态'}</Tag></div>
        <div className="metric-strip-item"><div className="metric-label">今日候选</div><div className="metric-value">{candidates.length}</div><Tag color="blue">模型信号</Tag></div>
      </div>

      <Alert
        type={status?.automaticExecutionEnabled ? 'error' : 'info'}
        showIcon
        message={status?.automaticExecutionEnabled ? '自动交易已具备真实下单条件' : '自动交易未就绪，不会提交真实委托'}
      />

      <Card className="surface-card" title="今日模型候选">
        <Table
          rowKey="stockCode"
          columns={columns}
          dataSource={candidates}
          loading={loading}
          pagination={false}
          locale={{ emptyText: <Empty description="当日暂无模型候选" /> }}
          scroll={{ x: 860 }}
        />
      </Card>
    </div>
  );
};

export default RealTrading;
// AI_GENERATE_END ---
