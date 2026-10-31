// AI_GENERATE_START--------------------------------
import { Spin } from 'antd'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { AppShell } from './layout/AppShell'
import LoginPage from './pages/login'
import DashboardPage from './pages/dashboard'
import MarketPage from './pages/market'
import TradingPage from './pages/trading'
import ModelsPage from './pages/models'
import AgentPage from './pages/agent'
import OperationsPage from './pages/operations'
import DocumentsPage from './pages/documents'
import MobileDemoPage from './pages/mobileDemo'

function ProtectedShell() {
  const auth = useAuth()
  const location = useLocation()
  if (auth.loading) return <div className="center-screen"><Spin size="large" /></div>
  if (!auth.username) return <Navigate to="/login" state={{ from: location.pathname }} replace />
  return <AppShell />
}

export default function App() {
  return <Routes>
    {import.meta.env.DEV && <Route path="/mobile-demo" element={<MobileDemoPage />} />}
    <Route path="/login" element={<LoginPage />} />
    <Route element={<ProtectedShell />}>
      <Route index element={<DashboardPage />} />
      <Route path="market" element={<MarketPage />} />
      <Route path="trading" element={<TradingPage />} />
      <Route path="models" element={<ModelsPage />} />
      <Route path="agent" element={<AgentPage />} />
      <Route path="operations" element={<OperationsPage />} />
      <Route path="documents" element={<DocumentsPage />} />
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
}
// AI_GENERATE_END--------------------------------
