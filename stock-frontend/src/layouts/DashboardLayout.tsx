// AI_GENERATE_START -------------------------
import React, { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Avatar, Badge, Button, Dropdown, Layout, Tooltip, type MenuProps } from 'antd';
import { useTranslation } from 'react-i18next';
import { Reorder, useDragControls } from 'framer-motion';
import {
  BellOutlined,
  DesktopOutlined,
  SettingOutlined,
  StockOutlined,
  DatabaseOutlined,
  MenuUnfoldOutlined,
  MenuFoldOutlined,
  HistoryOutlined,
  LineChartOutlined,
  FileTextOutlined,
  ExperimentOutlined,
  ScheduleOutlined,
  MoonOutlined,
  SunOutlined,
  UserOutlined,
  HolderOutlined,
  MessageOutlined,
  SlidersOutlined,
} from '@ant-design/icons';
import { useAppTheme } from '../theme/AppThemeProvider';
import { PageHeaderContext, type PageHeaderState } from './PageHeaderContext';
import request from '../utils/request';
import { useAuth } from '../auth/AuthContext';

const { Header, Content, Sider } = Layout;

const MENU_ORDER_STORAGE_KEY = 'stock-trading.sidebar.menuOrder';
const DEFAULT_MENU_ORDER = [
  '/dashboard',
  '/market',
  '/stock-information',
  '/history-orders',
  '/trade-records',
  '/logs',
  '/model-operations',
  '/sentiment-model',
  '/strategy-config',
  '/jobs',
  '/settings',
];

interface SidebarMenuItem {
  key: string;
  icon: ReactNode;
  label: string;
}

interface DraggableSidebarItemProps {
  item: SidebarMenuItem;
  selected: boolean;
  collapsed: boolean;
  onSelect: (key: string) => void;
}

const readStoredMenuOrder = () => {
  try {
    const storedValue = window.localStorage.getItem(MENU_ORDER_STORAGE_KEY);
    const parsedValue: unknown = storedValue ? JSON.parse(storedValue) : [];
    const validKeys = Array.isArray(parsedValue)
      ? parsedValue.filter((key): key is string => typeof key === 'string' && DEFAULT_MENU_ORDER.includes(key))
      : [];
    return [...validKeys, ...DEFAULT_MENU_ORDER.filter((key) => !validKeys.includes(key))];
  } catch {
    return DEFAULT_MENU_ORDER;
  }
};

const DraggableSidebarItem: React.FC<DraggableSidebarItemProps> = ({
  item,
  selected,
  collapsed,
  onSelect,
}) => {
  const dragControls = useDragControls();
  const { t } = useTranslation();

  return (
    <Reorder.Item
      as="li"
      value={item.key}
      dragListener={false}
      dragControls={dragControls}
      className={`app-menu-item${selected ? ' app-menu-item-selected' : ''}`}
    >
      <Tooltip title={collapsed ? item.label : undefined} placement="right">
        <button
          type="button"
          className="app-menu-item-main"
          aria-current={selected ? 'page' : undefined}
          onClick={() => onSelect(item.key)}
        >
          <span className="app-menu-item-icon">{item.icon}</span>
          {!collapsed && <span className="app-menu-item-label">{item.label}</span>}
        </button>
      </Tooltip>
      {!collapsed && (
        <Tooltip title={t('layout.dragToReorder')} placement="right">
          <button
            type="button"
            className="app-menu-drag-handle"
            aria-label={`${t('layout.dragToReorder')}: ${item.label}`}
            onPointerDown={(event) => {
              event.preventDefault();
              dragControls.start(event);
            }}
          >
            <HolderOutlined />
          </button>
        </Tooltip>
      )}
    </Reorder.Item>
  );
};

