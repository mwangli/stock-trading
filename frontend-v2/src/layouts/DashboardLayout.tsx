// AI_GENERATE_START --
import React, { useState } from 'react';
import NotificationBell from '../components/NotificationBell';
import NotificationListener from '../components/NotificationListener';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button } from 'antd';
import { useTranslation } from 'react-i18next';
import {
  DesktopOutlined,
  SettingOutlined,
  StockOutlined,
  FundOutlined,
  MenuUnfoldOutlined,
MenuFoldOutlined,
  HistoryOutlined,
  TransactionOutlined,
  FileTextOutlined,
  ScheduleOutlined
} from '@ant-design/icons';

const { Header, Content, Sider } = Layout;

const DashboardLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { t, i18n } = useTranslation();

  const changeLanguage = () => {
    const newLang = i18n.language === 'en' ? 'zh' : 'en';
    i18n.changeLanguage(newLang);
  };


  const menuItems = [
    { key: '/dashboard', icon: <DesktopOutlined />, label: t('layout.dashboard') },
    { key: '/market', icon: <StockOutlined />, label: t('layout.market') },
    { key: '/models', icon: <FundOutlined />, label: t('layout.models') },
    { key: '/transactions', icon: <HistoryOutlined />, label: t('layout.transactions') },
    { key: '/real-trading', icon: <TransactionOutlined />, label: '真实交易' },
    { key: '/jobs', icon: <ScheduleOutlined />, label: t('layout.jobs') },
    { key: '/logs', icon: <FileTextOutlined />, label: t('layout.logs') },
    { key: '/settings', icon: <SettingOutlined />, label: t('layout.settings') },
  ];

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
          </div>

          <div className="flex items-center gap-4 sm:gap-6">
            <NotificationBell />
            <Button 
              type="text" 
              className="text-[#00e396] border border-[#00e396]/20 hover:bg-[#00e396]/10 font-mono text-xs px-3 h-8 shrink-0"
              onClick={changeLanguage}
            >
              {i18n.language === 'en' ? 'EN' : '中文'}
            </Button>
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
      <NotificationListener />
    </Layout>
  );
};

export default DashboardLayout;
// AI_GENERATE_END --
