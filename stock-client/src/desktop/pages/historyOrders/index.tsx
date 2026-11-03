// AI_GENERATE_START -
import '../orderRecords/styles.css';
import React from 'react';
import { Card } from 'antd';
import { useTranslation } from 'react-i18next';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import OrderHistoryTable from '../orderRecords/OrderHistoryTable';

const HistoryOrders: React.FC = () => {
  const { t } = useTranslation();
  usePageHeader(t('historyOrders.subtitle'));

  return (
    <div className="app-page">
      <Card className="surface-card order-records-shell">
        <OrderHistoryTable />
      </Card>
    </div>
  );
};

export default HistoryOrders;
// AI_GENERATE_END -