const DashboardLayout: React.FC = () => {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { t, i18n } = useTranslation();
  const { mode, toggleMode } = useAppTheme();
  const { user, logout } = useAuth();
  const [pageHeader, setPageHeader] = useState<PageHeaderState | null>(null);
  const [menuOrder, setMenuOrder] = useState<string[]>(readStoredMenuOrder);
  const [backendStatus, setBackendStatus] = useState<'checking' | 'online' | 'offline'>('checking');

  useEffect(() => {
    let active = true;
    const checkBackendStatus = async () => {
      try {
        await request.get('/broker-query/status');
        if (active) {
          setBackendStatus('online');
        }
      } catch {
        if (active) {
          setBackendStatus('offline');
        }
      }
    };
    void checkBackendStatus();
    const timer = window.setInterval(() => void checkBackendStatus(), 30000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, []);

  const changeLanguage = () => {
    const newLang = i18n.language === 'en' ? 'zh' : 'en';
    i18n.changeLanguage(newLang);
  };


  const menuItems = useMemo<SidebarMenuItem[]>(() => [
    { key: '/dashboard', icon: <DesktopOutlined />, label: t('layout.dashboard') },
    { key: '/market', icon: <StockOutlined />, label: t('layout.market') },
    { key: '/stock-information', icon: <DatabaseOutlined />, label: t('layout.stockInformation') },
    { key: '/history-orders', icon: <HistoryOutlined />, label: t('layout.historyOrders') },
    { key: '/trade-records', icon: <LineChartOutlined />, label: t('layout.tradeRecords') },
    { key: '/logs', icon: <FileTextOutlined />, label: t('layout.logs') },
    { key: '/model-operations', icon: <ExperimentOutlined />, label: t('layout.modelOperations') },
    { key: '/sentiment-model', icon: <MessageOutlined />, label: t('layout.sentimentModel') },
    { key: '/strategy-config', icon: <SlidersOutlined />, label: t('layout.strategyConfig') },
    { key: '/jobs', icon: <ScheduleOutlined />, label: t('layout.jobs') },
    { key: '/settings', icon: <SettingOutlined />, label: t('layout.settings') },
  ], [t]);

  const orderedMenuItems = useMemo(() => {
    const itemMap = new Map(menuItems.map((item) => [item.key, item]));
    return [
      ...menuOrder.map((key) => itemMap.get(key)).filter((item): item is SidebarMenuItem => Boolean(item)),
      ...menuItems.filter((item) => !menuOrder.includes(item.key)),
    ];
  }, [menuItems, menuOrder]);

  const reorderMenu = (nextOrder: string[]) => {
    setMenuOrder(nextOrder);
    window.localStorage.setItem(MENU_ORDER_STORAGE_KEY, JSON.stringify(nextOrder));
  };

  const selectMenuItem = (key: string) => {
    navigate(key);
    if (isMobile) {
      setSidebarCollapsed(true);
    }
  };

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'username',
      label: user?.displayName ?? user?.username ?? '当前用户',
      disabled: true,
    },
    { type: 'divider' },
    { key: 'logout', label: t('common.logout'), danger: true },
  ];

  const handleUserMenuClick: MenuProps['onClick'] = async ({ key }) => {
    if (key !== 'logout') {
      return;
    }
    await logout();
    navigate('/login', { replace: true });
  };

  const pageTitles: Record<string, string> = {
    '/dashboard': t('layout.pageTitles.dashboard'),
    '/market': t('layout.pageTitles.market'),
    '/stock-information': t('layout.pageTitles.stockInformation'),
    '/history-orders': t('layout.pageTitles.historyOrders'),
    '/trade-records': t('layout.pageTitles.tradeRecords'),
    '/logs': t('layout.pageTitles.logs'),
    '/model-operations': t('layout.pageTitles.modelOperations'),
    '/sentiment-model': t('layout.pageTitles.sentimentModel'),
    '/strategy-config': t('layout.pageTitles.strategyConfig'),
    '/jobs': t('layout.pageTitles.jobs'),
    '/settings': t('layout.pageTitles.settings'),
  };

  return (
    <PageHeaderContext.Provider value={setPageHeader}>
      <Layout className="app-shell">
      <Sider
        trigger={null}
        collapsible
        collapsed={sidebarCollapsed}
        collapsedWidth={isMobile ? 0 : 76}
        breakpoint="lg"
        onBreakpoint={(broken) => {
          setIsMobile(broken);
          if (broken) {
            setSidebarCollapsed(true);
          }
        }}
        onCollapse={setSidebarCollapsed}
        width={192}
        className="app-sidebar"
      >
        <div className="app-logo" title={t('layout.title')}>
          <StockOutlined />
        </div>

        <Reorder.Group
          as="ul"
          axis="y"
          values={orderedMenuItems.map((item) => item.key)}
          onReorder={reorderMenu}
          className={`app-menu-list${sidebarCollapsed ? ' app-menu-list-collapsed' : ''}`}
        >
          {orderedMenuItems.map((item) => (
            <DraggableSidebarItem
              key={item.key}
              item={item}
              selected={location.pathname === item.key}
              collapsed={sidebarCollapsed}
              onSelect={selectMenuItem}
            />
          ))}
        </Reorder.Group>
      </Sider>

      <Layout className="app-main-layout">
        <Header className="app-header">
          <div className="app-header-identity">
            <Tooltip title={sidebarCollapsed ? t('layout.expandMenu') : t('layout.collapseMenu')}>
              <Button
                type="text"
                icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={() => setSidebarCollapsed((collapsed) => !collapsed)}
              />
            </Tooltip>
            <div className="min-w-0 flex-1">
              <div className="truncate text-base font-semibold">{pageTitles[location.pathname] ?? '智能交易终端'}</div>
              <div className="truncate text-xs text-[var(--text-muted)]">
                {pageHeader?.subtitle ?? 'Stock Operations Console'}
              </div>
            </div>
          </div>

          {pageHeader?.actions && (
            <div className="app-header-actions">
              {pageHeader.actions}
            </div>
          )}

          <div className="app-header-tools">
            <div className="app-system-status">
              <span className={`h-2 w-2 rounded-full ${backendStatus === 'online' ? 'bg-emerald-500' : backendStatus === 'offline' ? 'bg-red-500' : 'bg-amber-500'}`} />
              {backendStatus === 'online' ? t('layout.online') : backendStatus === 'offline' ? t('layout.offline') : t('layout.checking')}
            </div>
            <Tooltip title={mode === 'dark' ? t('layout.lightMode') : t('layout.darkMode')}>
              <Button type="text" icon={mode === 'dark' ? <SunOutlined /> : <MoonOutlined />} onClick={toggleMode} />
            </Tooltip>
            <Button
              type="text"
              className="app-header-language !h-8 !px-2 text-xs"
              onClick={changeLanguage}
            >
              {i18n.language === 'en' ? 'EN' : '中文'}
            </Button>
            <Badge dot color="#22c55e" className="app-header-notification">
              <Button type="text" icon={<BellOutlined />} />
            </Badge>
            <Dropdown menu={{ items: userMenuItems, onClick: handleUserMenuClick }} trigger={['click']}>
              <Button type="text" className="!h-8 !px-1" aria-label={t('layout.userMenu')}>
                <Avatar size={30} icon={<UserOutlined />} className="app-header-avatar !bg-blue-100 !text-blue-700" />
              </Button>
            </Dropdown>
          </div>
        </Header>

        <Content className="app-content">
          <Outlet />
        </Content>
      </Layout>
      </Layout>
    </PageHeaderContext.Provider>
  );
};

export default DashboardLayout;
// AI_GENERATE_END -------------------------
