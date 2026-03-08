import React, { useState, useEffect, useCallback } from 'react';
import { Switch, List, Tag, Button, Spin, message, Modal, InputNumber } from 'antd';
import {
  RocketOutlined,
  StockOutlined,
  SettingOutlined,
  CheckCircleOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import {
  getAnalysisList,
  setStrategyActive,
  getStrategyConfig,
  updateStrategyConfig,
  type AnalysisStrategyItem,
  type StrategyConfigDto,
} from '../api/strategyAnalysis';

const Analysis: React.FC = () => {
  const { t } = useTranslation();
  const [strategies, setStrategies] = useState<AnalysisStrategyItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [configModalVisible, setConfigModalVisible] = useState(false);
  const [configModalStrategyId, setConfigModalStrategyId] = useState<string | null>(null);
  const [configModalNameKey, setConfigModalNameKey] = useState<string>('');
  const [configLoading, setConfigLoading] = useState(false);
  const [configSaving, setConfigSaving] = useState(false);
  const [currentConfig, setCurrentConfig] = useState<StrategyConfigDto | null>(null);

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

  const openConfigModal = useCallback(async (item: AnalysisStrategyItem) => {
    setConfigModalStrategyId(item.id);
    setConfigModalNameKey(item.nameKey);
    setConfigModalVisible(true);
    setCurrentConfig(null);
    setConfigLoading(true);
    try {
      const config = await getStrategyConfig();
      setCurrentConfig(config ?? {});
    } catch {
      message.error(t('strategyAnalysis.fetchError', '获取配置失败'));
      setCurrentConfig({});
    } finally {
      setConfigLoading(false);
    }
  }, [t]);

  const handleConfigSave = useCallback(async () => {
    if (!currentConfig) return;
    setConfigSaving(true);
    try {
      await updateStrategyConfig(currentConfig);
      message.success(t('strategyAnalysis.params.saveSuccess'));
      setConfigModalVisible(false);
    } catch {
      message.error(t('strategyAnalysis.params.saveError'));
    } finally {
      setConfigSaving(false);
    }
  }, [currentConfig, t]);

  const updateConfigField = useCallback(<K extends keyof StrategyConfigDto>(key: K, value: StrategyConfigDto[K]) => {
    setCurrentConfig((prev) => (prev ? { ...prev, [key]: value } : { [key]: value }));
  }, []);

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
                onClick={() => openConfigModal(item)}
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

      <Modal
        title={`${t('strategyAnalysis.params.title')} - ${configModalNameKey ? t(configModalNameKey) : ''}`}
        open={configModalVisible}
        onCancel={() => setConfigModalVisible(false)}
        onOk={handleConfigSave}
        okText={t('strategyAnalysis.params.save')}
        cancelText={t('strategyAnalysis.params.cancel')}
        confirmLoading={configSaving}
        destroyOnClose
        width={480}
      >
        {configLoading ? (
          <div className="py-8 flex justify-center">
            <Spin />
          </div>
        ) : currentConfig && configModalStrategyId ? (
          <div className="space-y-4 pt-2">
            {configModalStrategyId === 'doubleFactor' && (
              <>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.doubleFactor.lstmWeight')}</label>
                  <InputNumber
                    min={0}
                    max={1}
                    step={0.1}
                    value={currentConfig.lstmWeight}
                    onChange={(v) => updateConfigField('lstmWeight', v ?? 0)}
                    className="w-full"
                  />
                </div>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.doubleFactor.sentimentWeight')}</label>
                  <InputNumber
                    min={0}
                    max={1}
                    step={0.1}
                    value={currentConfig.sentimentWeight}
                    onChange={(v) => updateConfigField('sentimentWeight', v ?? 0)}
                    className="w-full"
                  />
                </div>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.doubleFactor.topN')}</label>
                  <InputNumber
                    min={1}
                    max={50}
                    value={currentConfig.topN}
                    onChange={(v) => updateConfigField('topN', v ?? 10)}
                    className="w-full"
                  />
                </div>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.doubleFactor.minScore')}</label>
                  <InputNumber
                    min={0}
                    max={1}
                    step={0.05}
                    value={currentConfig.minScore}
                    onChange={(v) => updateConfigField('minScore', v ?? 0.5)}
                    className="w-full"
                  />
                </div>
              </>
            )}
            {configModalStrategyId === 'tplus1' && (
              <>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.tplus1.highReturnThreshold')}</label>
                  <InputNumber
                    min={0}
                    max={20}
                    value={currentConfig.highReturnThreshold}
                    onChange={(v) => updateConfigField('highReturnThreshold', v ?? 3)}
                    className="w-full"
                  />
                </div>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.tplus1.normalReturnThreshold')}</label>
                  <InputNumber
                    min={0}
                    max={20}
                    value={currentConfig.normalReturnThreshold}
                    onChange={(v) => updateConfigField('normalReturnThreshold', v ?? 2)}
                    className="w-full"
                  />
                </div>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.tplus1.lowReturnThreshold')}</label>
                  <InputNumber
                    min={0}
                    max={20}
                    value={currentConfig.lowReturnThreshold}
                    onChange={(v) => updateConfigField('lowReturnThreshold', v ?? 1)}
                    className="w-full"
                  />
                </div>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.tplus1.lossReturnThreshold')}</label>
                  <InputNumber
                    min={-10}
                    max={0}
                    value={currentConfig.lossReturnThreshold}
                    onChange={(v) => updateConfigField('lossReturnThreshold', v ?? 0)}
                    className="w-full"
                  />
                </div>
              </>
            )}
            {configModalStrategyId === 'stopLoss' && (
              <>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.stopLoss.trailingStopWeight')}</label>
                  <InputNumber
                    min={0}
                    max={1}
                    step={0.05}
                    value={currentConfig.trailingStopWeight}
                    onChange={(v) => updateConfigField('trailingStopWeight', v ?? 0.25)}
                    className="w-full"
                  />
                </div>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.stopLoss.trailingStopTolerance')}</label>
                  <InputNumber
                    min={0}
                    max={0.2}
                    step={0.01}
                    value={currentConfig.trailingStopTolerance}
                    onChange={(v) => updateConfigField('trailingStopTolerance', v ?? 0.02)}
                    className="w-full"
                  />
                </div>
              </>
            )}
            {configModalStrategyId === 'rsi' && (
              <>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.rsi.rsiWeight')}</label>
                  <InputNumber
                    min={0}
                    max={1}
                    step={0.05}
                    value={currentConfig.rsiWeight}
                    onChange={(v) => updateConfigField('rsiWeight', v ?? 0.25)}
                    className="w-full"
                  />
                </div>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.rsi.rsiOverboughtThreshold')}</label>
                  <InputNumber
                    min={50}
                    max={100}
                    value={currentConfig.rsiOverboughtThreshold}
                    onChange={(v) => updateConfigField('rsiOverboughtThreshold', v ?? 70)}
                    className="w-full"
                  />
                </div>
              </>
            )}
            {configModalStrategyId === 'volume' && (
              <>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.volume.volumeWeight')}</label>
                  <InputNumber
                    min={0}
                    max={1}
                    step={0.05}
                    value={currentConfig.volumeWeight}
                    onChange={(v) => updateConfigField('volumeWeight', v ?? 0.25)}
                    className="w-full"
                  />
                </div>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.volume.volumeShrinkThreshold')}</label>
                  <InputNumber
                    min={0}
                    max={1}
                    step={0.05}
                    value={currentConfig.volumeShrinkThreshold}
                    onChange={(v) => updateConfigField('volumeShrinkThreshold', v ?? 0.5)}
                    className="w-full"
                  />
                </div>
              </>
            )}
            {configModalStrategyId === 'bollinger' && (
              <>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.bollinger.bollingerWeight')}</label>
                  <InputNumber
                    min={0}
                    max={1}
                    step={0.05}
                    value={currentConfig.bollingerWeight}
                    onChange={(v) => updateConfigField('bollingerWeight', v ?? 0.25)}
                    className="w-full"
                  />
                </div>
                <div>
                  <label className="block text-gray-400 text-sm mb-1">{t('strategyAnalysis.params.bollinger.bollingerBreakoutThreshold')}</label>
                  <InputNumber
                    min={0}
                    max={3}
                    step={0.1}
                    value={currentConfig.bollingerBreakoutThreshold}
                    onChange={(v) => updateConfigField('bollingerBreakoutThreshold', v ?? 1)}
                    className="w-full"
                  />
                </div>
              </>
            )}
          </div>
        ) : null}
      </Modal>
    </div>
  );
};

export default Analysis;
