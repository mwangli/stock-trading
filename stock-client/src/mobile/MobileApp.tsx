// AI_GENERATE_START --
import './styles/app.css';
import './styles/layout.css';
import { lazy, Suspense } from 'react';
import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { useAuth } from '@shared/auth/AuthContext';
import MobileLayout from './layouts/MobileLayout';

const Dashboard = lazy(() => import('./pages/dashboard'));
const TradeRecords = lazy(() => import('./pages/tradeRecords'));
const AgentConversation = lazy(() => import('./pages/agentConversation'));
const Logs = lazy(() => import('./pages/logs'));
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

const MobileApp = () => (
  <div className="mobile-app h-full">
    <Suspense fallback={fallback}>
      <Routes>
        <Route path="/login" element={<PublicOnlyRoute />} />
        <Route path="/" element={<ProtectedRoute />}>
          <Route element={<MobileLayout />}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="dashboard" element={<Dashboard />} />
            <Route path="tradeRecords" element={<TradeRecords />} />
            <Route path="agentConversation" element={<AgentConversation />} />
            <Route path="logs" element={<Logs />} />
            <Route path="market" element={<Navigate to="/dashboard" replace />} />
            <Route path="stockInformation" element={<Navigate to="/dashboard" replace />} />
            <Route path="historyOrders" element={<Navigate to="/dashboard" replace />} />
            <Route path="modelInfo" element={<Navigate to="/dashboard" replace />} />
            <Route path="orderRecords" element={<Navigate to="/tradeRecords" replace />} />
            <Route path="modelOperations" element={<Navigate to="/dashboard" replace />} />
            <Route path="tradeRecordDemos" element={<Navigate to="/dashboard" replace />} />
            <Route path="uiStyleDemos" element={<Navigate to="/dashboard" replace />} />
            <Route path="docs/*" element={<Navigate to="/dashboard" replace />} />
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

export default MobileApp;
// AI_GENERATE_END --
