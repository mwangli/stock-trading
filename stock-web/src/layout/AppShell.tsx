// AI_GENERATE_START -----
import { useEffect, useState } from 'react'
import { App, Avatar, Button, ConfigProvider, Drawer, Layout, Menu, Space, Tooltip, theme } from 'antd'
import {
  BarChartOutlined, BookOutlined, BulbOutlined, DashboardOutlined, DatabaseOutlined,
  EllipsisOutlined, LockOutlined, LogoutOutlined, MenuFoldOutlined, MenuUnfoldOutlined,
  MessageOutlined, MoonOutlined, OrderedListOutlined, SafetyCertificateOutlined, SunOutlined,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useMediaQuery } from '../hooks/useMediaQuery'

const items = [
  { key: '/', icon: <DashboardOutlined />, label: '概览' },
  { key: '/market', icon: <BarChartOutlined />, label: '行情' },
  { key: '/trading', icon: <OrderedListOutlined />, label: '交易记录' },
  { key: '/models', icon: <DatabaseOutlined />, label: '模型与预测' },
  { key: '/agent', icon: <MessageOutlined />, label: 'Agent' },
  { key: '/operations', icon: <BulbOutlined />, label: '运行状态与日志' },
  { key: '/documents', icon: <BookOutlined />, label: '文档工作台' },
]

const primaryMobileKeys = ['/', '/market', '/trading', '/agent']
const consoleThemeTokens = {
  light: {
    colorPrimary: '#0f766e', colorBgLayout: '#f3f5f7', colorBgContainer: '#ffffff',
    colorBorderSecondary: '#dce2e6', colorText: '#182229', colorTextSecondary: '#6f7b84',
  },
  dark: {
    colorPrimary: '#35a995', colorBgLayout: '#0b1014', colorBgContainer: '#11181d',
    colorBorderSecondary: '#26333a', colorText: '#edf2f4', colorTextSecondary: '#a5b0b6',
  },
}
const routeDescriptions: Record<string, string> = {
  '/': '账户与绩效总览',
  '/market': '行情目录与历史走势',
  '/trading': '委托、成交与持仓事实',
  '/models': '模型版本与预测评估',
  '/agent': '只读事实问答',
  '/operations': '任务状态与运行日志',
  '/documents': '项目文档与检索',
}

function routeLabel(pathname: string) {
  return items.find((item) => item.key === pathname)?.label || '股票交易控制台'
}

