// AI_GENERATE_START----------------------------
import { Suspense, lazy } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spin } from 'antd';

import DashboardLayout from './layouts/DashboardLayout';
import { useAuth } from './auth/AuthContext';

const Dashboard = lazy(() => import('./pages/dashboard'));
const Market = lazy(() => import('./pages/market'));
const OrderRecords = lazy(() => import('./pages/orderRecords'));
const TradeRecordDemos = lazy(() => import('./pages/tradeRecordDemos'));
const Logs = lazy(() => import('./pages/logs'));
const ModelOperations = lazy(() => import('./pages/modelOperations'));
const AgentConversation = lazy(() => import('./pages/agentConversation'));
const AdaptiveStrategy = lazy(() => import('./pages/adaptiveStrategy'));
const JobManagement = lazy(() => import('./pages/jobs'));
const Login = lazy(() => import('./pages/login'));

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
              <Route path="orderRecords" element={<OrderRecords />} />
              <Route path="tradeRecordDemos" element={<TradeRecordDemos />} />
              <Route path="logs" element={<Logs />} />
              <Route path="modelOperations" element={<ModelOperations />} />
              <Route path="agentConversation" element={<AgentConversation />} />
              <Route path="adaptiveStrategy" element={<AdaptiveStrategy />} />
              <Route path="realTrading" element={<Navigate to="/adaptiveStrategy" replace />} />
              <Route path="jobs" element={<JobManagement />} />
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
// AI_GENERATE_END----------------------------
