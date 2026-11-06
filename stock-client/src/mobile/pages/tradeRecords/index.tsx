// AI_GENERATE_START ---
import '../orderRecords/styles.css';
import React from 'react';
import { Card } from 'antd';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import TradeRecordTable from '../orderRecords/TradeRecordTable';

const TradeRecords: React.FC = () => {
  usePageHeader('全部成本 · 累计交易');

  return (
    <div className="app-page">
      <Card className="surface-card order-records-shell">
        <TradeRecordTable />
      </Card>
    </div>
  );
};

export default TradeRecords;
// AI_GENERATE_END ---
