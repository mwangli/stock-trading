import React, { useState, useEffect, useCallback } from 'react';
import { Switch, List, Tag, Button, Spin, message } from 'antd';
import {
  RocketOutlined,
  StockOutlined,
  SettingOutlined,
  CheckCircleOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { getAnalysisList, setStrategyActive, type AnalysisStrategyItem } from '../api/strategyAnalysis';

const Analysis: React.FC = () => {
  const { t } = useTranslation();
  const [strategies, setStrategies] = useState<AnalysisStrategyItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  const fetchList = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await getAnalysisList();
      setStrategies(list);
    } catch (e) {
      setError(t('strategyAnalysis.fetchError', '获取策略列表失败，请检查后端服务'));
      setStrategies([]);
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  const toggleStrategy = async (item: AnalysisStrategyItem) => {
    const nextActive = !item.active;
    setTogglingId(item.id);
    setStrategies((prev) =>
      prev.map((s) => (s.id === item.id ? { ...s, active: nextActive } : s))
    );
    try {
      await setStrategyActive(item.id, nextActive);
    } catch (e) {
      message.error(t('strategyAnalysis.toggleError', '更新失败，请重试'));
      setStrategies((prev) =>
        prev.map((s) => (s.id === item.id ? { ...s, active: item.active } : s))
      );
    } finally {
      setTogglingId(null);
    }
  };

  const renderStrategyList = (type: 'selection' | 'trading', title: string) => (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="glass p-6 rounded-2xl border border-white/10 flex-1"
    >
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-xl font-bold flex items-center gap-3">
          {type === 'selection' ? (
            <RocketOutlined className="text-[#00e396]" />
          ) : (
            <StockOutlined className="text-[#775dd0]" />
          )}
          {title}
        </h2>
        <Tag color={type === 'selection' ? 'success' : 'processing'} className="uppercase border-none">
          {type === 'selection' ? 'Daily' : 'Intraday'}
        </Tag>
      </div>

      <List
        itemLayout="horizontal"
        dataSource={strategies.filter((s) => s.type === type)}
        renderItem={(item) => (
          <List.Item
            className="border-b border-white/5 hover:bg-white/5 transition-colors p-4 rounded-lg mb-2"
            actions={[
              <Switch
                key="switch"
                checked={item.active}
                loading={togglingId === item.id}
                disabled={togglingId !== null && togglingId !== item.id}
                onChange={() => toggleStrategy(item)}
                className={item.active ? 'bg-[#00e396]' : 'bg-gray-700'}
              />,
              <Button
                key="setting"
                type="text"
                icon={<SettingOutlined />}
                className="text-gray-500 hover:text-white"
              />,
            ]}
          >
            <List.Item.Meta
              avatar={
                <div
                  className={`w-10 h-10 rounded-lg flex items-center justify-center text-lg ${
                    item.active ? 'bg-[#00e396]/20 text-[#00e396]' : 'bg-gray-800 text-gray-500'
                  }`}
                >
                  {item.active ? <CheckCircleOutlined /> : <StopOutlined />}
                </div>
              }
              title={
                <span className="text-white font-medium text-lg">{t(item.nameKey)}</span>
              }
              description={
                <div className="flex gap-4 mt-2 text-xs">
                  <span className="flex items-center gap-1">
                    <span className="text-gray-500">{t('strategyAnalysis.metrics.winRate')}:</span>
                    <span
                      className={
                        item.winRate > 50 ? 'text-[#00e396]' : 'text-[#ff4560]'
                      }
                    >
                      {item.winRate}%
                    </span>
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="text-gray-500">{t('strategyAnalysis.metrics.pnl')}:</span>
                    <span
                      className={
                        item.pnl >= 0 ? 'text-[#00e396]' : 'text-[#ff4560]'
                      }
                    >
                      ¥{item.pnl.toLocaleString()}
                    </span>
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="text-gray-500">
                      {t('strategyAnalysis.metrics.totalTrades')}:
                    </span>
                    <span className="text-white">{item.totalTrades}</span>
                  </span>
                </div>
              }
            />
          </List.Item>
        )}
      />
    </motion.div>
  );

  if (loading && strategies.length === 0) {
    return (
      <div className="flex items-center justify-center min-h-[320px]">
        <Spin size="large" tip={t('common.loading', '加载中...')} />
      </div>
    );
  }

  if (error && strategies.length === 0) {
    return (
      <div className="space-y-8 h-full">
        <div>
          <h1 className="text-3xl font-bold mb-2">{t('strategyAnalysis.title')}</h1>
          <p className="text-gray-400">{t('strategyAnalysis.subtitle')}</p>
        </div>
        <div className="glass p-8 rounded-2xl border border-white/10 text-center">
          <p className="text-[#ff4560] mb-4">{error}</p>
          <Button type="primary" onClick={() => fetchList()}>
            {t('common.retry', '重试')}
          </Button>
        </div>
      </div>
    );
  }

  const activeCount = strategies.filter((s) => s.active).length;
  const totalPnl = strategies.reduce((acc, s) => acc + s.pnl, 0);

  return (
    <div className="space-y-8 h-full">
      <div className="flex justify-between items-center mb-8">
        <div>
          <h1 className="text-3xl font-bold mb-2">{t('strategyAnalysis.title')}</h1>
          <p className="text-gray-400">{t('strategyAnalysis.subtitle')}</p>
        </div>
        <div className="flex gap-4">
          <div className="glass px-6 py-2 rounded-xl flex items-center gap-3 border border-white/10">
            <span className="text-gray-400 text-xs uppercase tracking-wider">
              Active Strategies
            </span>
            <span className="text-2xl font-bold text-[#00e396]">{activeCount}</span>
          </div>
          <div className="glass px-6 py-2 rounded-xl flex items-center gap-3 border border-white/10">
            <span className="text-gray-400 text-xs uppercase tracking-wider">Total P&L</span>
            <span
              className={`text-2xl font-bold ${totalPnl >= 0 ? 'text-[#00e396]' : 'text-[#ff4560]'}`}
            >
              {totalPnl >= 0 ? '+' : ''}¥{totalPnl.toLocaleString()}
            </span>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-start">
        {renderStrategyList('selection', t('strategyAnalysis.selection.title'))}
        {renderStrategyList('trading', t('strategyAnalysis.trading.title'))}
      </div>
    </div>
  );
};

export default Analysis;
