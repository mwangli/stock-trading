import React, { Suspense, lazy } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Spin } from 'antd';

import DashboardLayout from './layouts/DashboardLayout';
import { useUserStore } from './store/userStore';

const Login = lazy(() => import('./pages/Login'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Market = lazy(() => import('./pages/Market'));
const Strategies = lazy(() => import('./pages/Strategies'));
const Analysis = lazy(() => import('./pages/Analysis'));
const Settings = lazy(() => import('./pages/Settings'));
const Transactions = lazy(() => import('./pages/Transactions'));
const HistoryOrders = lazy(() => import('./pages/HistoryOrders'));
const Logs = lazy(() => import('./pages/Logs'));
const JobAdmin = lazy(() => import('./pages/JobAdmin'));

const PrivateRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated } = useUserStore();
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
};

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
        <Route path="/login" element={<Login />} />
        
        <Route path="/" element={<PrivateRoute><DashboardLayout /></PrivateRoute>}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="market" element={<Market />} />
          <Route path="models" element={<Strategies />} />
          <Route path="analysis" element={<Analysis />} />
          <Route path="transactions" element={<Transactions />} />
          <Route path="history-orders" element={<HistoryOrders />} />
          <Route path="jobs" element={<JobAdmin />} />
          <Route path="logs" element={<Logs />} />
          <Route path="settings" element={<Settings />} />
        </Route>

        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
      </Suspense>
    </Router>
  );
}

export default App;
