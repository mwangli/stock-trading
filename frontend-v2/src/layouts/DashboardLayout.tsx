import React, { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Avatar, Dropdown, Button } from 'antd';
import { useTranslation } from 'react-i18next';
import {
  DesktopOutlined,
  PieChartOutlined,
  UserOutlined,
  LogoutOutlined,
  SettingOutlined,
  StockOutlined,
  FundOutlined,
  MenuUnfoldOutlined,
MenuFoldOutlined,
  HistoryOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined
} from '@ant-design/icons';
import { useUserStore } from '../store/userStore';

const { Header, Content, Sider } = Layout;

const DashboardLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useUserStore();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };
  const { t, i18n } = useTranslation();

  const changeLanguage = () => {
    const newLang = i18n.language === 'en' ? 'zh' : 'en';
    i18n.changeLanguage(newLang);
  };


  const menuItems = [
    { key: '/dashboard', icon: <DesktopOutlined />, label: t('layout.dashboard') },
    { key: '/market', icon: <StockOutlined />, label: t('layout.market') },
    { key: '/strategies', icon: <FundOutlined />, label: t('layout.strategies') },
    { key: '/analysis', icon: <PieChartOutlined />, label: t('layout.analysis') },
    { key: '/transactions', icon: <HistoryOutlined />, label: t('layout.transactions') },
    { key: '/settings', icon: <SettingOutlined />, label: t('layout.settings') },
  ];

  const userMenu = {
    items: [
      { key: 'profile', icon: <UserOutlined />, label: t('layout.profile') },
      { key: 'settings', icon: <SettingOutlined />, label: t('layout.settings') },
      { type: 'divider' },
      { key: 'logout', icon: <LogoutOutlined />, label: t('common.logout'), onClick: handleLogout, danger: true },
    ]
  };

  return (
    <Layout style={{ minHeight: '100vh', background: '#050505' }}>
      <Sider 
        trigger={null} 
        collapsible 
        collapsed={collapsed}
        width={250}
        style={{ 
          background: '#0a0c10', 
          borderRight: '1px solid rgba(255,255,255,0.05)',
        }}
        className="shadow-2xl z-20"
      >
        <div className="h-16 flex items-center justify-center border-b border-white/5 mx-4 mb-4">
          {!collapsed ? (
            <span className="text-xl font-bold tracking-widest text-transparent bg-clip-text bg-gradient-to-r from-[#00e396] to-[#00b374]">
              {t('layout.title')}
            </span>
          ) : (
            <StockOutlined className="text-2xl text-[#00e396]" />
          )}
        </div>

        <Menu
          theme="dark"
          mode="inline"
          defaultSelectedKeys={[location.pathname]}
          selectedKeys={[location.pathname]}
          style={{ background: 'transparent', borderRight: 0 }}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          className="px-2"
        />

        <div className="absolute bottom-8 w-full px-6">
          {!collapsed && (
            <div className="p-4 rounded-xl bg-gradient-to-br from-[#00e396]/10 to-transparent border border-[#00e396]/20">
              <div className="text-xs text-[#00e396] font-mono mb-2">{t('layout.systemStatus')}</div>
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-[#00e396] animate-pulse"></div>
                <span className="text-xs text-white/70">{t('layout.connected')}</span>
              </div>
              <div className="text-xs text-white/40 mt-1">{t('layout.latency')}: 24ms</div>
            </div>
          )}
        </div>
      </Sider>

      <Layout style={{ background: '#050505' }}>
        <Header 
          style={{ 
            padding: '0 24px', 
            background: 'rgba(5, 5, 5, 0.8)', 
            backdropFilter: 'blur(10px)',
            borderBottom: '1px solid rgba(255,255,255,0.05)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            height: '64px',
            position: 'sticky',
            top: 0,
            zIndex: 10
          }}
        >
          <div className="flex items-center gap-4">
             <Button
                type="text"
                icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={() => setCollapsed(!collapsed)}
                style={{
                  fontSize: '16px',
                  width: 64,
                  height: 64,
                  color: '#fff'
                }}
              />
              <div className="hidden md:flex items-center gap-6 text-xs font-mono text-gray-400">
                <span className="flex items-center gap-2">
                  <span className="text-gray-500">{t('layout.pnl.today')}</span>
                  <span className="text-[#00e396] flex items-center gap-1">
                    <ArrowUpOutlined /> $1,245.50
                  </span>
                </span>
                <span className="flex items-center gap-2">
                  <span className="text-gray-500">{t('layout.pnl.week')}</span>
                  <span className="text-[#ff4560] flex items-center gap-1">
                    <ArrowDownOutlined /> $532.10
                  </span>
                </span>
                <span className="flex items-center gap-2">
                  <span className="text-gray-500">{t('layout.pnl.month')}</span>
                  <span className="text-[#00e396] flex items-center gap-1">
                    <ArrowUpOutlined /> $4,320.00
                  </span>
                </span>
              </div>
          </div>

          <div className="flex items-center gap-6">
            <Button 
                type="text" 
                className="text-[#00e396] border border-[#00e396]/20 hover:bg-[#00e396]/10 font-mono text-xs px-3 h-8"
                onClick={changeLanguage}
            >
                {i18n.language === 'en' ? 'EN' : '中文'}
            </Button>
<div className="text-right hidden sm:block">
              <div className="text-sm text-white font-medium">{user?.username}</div>
              <div className="text-xs text-[#00e396]">{user?.role}</div>
            </div>
            {/* @ts-ignore */}
            <Dropdown menu={userMenu} placement="bottomRight" arrow>
              <Avatar 
                size="large" 
                style={{ backgroundColor: '#00e396', cursor: 'pointer' }} 
                icon={<UserOutlined />} 
              />
            </Dropdown>
          </div>
        </Header>

        <Content 
          style={{ 
            margin: '24px 16px', 
            padding: 24, 
            minHeight: 280, 
            background: 'transparent',
            overflowY: 'auto'
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default DashboardLayout;
