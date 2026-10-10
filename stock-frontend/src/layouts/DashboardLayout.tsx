// AI_GENERATE_START --------
import React, { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Avatar, Badge, Button, Layout, Menu, Tooltip } from 'antd';
import { useTranslation } from 'react-i18next';
import {
  BellOutlined,
  DesktopOutlined,
  SettingOutlined,
  StockOutlined,
  MenuUnfoldOutlined,
  HistoryOutlined,
  TransactionOutlined,
  FileTextOutlined,
  ExperimentOutlined,
  MoonOutlined,
  SunOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useAppTheme } from '../theme/AppThemeProvider';

const { Header, Content, Sider } = Layout;

const DashboardLayout: React.FC = () => {
  const [mobileCollapsed, setMobileCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { t, i18n } = useTranslation();
  const { mode, toggleMode } = useAppTheme();

  const changeLanguage = () => {
    const newLang = i18n.language === 'en' ? 'zh' : 'en';
    i18n.changeLanguage(newLang);
  };


  const menuItems = [
    { key: '/dashboard', icon: <DesktopOutlined />, label: t('layout.dashboard') },
    { key: '/market', icon: <StockOutlined />, label: t('layout.market') },
    { key: '/transactions', icon: <HistoryOutlined />, label: t('layout.transactions') },
    { key: '/real-trading', icon: <TransactionOutlined />, label: '真实交易' },
    { key: '/logs', icon: <FileTextOutlined />, label: t('layout.logs') },
    { key: '/model-operations', icon: <ExperimentOutlined />, label: '模型运维' },
    { key: '/settings', icon: <SettingOutlined />, label: t('layout.settings') },
  ];

  const pageTitles: Record<string, string> = {
    '/dashboard': '数据总览',
    '/market': '市场行情',
    '/transactions': '交易记录',
    '/real-trading': '真实交易',
    '/logs': '运行日志',
    '/model-operations': '模型运维',
    '/settings': '系统设置',
  };

  return (
    <Layout className="app-shell">
      <Sider
        trigger={null}
        collapsible
        collapsed={mobileCollapsed}
        collapsedWidth={0}
        breakpoint="lg"
        onBreakpoint={setMobileCollapsed}
        width={76}
        className="app-sidebar"
      >
        <div className="app-logo" title={t('layout.title')}>
          <StockOutlined />
        </div>

        <Menu
          theme={mode}
          mode="inline"
          inlineCollapsed
          defaultSelectedKeys={[location.pathname]}
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => {
            navigate(key);
            if (window.innerWidth < 992) {
              setMobileCollapsed(true);
            }
          }}
          className="app-menu"
        />
      </Sider>

      <Layout className="app-main-layout">
        <Header className="app-header">
          <div className="flex min-w-0 items-center gap-3">
            <Button
              type="text"
              className="lg:!hidden"
              icon={<MenuUnfoldOutlined />}
              onClick={() => setMobileCollapsed(false)}
            />
            <div className="min-w-0">
              <div className="truncate text-base font-semibold">{pageTitles[location.pathname] ?? '智能交易终端'}</div>
              <div className="text-xs text-[var(--text-muted)]">Stock Operations Console</div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="hidden items-center gap-2 rounded border border-[var(--border-color)] px-3 py-1.5 text-xs text-[var(--text-secondary)] md:flex">
              <span className="h-2 w-2 rounded-full bg-emerald-500" />系统在线
            </div>
            <Tooltip title={mode === 'dark' ? '切换浅色模式' : '切换深色模式'}>
              <Button type="text" icon={mode === 'dark' ? <SunOutlined /> : <MoonOutlined />} onClick={toggleMode} />
            </Tooltip>
            <Button
              type="text"
              className="!h-8 !px-2 text-xs"
              onClick={changeLanguage}
            >
              {i18n.language === 'en' ? 'EN' : '中文'}
            </Button>
            <Badge dot color="#22c55e">
              <Button type="text" icon={<BellOutlined />} />
            </Badge>
            <Avatar size={30} icon={<UserOutlined />} className="!bg-blue-100 !text-blue-700" />
          </div>
        </Header>

        <Content className="app-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default DashboardLayout;
// AI_GENERATE_END --------