export function AppShell() {
  const navigate = useNavigate()
  const location = useLocation()
  const { username, logout } = useAuth()
  const mobile = useMediaQuery('(max-width: 480px)')
  const [collapsed, setCollapsed] = useState(false)
  const [moreOpen, setMoreOpen] = useState(false)
  const [dark, setDark] = useState(() => localStorage.getItem('stock-console-theme') === 'dark')

  useEffect(() => {
    document.documentElement.dataset.theme = dark ? 'dark' : 'light'
  }, [dark])

  const toggleTheme = () => {
    const next = !dark
    setDark(next)
    localStorage.setItem('stock-console-theme', next ? 'dark' : 'light')
  }
  const go = (path: string) => {
    navigate(path)
    setMoreOpen(false)
  }
  const moreActive = !primaryMobileKeys.includes(location.pathname)
  const pageTitle = routeLabel(location.pathname)

  return <ConfigProvider theme={{
    algorithm: dark ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: {
      ...consoleThemeTokens[dark ? 'dark' : 'light'],
      borderRadius: 4,
      controlHeight: 36,
      fontSize: 14,
    },
  }}>
    <App><Layout className="app-layout">
      {!mobile && <Layout.Sider
        collapsed={collapsed}
        collapsedWidth={72}
        trigger={null}
        width={224}
        className={collapsed ? 'app-sider is-collapsed' : 'app-sider'}
      >
        <div className="brand">
          <img src="/system-mark.svg" alt="" />
          {!collapsed && <div><strong>股票交易控制台</strong><span>Python 事实工作台</span></div>}
        </div>
        <div className="sider-section-label">{collapsed ? '···' : '工作区'}</div>
        <Menu theme="dark" mode="inline" selectedKeys={[location.pathname]} items={items} onClick={({ key }) => navigate(key)} />
        <div className="sider-footer">
          <LockOutlined aria-hidden="true" />
          {!collapsed && <div><strong>只读控制面</strong><span>{username}</span></div>}
        </div>
      </Layout.Sider>}
      <Layout className={mobile ? 'app-main app-main-mobile' : 'app-main'}>
        <Layout.Header className="app-header">
          {mobile ? <div className="mobile-header-title">
            <img src="/system-mark.svg" alt="" />
            <div><strong>{pageTitle}</strong><span>{routeDescriptions[location.pathname]}</span></div>
          </div> : <div className="app-header-left">
            <Button
              className="header-icon-button"
              aria-label={collapsed ? '展开菜单' : '收起菜单'}
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed((value) => !value)}
            />
            <div className="app-header-context"><strong>{pageTitle}</strong><span>{routeDescriptions[location.pathname]}</span></div>
          </div>}
          {mobile ? <span className="mobile-readonly-label"><LockOutlined aria-hidden="true" />只读</span> : <Space size={8}>
            <span className="header-readonly"><SafetyCertificateOutlined aria-hidden="true" />Python 事实只读</span>
            <Tooltip title={dark ? '切换浅色主题' : '切换深色主题'}>
              <Button className="header-icon-button" aria-label="切换主题" icon={dark ? <SunOutlined /> : <MoonOutlined />} onClick={toggleTheme} />
            </Tooltip>
            <span className="header-user"><Avatar size={28}>{username?.slice(0, 1).toUpperCase()}</Avatar><span>{username}</span></span>
            <Tooltip title="退出登录"><Button className="header-icon-button" aria-label="退出登录" icon={<LogoutOutlined />} onClick={() => void logout()} /></Tooltip>
          </Space>}
        </Layout.Header>
        <Layout.Content className="app-content"><Outlet /></Layout.Content>
        {mobile && <nav className="mobile-tabbar" aria-label="主要导航">
          {items.filter((item) => primaryMobileKeys.includes(item.key)).map((item) => <button type="button" className={location.pathname === item.key ? 'mobile-tabbar-item active' : 'mobile-tabbar-item'} aria-current={location.pathname === item.key ? 'page' : undefined} key={item.key} onClick={() => go(item.key)}>{item.icon}<span>{item.label}</span></button>)}
          <button type="button" className={moreActive ? 'mobile-tabbar-item active' : 'mobile-tabbar-item'} aria-label="打开更多功能" aria-expanded={moreOpen} onClick={() => setMoreOpen(true)}><EllipsisOutlined /><span>更多</span></button>
        </nav>}
      </Layout>
      <Drawer title="更多功能" placement="bottom" height="auto" open={moreOpen} onClose={() => setMoreOpen(false)} rootClassName="mobile-more-drawer">
        <div className="mobile-profile"><Avatar>{username?.slice(0, 1).toUpperCase()}</Avatar><div><strong>{username}</strong><span>单用户只读控制台</span></div></div>
        <div className="mobile-more-grid">
          {items.filter((item) => !primaryMobileKeys.includes(item.key)).map((item) => <button type="button" className={location.pathname === item.key ? 'mobile-more-item active' : 'mobile-more-item'} key={item.key} onClick={() => go(item.key)}>{item.icon}<span>{item.label}</span></button>)}
          <button type="button" className="mobile-more-item" onClick={toggleTheme}>{dark ? <SunOutlined /> : <MoonOutlined />}<span>{dark ? '浅色主题' : '深色主题'}</span></button>
          <button type="button" className="mobile-more-item danger" onClick={() => void logout()}><LogoutOutlined /><span>退出登录</span></button>
        </div>
      </Drawer>
    </Layout></App>
  </ConfigProvider>
}
// AI_GENERATE_END -----
