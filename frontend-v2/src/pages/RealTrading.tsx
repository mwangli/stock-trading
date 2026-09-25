// AI_GENERATE_START -
import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Empty, InputNumber, Modal, Space, Table, Tag, Typography, message } from 'antd';
import { ReloadOutlined, SafetyCertificateOutlined, ShoppingCartOutlined } from '@ant-design/icons';
import request from '../utils/request';

const { Title, Text } = Typography;

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

interface TradingStatus {
  mode: 'LIVE_MANUAL' | 'LIVE_AUTO';
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

interface TradeExecution {
  success: boolean;
  orderId?: string;
  stockCode?: string;
  direction?: string;
  price?: number;
  quantity?: number;
  status?: string;
  message?: string;
}

const RealTrading: React.FC = () => {
  const [status, setStatus] = useState<TradingStatus | null>(null);
  const [candidates, setCandidates] = useState<TradingCandidate[]>([]);
  const [amount, setAmount] = useState<number>(1000);
  const [loading, setLoading] = useState(false);
  const [submittingCode, setSubmittingCode] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const statusResponse = await request.get('/real-trading/status') as ApiResponse<TradingStatus>;
      if (!statusResponse.success) {
        throw new Error(statusResponse.message || '读取真实交易状态失败');
      }
      setStatus(statusResponse.data);
      if (!statusResponse.data.brokerAuthenticated) {
        setCandidates([]);
        setError('券商会话尚未建立，候选列表和真实持仓状态暂不可用');
        return;
      }
      const candidateResponse = await request.get('/real-trading/candidates') as ApiResponse<CandidateList>;
      if (!candidateResponse.success) {
        throw new Error(candidateResponse.message || '读取真实交易候选失败');
      }
      setCandidates(candidateResponse.data?.items ?? []);
    } catch (cause) {
      setCandidates([]);
      setError(cause instanceof Error ? cause.message : '读取真实交易数据失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const executeBuy = (candidate: TradingCandidate) => {
    Modal.confirm({
      title: '确认提交真实买入委托',
      content:         candidate.stockCode + ' ' + candidate.stockName + '，计划金额 ¥' + amount.toFixed(2) +
        '。提交后将直接调用真实券商账户。',
      okText: '确认下单',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setSubmittingCode(candidate.stockCode);
        try {
          const response = await request.post('/real-trading/manual-buy', {
            stockCode: candidate.stockCode,
            amount,
            monitorPrice: false,
          }) as ApiResponse<TradeExecution>;
          if (!response.success) {
            throw new Error(response.message || '真实买入未执行');
          }
          message.success(response.data?.message || '真实委托已提交');
          await loadData();
        } catch (cause) {
          message.error(cause instanceof Error ? cause.message : '真实买入执行失败');
        } finally {
          setSubmittingCode(null);
        }
      },
    });
  };

  const writeReady = Boolean(status?.brokerAuthenticated && status?.realWriteEnabled);
  const manualReady = writeReady && status?.mode === 'LIVE_MANUAL';
  const columns = [
    { title: '排名', dataIndex: 'rank', key: 'rank', width: 72 },
    { title: '股票', key: 'stock', render: (_: unknown, item: TradingCandidate) => item.stockCode + ' ' + (item.stockName ?? '') },
    { title: 'LSTM', dataIndex: 'lstmScore', key: 'lstmScore', render: (value: number) => value.toFixed(4) },
    { title: '情感', dataIndex: 'sentimentScore', key: 'sentimentScore', render: (value: number) => value.toFixed(4) },
    { title: '综合', dataIndex: 'totalScore', key: 'totalScore', render: (value: number) => value.toFixed(4) },
    { title: '持仓', dataIndex: 'held', key: 'held', render: (held: boolean) => <Tag color={held ? 'blue' : 'default'}>{held ? '已持有' : '未持有'}</Tag> },
    { title: '依据', dataIndex: 'reason', key: 'reason' },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, item: TradingCandidate) => (
        <Button
          danger
          icon={<ShoppingCartOutlined />}
          disabled={!manualReady || item.held}
          loading={submittingCode === item.stockCode}
          onClick={() => executeBuy(item)}
        >
          真实买入
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <Title level={2} className="!mb-1 !text-white"><SafetyCertificateOutlined className="mr-2" />真实交易</Title>
          <Text className="text-gray-400">模型候选、人工确认和真实券商委托</Text>
        </div>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadData()}>刷新</Button>
      </div>

      {error && <Alert type="warning" showIcon message={error} />}
      <Card>
        <Space wrap size="large">
          <span>模式：<Tag color={status?.mode === 'LIVE_AUTO' ? 'orange' : 'blue'}>{status?.mode ?? '-'}</Tag></span>
          <span>券商会话：<Tag color={status?.brokerAuthenticated ? 'green' : 'red'}>{status?.brokerAuthenticated ? '有效' : '无效'}</Tag></span>
          <span>真实写入：<Tag color={status?.realWriteEnabled ? 'red' : 'default'}>{status?.realWriteEnabled ? '已开启' : '已关闭'}</Tag></span>
          <span>单笔金额：<InputNumber min={1000} step={100} value={amount} onChange={(value) => setAmount(value ?? 1000)} prefix="¥" /></span>
        </Space>
      </Card>

      <Alert
        type={writeReady ? 'error' : 'info'}
        showIcon
        message={writeReady ? '真实写入门禁已开启，确认操作会直接提交真实委托' : '真实写入门禁默认关闭，当前不会提交真实委托'}
      />

      <Card>
        <Table
          rowKey="stockCode"
          columns={columns}
          dataSource={candidates}
          loading={loading}
          pagination={false}
          locale={{ emptyText: <Empty description="当日暂无真实模型候选" /> }}
          scroll={{ x: 980 }}
        />
      </Card>
    </div>
  );
};

export default RealTrading;
// AI_GENERATE_END -
