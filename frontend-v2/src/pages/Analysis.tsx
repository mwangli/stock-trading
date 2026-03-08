import React, { useState } from 'react';
import { Switch, List, Tag, Button } from 'antd';
import { 
  RocketOutlined, 
  StockOutlined, 
  SettingOutlined, 
  CheckCircleOutlined,
  StopOutlined
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';

const Analysis: React.FC = () => {
  const { t } = useTranslation();

  const [strategies, setStrategies] = useState([
    { id: 'doubleFactor', type: 'selection', active: true, name: t('strategyAnalysis.selection.doubleFactor'), winRate: 68.5, pnl: 12450, trades: 142 },
    { id: 'tplus1', type: 'trading', active: true, name: t('strategyAnalysis.trading.tplus1'), winRate: 72.1, pnl: 8920, trades: 315 },
    { id: 'stopLoss', type: 'trading', active: true, name: t('strategyAnalysis.trading.stopLoss'), winRate: 55.4, pnl: -1200, trades: 89 },
    { id: 'rsi', type: 'trading', active: false, name: t('strategyAnalysis.trading.rsi'), winRate: 48.2, pnl: 560, trades: 45 },
    { id: 'volume', type: 'trading', active: true, name: t('strategyAnalysis.trading.volume'), winRate: 61.8, pnl: 3400, trades: 112 },
    { id: 'bollinger', type: 'trading', active: false, name: t('strategyAnalysis.trading.bollinger'), winRate: 51.0, pnl: -450, trades: 23 },
  ]);

  const toggleStrategy = (id: string) => {
    setStrategies(prev => prev.map(s => s.id === id ? { ...s, active: !s.active } : s));
  };

  const renderStrategyList = (type: 'selection' | 'trading', title: string) => (
    <motion.div 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="glass p-6 rounded-2xl border border-white/10 flex-1"
    >
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-xl font-bold flex items-center gap-3">
          {type === 'selection' ? <RocketOutlined className="text-[#00e396]" /> : <StockOutlined className="text-[#775dd0]" />}
          {title}
        </h2>
        <Tag color={type === 'selection' ? 'success' : 'processing'} className="uppercase border-none">
          {type === 'selection' ? 'Daily' : 'Intraday'}
        </Tag>
      </div>

      <List
        itemLayout="horizontal"
        dataSource={strategies.filter(s => s.type === type)}
        renderItem={item => (
          <List.Item 
            className="border-b border-white/5 hover:bg-white/5 transition-colors p-4 rounded-lg mb-2"
            actions={[
              <Switch 
                checked={item.active} 
                onChange={() => toggleStrategy(item.id)}
                className={item.active ? 'bg-[#00e396]' : 'bg-gray-700'}
              />,
              <Button type="text" icon={<SettingOutlined />} className="text-gray-500 hover:text-white" />
            ]}
          >
            <List.Item.Meta
              avatar={
                <div className={`w-10 h-10 rounded-lg flex items-center justify-center text-lg ${item.active ? 'bg-[#00e396]/20 text-[#00e396]' : 'bg-gray-800 text-gray-500'}`}>
                  {item.active ? <CheckCircleOutlined /> : <StopOutlined />}
                </div>
              }
              title={<span className="text-white font-medium text-lg">{item.name}</span>}
              description={
                <div className="flex gap-4 mt-2 text-xs">
                  <span className="flex items-center gap-1">
                    <span className="text-gray-500">{t('strategyAnalysis.metrics.winRate')}:</span>
                    <span className={item.winRate > 50 ? 'text-[#00e396]' : 'text-[#ff4560]'}>{item.winRate}%</span>
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="text-gray-500">{t('strategyAnalysis.metrics.pnl')}:</span>
                    <span className={item.pnl >= 0 ? 'text-[#00e396]' : 'text-[#ff4560]'}>¥{item.pnl.toLocaleString()}</span>
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="text-gray-500">{t('strategyAnalysis.metrics.totalTrades')}:</span>
                    <span className="text-white">{item.trades}</span>
                  </span>
                </div>
              }
            />
          </List.Item>
        )}
      />
    </motion.div>
  );

  return (
    <div className="space-y-8 h-full">
      <div className="flex justify-between items-center mb-8">
        <div>
          <h1 className="text-3xl font-bold mb-2">{t('strategyAnalysis.title')}</h1>
          <p className="text-gray-400">{t('strategyAnalysis.subtitle')}</p>
        </div>
        <div className="flex gap-4">
            <div className="glass px-6 py-2 rounded-xl flex items-center gap-3 border border-white/10">
                <span className="text-gray-400 text-xs uppercase tracking-wider">Active Strategies</span>
                <span className="text-2xl font-bold text-[#00e396]">{strategies.filter(s => s.active).length}</span>
            </div>
            <div className="glass px-6 py-2 rounded-xl flex items-center gap-3 border border-white/10">
                <span className="text-gray-400 text-xs uppercase tracking-wider">Total P&L</span>
                <span className="text-2xl font-bold text-[#00e396]">+¥{strategies.reduce((acc, s) => acc + s.pnl, 0).toLocaleString()}</span>
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
