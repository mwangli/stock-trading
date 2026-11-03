// AI_GENERATE_START ------
import './styles.css';
import React, { useRef, useState } from 'react';
import { Tabs } from 'antd';
import { HistoryOutlined, LineChartOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import OrderHistoryTable from './OrderHistoryTable';
import TradeRecordTable from './TradeRecordTable';

const OrderRecords: React.FC = () => {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<'orders' | 'trades'>('orders');
  const swipeStartRef = useRef<{ x: number; y: number } | null>(null);

  usePageHeader(t('orderRecords.subtitle'));

  const handleTouchStart = (event: React.TouchEvent<HTMLDivElement>) => {
    if (event.touches.length !== 1) {
      swipeStartRef.current = null;
      return;
    }
    swipeStartRef.current = {
      x: event.touches[0].clientX,
      y: event.touches[0].clientY,
    };
  };

  const handleTouchEnd = (event: React.TouchEvent<HTMLDivElement>) => {
    const start = swipeStartRef.current;
    const end = event.changedTouches[0];
    swipeStartRef.current = null;
    if (!start || !end) return;

    const distanceX = end.clientX - start.x;
    const distanceY = end.clientY - start.y;
    if (Math.abs(distanceX) < 56 || Math.abs(distanceX) <= Math.abs(distanceY) * 1.25) return;

    if (distanceX < 0 && activeTab === 'orders') {
      setActiveTab('trades');
    } else if (distanceX > 0 && activeTab === 'trades') {
      setActiveTab('orders');
    }
  };

  return (
    <div className="app-page">
      <div
        className="surface-card order-records-shell order-records-swipe-area"
        onTouchStart={handleTouchStart}
        onTouchEnd={handleTouchEnd}
        onTouchCancel={() => { swipeStartRef.current = null; }}
      >
        <Tabs
          activeKey={activeTab}
          onChange={(key) => setActiveTab(key as 'orders' | 'trades')}
          animated={{ inkBar: true, tabPane: true }}
          tabBarGutter={28}
          items={[
            {
              key: 'orders',
              label: <span><HistoryOutlined />{t('orderRecords.tabs.orders')}</span>,
              children: <OrderHistoryTable />,
            },
            {
              key: 'trades',
              label: <span><LineChartOutlined />{t('orderRecords.tabs.trades')}</span>,
              children: <TradeRecordTable />,
            },
          ]}
        />
      </div>
    </div>
  );
};

export default OrderRecords;
// AI_GENERATE_END ------
