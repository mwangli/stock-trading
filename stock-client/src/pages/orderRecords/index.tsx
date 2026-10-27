// AI_GENERATE_START --
import './styles.css';
import React from 'react';
import { Tabs } from 'antd';
import { HistoryOutlined, LineChartOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import OrderHistoryTable from './OrderHistoryTable';
import TradeRecordTable from './TradeRecordTable';

const OrderRecords: React.FC = () => {
  const { t } = useTranslation();

  usePageHeader(t('orderRecords.subtitle'));

  return (
    <div className="app-page">
      <div className="surface-card order-records-shell">
        <Tabs
          defaultActiveKey="orders"
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
// AI_GENERATE_END --
