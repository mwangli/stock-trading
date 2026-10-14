// AI_GENERATE_START --------------
import { Suspense, lazy } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spin } from 'antd';

import DashboardLayout from './layouts/DashboardLayout';
import { useAuth } from './auth/AuthContext';

const Dashboard = lazy(() => import('./pages/Dashboard'));
const Market = lazy(() => import('./pages/Market'));
const StockInformation = lazy(() => import('./pages/StockInformation'));
const Settings = lazy(() => import('./pages/Settings'));
const HistoryOrders = lazy(() => import('./pages/HistoryOrders'));
const TradeRecords = lazy(() => import('./pages/TradeRecords'));
const Logs = lazy(() => import('./pages/Logs'));
const ModelOperations = lazy(() => import('./pages/ModelOperations'));
const SentimentModel = lazy(() => import('./pages/SentimentModel'));
const StrategyConfig = lazy(() => import('./pages/StrategyConfig'));
const RealTrading = lazy(() => import('./pages/RealTrading'));
const JobManagement = lazy(() => import('./pages/JobManagement'));
const Login = lazy(() => import('./pages/Login'));

const PageFallback = (
  <div className="flex min-h-[200px] items-center justify-center">
    <Spin size="large" />
  </div>
);

function App() {
  return (
    <Router>
      <Suspense fallback={PageFallback}>
        <Routes>
          <Route path="/login" element={<PublicOnlyRoute />} />
          <Route path="/" element={<ProtectedRoute />}>
            <Route element={<DashboardLayout />}>
              <Route index element={<Navigate to="/dashboard" replace />} />
              <Route path="dashboard" element={<Dashboard />} />
              <Route path="market" element={<Market />} />
              <Route path="stock-information" element={<StockInformation />} />
              <Route path="transactions" element={<Navigate to="/history-orders" replace />} />
              <Route path="history-orders" element={<HistoryOrders />} />
              <Route path="trade-records" element={<TradeRecords />} />
              <Route path="logs" element={<Logs />} />
              <Route path="model-operations" element={<ModelOperations />} />
              <Route path="sentiment-model" element={<SentimentModel />} />
              <Route path="strategy-config" element={<StrategyConfig />} />
              <Route path="real-trading" element={<RealTrading />} />
              <Route path="jobs" element={<JobManagement />} />
              <Route path="settings" element={<Settings />} />
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </Suspense>
    </Router>
  );
}

function ProtectedRoute() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return PageFallback;
  if (!user) return <Navigate to={`/login?redirect=${encodeURIComponent(location.pathname)}`} replace />;
  return <Outlet />;
}

function PublicOnlyRoute() {
  const { user, loading } = useAuth();
  if (loading) return PageFallback;
  return user ? <Navigate to="/dashboard" replace /> : <Login />;
}

export default App;
// AI_GENERATE_END --------------
