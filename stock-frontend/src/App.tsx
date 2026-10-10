// AI_GENERATE_START ----
import { Suspense, lazy } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Spin } from 'antd';

import DashboardLayout from './layouts/DashboardLayout';

const Dashboard = lazy(() => import('./pages/Dashboard'));
const Market = lazy(() => import('./pages/Market'));
const Settings = lazy(() => import('./pages/Settings'));
const Transactions = lazy(() => import('./pages/Transactions'));
const RealTrading = lazy(() => import('./pages/RealTrading'));
const Logs = lazy(() => import('./pages/Logs'));
const ModelOperations = lazy(() => import('./pages/ModelOperations'));

const PageFallback = (
  <div className="flex justify-center items-center min-h-[200px]">
    <Spin size="large" />
  </div>
);

function App() {
  return (
    <Router>
      <Suspense fallback={PageFallback}>
      <Routes>
        <Route path="/" element={<DashboardLayout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="market" element={<Market />} />
          <Route path="transactions" element={<Transactions />} />
          <Route path="real-trading" element={<RealTrading />} />
          <Route path="logs" element={<Logs />} />
          <Route path="model-operations" element={<ModelOperations />} />
          <Route path="settings" element={<Settings />} />
        </Route>

        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
      </Suspense>
    </Router>
  );
}

export default App;
// AI_GENERATE_END ----
