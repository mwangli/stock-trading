// AI_GENERATE_START -
import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Empty, Row, Statistic, Table, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import request from '../utils/request';

const { Title, Text } = Typography;

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

interface BrokerAccount {
  totalAssets: number;
  availableCash: number;
  frozenAmount: number;
  totalPosition: number;
}

interface BrokerPosition {
  stockCode: string;
  stockName: string;
  totalQuantity: number;
  availableQuantity: number;
  averageCost: number;
  currentPrice: number;
  marketValue: number;
}

interface BrokerPositionList {
  items: BrokerPosition[];
}

const Dashboard: React.FC = () => {
  const [account, setAccount] = useState<BrokerAccount | null>(null);
  const [positions, setPositions] = useState<BrokerPosition[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadBrokerData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [accountResponse, positionResponse] = await Promise.all([
        request.get('/broker-query/account') as Promise<ApiResponse<BrokerAccount>>,
        request.get('/broker-query/positions') as Promise<ApiResponse<BrokerPositionList>>,
      ]);
      if (!accountResponse.success || !positionResponse.success) {
        throw new Error(accountResponse.message || positionResponse.message || '券商数据读取失败');
      }
      setAccount(accountResponse.data);
      setPositions(positionResponse.data?.items ?? []);
    } catch (cause) {
      setAccount(null);
      setPositions([]);
      setError(cause instanceof Error ? cause.message : '券商数据读取失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadBrokerData();
  }, [loadBrokerData]);

  const columns = [
    { title: '股票代码', dataIndex: 'stockCode', key: 'stockCode' },
    { title: '股票名称', dataIndex: 'stockName', key: 'stockName' },
    { title: '持仓数量', dataIndex: 'totalQuantity', key: 'totalQuantity' },
    { title: '可卖数量', dataIndex: 'availableQuantity', key: 'availableQuantity' },
    { title: '成本价', dataIndex: 'averageCost', key: 'averageCost', render: (value: number) => `¥${Number(value).toFixed(2)}` },
    { title: '现价', dataIndex: 'currentPrice', key: 'currentPrice', render: (value: number) => `¥${Number(value).toFixed(2)}` },
    { title: '市值', dataIndex: 'marketValue', key: 'marketValue', render: (value: number) => `¥${Number(value).toFixed(2)}` },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Title level={2} className="!mb-1 !text-white">真实账户总览</Title>
          <Text className="text-gray-400">数据直接来自中信证券只读接口</Text>
        </div>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadBrokerData()}>
          刷新
        </Button>
      </div>

      {error && <Alert type="error" showIcon message={error} />}

      <Row gutter={[16, 16]}>
        <Col xs={24} md={6}><Card loading={loading}><Statistic title="总资产" value={account?.totalAssets} precision={2} prefix="¥" /></Card></Col>
        <Col xs={24} md={6}><Card loading={loading}><Statistic title="可用资金" value={account?.availableCash} precision={2} prefix="¥" /></Card></Col>
        <Col xs={24} md={6}><Card loading={loading}><Statistic title="持仓市值" value={account?.totalPosition} precision={2} prefix="¥" /></Card></Col>
        <Col xs={24} md={6}><Card loading={loading}><Statistic title="冻结资金" value={account?.frozenAmount} precision={2} prefix="¥" /></Card></Col>
      </Row>

      <Card title="当前持仓">
        <Table
          rowKey={(record) => record.stockCode}
          columns={columns}
          dataSource={positions}
          loading={loading}
          locale={{ emptyText: <Empty description="暂无真实持仓数据" /> }}
          pagination={false}
        />
      </Card>
    </div>
  );
};

export default Dashboard;
// AI_GENERATE_END -
