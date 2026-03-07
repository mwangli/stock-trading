import React from 'react';
import { Button, Tag } from 'antd';
import { 
  ThunderboltOutlined, 
  ReadOutlined, 
  ReloadOutlined, 
  HistoryOutlined, 
  ExperimentOutlined,
  CheckCircleOutlined,
  SyncOutlined
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';

const Strategies: React.FC = () => {
  const { t } = useTranslation();

  const models = [
    {
      id: 'lstm',
      title: t('models.lstm.title'),
      desc: t('models.lstm.desc'),
      status: 'online',
      color: '#00e396',
      icon: <ThunderboltOutlined />,
      stats: [
        { label: t('models.lstm.accuracy'), value: '87.4%', color: '#00e396' },
        { label: t('models.lstm.loss'), value: '0.024', color: '#feb019' },
        { label: t('models.lstm.epochs'), value: '500', color: '#fff' }
      ]
    },
    {
      id: 'finbert',
      title: t('models.finbert.title'),
      desc: t('models.finbert.desc'),
      status: 'processing',
      color: '#ff4560',
      icon: <ReadOutlined />,
      stats: [
        { label: t('models.finbert.sentiment'), value: 'Bullish', color: '#00e396' },
        { label: t('models.finbert.confidence'), value: '92.1%', color: '#00e396' },
        { label: t('models.finbert.sources'), value: '14', color: '#fff' }
      ]
    }
  ];

  return (
    <div className="space-y-8">
      <div className="flex justify-between items-center mb-8">
        <div>
          <h1 className="text-3xl font-bold mb-2">{t('models.title')}</h1>
          <p className="text-gray-400">{t('models.subtitle')}</p>
        </div>
        <Button 
          type="primary" 
          size="large" 
          icon={<ReloadOutlined />} 
          className="bg-[#00e396] text-black border-none font-bold hover:bg-[#00c985]"
        >
          {t('models.actions.retrain')} All
        </Button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {models.map((model, index) => (
          <motion.div
            key={model.id}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: index * 0.1 }}
            className="relative glass rounded-2xl p-8 border border-white/10 overflow-hidden"
          >
             {/* Background Glow */}
             <div className={`absolute top-0 right-0 w-64 h-64 bg-[${model.color}]/5 blur-[80px] rounded-full pointer-events-none`} />

            <div className="flex justify-between items-start mb-6">
              <div className="flex items-center gap-4">
                <div className={`w-16 h-16 rounded-2xl flex items-center justify-center text-3xl bg-[${model.color}]/20 text-[${model.color}] shadow-lg shadow-[${model.color}]/20`}>
                  {model.icon}
                </div>
                <div>
                  <h2 className="text-2xl font-bold text-white">{model.title}</h2>
                  <div className="flex items-center gap-2 mt-1">
                    <Tag color={model.status === 'online' ? 'success' : 'processing'} className="border-none px-2 py-0.5">
                      {model.status === 'online' ? <CheckCircleOutlined /> : <SyncOutlined spin />} 
                      <span className="ml-1 uppercase">{model.status}</span>
                    </Tag>
                  </div>
                </div>
              </div>
            </div>

            <p className="text-gray-400 mb-8 h-12 text-lg leading-relaxed">{model.desc}</p>

            <div className="grid grid-cols-3 gap-4 mb-8 bg-white/5 p-4 rounded-xl border border-white/5">
              {model.stats.map((stat, i) => (
                <div key={i} className="text-center">
                  <div className="text-xs text-gray-500 uppercase mb-1">{stat.label}</div>
                  <div className="text-xl font-bold font-mono" style={{ color: stat.color }}>{stat.value}</div>
                </div>
              ))}
            </div>

            <div className="flex gap-3">
              <Button type="primary" icon={<ExperimentOutlined />} className="flex-1 bg-white/10 border-white/10 hover:bg-white/20 hover:border-white/30 h-10">
                {t('models.actions.backtest')}
              </Button>
              <Button icon={<HistoryOutlined />} className="flex-1 bg-transparent border-white/10 text-gray-400 hover:text-white hover:border-white h-10">
                {t('models.actions.logs')}
              </Button>
              <Button icon={<ReloadOutlined />} className="bg-transparent border-white/10 text-gray-400 hover:text-[#00e396] hover:border-[#00e396] h-10" />
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  );
};

export default Strategies;
