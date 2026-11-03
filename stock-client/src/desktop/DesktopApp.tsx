// AI_GENERATE_START --
import './styles/app.css';
import { lazy, Suspense } from 'react';
import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { useAuth } from '@shared/auth/AuthContext';
import DesktopLayout from './layouts/DesktopLayout';

const Dashboard = lazy(() => import('./pages/dashboard'));
const Market = lazy(() => import('./pages/market'));
const StockInformation = lazy(() => import('./pages/stockInformation'));
const HistoryOrders = lazy(() => import('./pages/historyOrders'));
const TradeRecords = lazy(() => import('./pages/tradeRecords'));
const ModelInfo = lazy(() => import('./pages/modelInfo'));
const TradeRecordDemos = lazy(() => import('./pages/tradeRecordDemos'));
const UiStyleDemos = lazy(() => import('./pages/uiStyleDemos'));
const Logs = lazy(() => import('./pages/logs'));
const AgentConversation = lazy(() => import('./pages/agentConversation'));
const Documentation = lazy(() => import('./pages/documentation'));
const Login = lazy(() => import('./pages/login'));

const fallback = <div className="flex min-h-[200px] items-center justify-center"><Spin size="large" /></div>;

const ProtectedRoute = () => {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return fallback;
  if (!user) {
    const redirect = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to={`/login?redirect=${encodeURIComponent(redirect)}`} replace />;
  }
  return <Outlet />;
};

const PublicOnlyRoute = () => {
  const { user, loading } = useAuth();
  if (loading) return fallback;
  return user ? <Navigate to="/dashboard" replace /> : <Login />;
};

const DesktopApp = () => (
  <div className="desktop-app h-full">
    <Suspense fallback={fallback}>
      <Routes>
        <Route path="/login" element={<PublicOnlyRoute />} />
        <Route path="/" element={<ProtectedRoute />}>
          <Route element={<DesktopLayout />}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="dashboard" element={<Dashboard />} />
            <Route path="market" element={<Market />} />
            <Route path="stockInformation" element={<StockInformation />} />
            <Route path="historyOrders" element={<HistoryOrders />} />
            <Route path="tradeRecords" element={<TradeRecords />} />
            <Route path="modelInfo" element={<ModelInfo />} />
            <Route path="orderRecords" element={<Navigate to="/tradeRecords" replace />} />
            <Route path="tradeRecordDemos" element={<TradeRecordDemos />} />
            <Route path="uiStyleDemos" element={<UiStyleDemos />} />
            <Route path="modelOperations" element={<Navigate to="/modelInfo" replace />} />
            <Route path="agentConversation" element={<AgentConversation />} />
            <Route path="logs" element={<Logs />} />
            <Route path="docs/*" element={<Documentation />} />
            <Route path="adaptiveStrategy" element={<Navigate to="/dashboard" replace />} />
            <Route path="realTrading" element={<Navigate to="/dashboard" replace />} />
            <Route path="jobs" element={<Navigate to="/dashboard" replace />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </Suspense>
  </div>
);

export default DesktopApp;
// AI_GENERATE_END --
