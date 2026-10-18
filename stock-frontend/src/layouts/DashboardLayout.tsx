// AI_GENERATE_START --------------------------------
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
  RobotOutlined,
} from '@ant-design/icons';
import { useAppTheme } from '../theme/AppThemeProvider';
import { PageHeaderContext, type PageHeaderState } from './PageHeaderContext';
import { checkBackendHealth } from '../utils/backendHealth';
import { useAuth } from '../auth/AuthContext';
import SystemMark from '../components/SystemMark';
import request from '../utils/request';
import { type ApiResponse } from '../utils/api';

const { Header, Content, Sider } = Layout;

const DEFAULT_MENU_ORDER = [
  '/dashboard',
  '/market',
  '/stockInformation',
  '/historyOrders',
  '/tradeRecords',
  '/modelOperations',
  '/sentimentModel',
  '/strategyConfig',
  '/jobs',
  '/settings',
  '/logs',
];

interface UserMenuPreferenceResponse {
  menuOrder: string[];
}

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

const normalizeMenuOrder = (menuOrder: unknown) => {
  if (!Array.isArray(menuOrder) || menuOrder.length !== DEFAULT_MENU_ORDER.length) {
    return DEFAULT_MENU_ORDER;
  }

  const validKeys = menuOrder.filter(
    (key): key is string => typeof key === 'string' && DEFAULT_MENU_ORDER.includes(key),
  );
  const isCompleteOrder = validKeys.length === DEFAULT_MENU_ORDER.length
    && new Set(validKeys).size === DEFAULT_MENU_ORDER.length;

  return isCompleteOrder ? validKeys : DEFAULT_MENU_ORDER;
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
  const [menuOrder, setMenuOrder] = useState<string[]>(DEFAULT_MENU_ORDER);
  const [menuOrderLoaded, setMenuOrderLoaded] = useState(false);
  const [backendStatus, setBackendStatus] = useState<'checking' | 'online' | 'offline'>('checking');

  useEffect(() => {
    let active = true;
    setMenuOrderLoaded(false);
    void (request.get('/userPreferences/menuOrder') as unknown as Promise<ApiResponse<UserMenuPreferenceResponse>>)
      .then((response) => {
        if (!active) {
          return;
        }
        setMenuOrder(normalizeMenuOrder(response.success ? response.data?.menuOrder : undefined));
      })
      .catch(() => {
        if (active) {
          setMenuOrder(DEFAULT_MENU_ORDER);
        }
      })
      .finally(() => {
        if (active) {
          setMenuOrderLoaded(true);
        }
      });
    return () => {
      active = false;
    };
  }, [user?.id]);

  useEffect(() => {
    let active = true;
    const checkBackendStatus = async () => {
      try {
        const online = await checkBackendHealth();
        if (active) {
          setBackendStatus(online ? 'online' : 'offline');
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
    { key: '/stockInformation', icon: <DatabaseOutlined />, label: t('layout.stockInformation') },
    { key: '/historyOrders', icon: <HistoryOutlined />, label: t('layout.historyOrders') },
    { key: '/tradeRecords', icon: <LineChartOutlined />, label: t('layout.tradeRecords') },
    { key: '/logs', icon: <FileTextOutlined />, label: t('layout.logs') },
    { key: '/modelOperations', icon: <ExperimentOutlined />, label: t('layout.modelOperations') },
    { key: '/sentimentModel', icon: <MessageOutlined />, label: t('layout.sentimentModel') },
    { key: '/strategyConfig', icon: <RobotOutlined />, label: t('layout.agent') },
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
    const normalizedOrder = normalizeMenuOrder(nextOrder);
    setMenuOrder(normalizedOrder);
    if (!menuOrderLoaded) {
      return;
    }
    void (request.put('/userPreferences/menuOrder', { menuOrder: normalizedOrder }) as unknown as Promise<ApiResponse<UserMenuPreferenceResponse>>)
      .catch(() => undefined);
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
    '/stockInformation': t('layout.pageTitles.stockInformation'),
    '/historyOrders': t('layout.pageTitles.historyOrders'),
    '/tradeRecords': t('layout.pageTitles.tradeRecords'),
    '/logs': t('layout.pageTitles.logs'),
    '/modelOperations': t('layout.pageTitles.modelOperations'),
    '/sentimentModel': t('layout.pageTitles.sentimentModel'),
    '/strategyConfig': t('layout.pageTitles.agent'),
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
        <div className="app-brand" title={t('layout.title')}>
          <SystemMark size="small" title={t('layout.title')} />
          {!sidebarCollapsed && (
            <div className="app-brand-copy">
              <strong>{t('login.algoTrading')}</strong>
            </div>
          )}
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
              <div className="app-page-title truncate">{pageTitles[location.pathname] ?? '智能交易终端'}</div>
              <div className="truncate text-xs text-[var(--text-muted)]">
                {pageHeader?.subtitle ?? 'Stock Operations Console'}
              </div>
            </div>
          </div>

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
// AI_GENERATE_END --------------------------------
