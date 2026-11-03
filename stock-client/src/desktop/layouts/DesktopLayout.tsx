// AI_GENERATE_START ---
import React, {
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent as ReactKeyboardEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Avatar, Badge, Button, Dropdown, Layout, Tooltip, type MenuProps } from 'antd';
import { Reorder, useDragControls } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { BookOpenText } from 'lucide-react';
import {
  BellOutlined,
  CheckOutlined,
  DesktopOutlined,
  FileTextOutlined,
  FundProjectionScreenOutlined,
  HistoryOutlined,
  HolderOutlined,
  LineChartOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MessageOutlined,
  MoonOutlined,
  StockOutlined,
  SunOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useAppTheme } from '@shared/theme/AppThemeProvider';
import { useAuth } from '@shared/auth/AuthContext';
import { checkBackendHealth } from '@shared/utils/backendHealth';
import SystemBrand from '../components/SystemBrand';
import { PageHeaderContext, type PageHeaderState } from './PageHeaderContext';

const { Header, Content, Sider } = Layout;

const DEFAULT_MENU_ORDER = [
  '/dashboard',
  '/market',
  '/stockInformation',
  '/historyOrders',
  '/tradeRecords',
  '/modelInfo',
  '/agentConversation',
  '/logs',
];
const MENU_ORDER_CACHE_PREFIX = 'stockTrading.desktop.menuOrder';
const SIDEBAR_WIDTH_CACHE_PREFIX = 'stockTrading.desktop.sidebarWidth';
const SIDEBAR_WIDTH_LIMITS = { min: 160, max: 360 } as const;

interface SidebarMenuItem {
  key: string;
  icon: ReactNode;
  label: string;
}

interface ResizeState {
  pointerId: number;
  startX: number;
  startWidth: number;
  currentWidth: number;
  previousCursor: string;
  previousUserSelect: string;
}

interface DraggableSidebarItemProps {
  item: SidebarMenuItem;
  selected: boolean;
  collapsed: boolean;
  onSelect: (key: string) => void;
}

const normalizeMenuOrder = (value: unknown) => {
  if (!Array.isArray(value)) return DEFAULT_MENU_ORDER;
  const migrated = value.flatMap((key) => {
    if (key === '/orderRecords' || key === '/transactions') return ['/historyOrders', '/tradeRecords'];
    if (key === '/modelOperations') return ['/modelInfo'];
    return [key];
  });
  const valid = migrated.filter(
    (key): key is string => typeof key === 'string' && DEFAULT_MENU_ORDER.includes(key),
  );
  const unique = [...new Set(valid)];
  return [...unique, ...DEFAULT_MENU_ORDER.filter((key) => !unique.includes(key))];
};

const readMenuOrder = (userId?: number) => {
  if (userId === undefined) return null;
  try {
    const cached = window.localStorage.getItem(`${MENU_ORDER_CACHE_PREFIX}.${userId}`);
    return cached ? normalizeMenuOrder(JSON.parse(cached) as unknown) : null;
  } catch {
    return null;
  }
};

const defaultSidebarWidth = () => Math.min(
  220,
  Math.max(SIDEBAR_WIDTH_LIMITS.min, Math.round(window.innerWidth * 0.15)),
);

const sidebarWidthKey = (userId?: number) => `${SIDEBAR_WIDTH_CACHE_PREFIX}.${userId ?? 'anonymous'}`;

const readSidebarWidth = (userId?: number) => {
  const value = Number(window.localStorage.getItem(sidebarWidthKey(userId)));
  return Number.isFinite(value) && value >= SIDEBAR_WIDTH_LIMITS.min && value <= SIDEBAR_WIDTH_LIMITS.max
    ? value
    : defaultSidebarWidth();
};

const DraggableSidebarItem: React.FC<DraggableSidebarItemProps> = ({ item, selected, collapsed, onSelect }) => {
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
        <button type="button" className="app-menu-item-main" aria-current={selected ? 'page' : undefined} onClick={() => onSelect(item.key)}>
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

const DesktopLayout: React.FC = () => {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { t, i18n } = useTranslation();
  const { mode, autoThemeEnabled, setAutoThemeEnabled, toggleMode } = useAppTheme();
  const { user, logout } = useAuth();
  const resizeStateRef = useRef<ResizeState | null>(null);
  const [pageHeader, setPageHeader] = useState<PageHeaderState | null>(null);
  const [menuOrder, setMenuOrder] = useState(() => readMenuOrder(user?.id) ?? DEFAULT_MENU_ORDER);
  const [sidebarWidth, setSidebarWidth] = useState(() => readSidebarWidth(user?.id));
  const [backendStatus, setBackendStatus] = useState<'checking' | 'online' | 'offline'>('checking');

  useEffect(() => {
    setMenuOrder(readMenuOrder(user?.id) ?? DEFAULT_MENU_ORDER);
    setSidebarWidth(readSidebarWidth(user?.id));
  }, [user?.id]);

  useEffect(() => {
    let active = true;
    const check = async () => {
      try {
        const online = await checkBackendHealth();
        if (active) setBackendStatus(online ? 'online' : 'offline');
      } catch {
        if (active) setBackendStatus('offline');
      }
    };
    void check();
    const timer = window.setInterval(() => void check(), 10_000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, []);

  const menuItems = useMemo<SidebarMenuItem[]>(() => [
    { key: '/dashboard', icon: <DesktopOutlined />, label: t('layout.dashboard') },
    { key: '/market', icon: <StockOutlined />, label: t('layout.market') },
    { key: '/stockInformation', icon: <StockOutlined />, label: t('layout.stockInformation') },
    { key: '/historyOrders', icon: <HistoryOutlined />, label: t('layout.historyOrders') },
    { key: '/tradeRecords', icon: <LineChartOutlined />, label: t('layout.tradeRecords') },
    { key: '/modelInfo', icon: <FundProjectionScreenOutlined />, label: t('layout.modelInfo') },
    { key: '/agentConversation', icon: <MessageOutlined />, label: t('layout.agentConversation') },
    { key: '/logs', icon: <FileTextOutlined />, label: t('layout.logs') },
  ], [t]);

  const orderedMenuItems = useMemo(() => {
    const itemMap = new Map(menuItems.map((item) => [item.key, item]));
    return [
      ...menuOrder.map((key) => itemMap.get(key)).filter((item): item is SidebarMenuItem => Boolean(item)),
      ...menuItems.filter((item) => !menuOrder.includes(item.key)),
    ];
  }, [menuItems, menuOrder]);

  const reorderMenu = (nextOrder: string[]) => {
    const normalized = normalizeMenuOrder(nextOrder);
    setMenuOrder(normalized);
    if (user?.id !== undefined) {
      window.localStorage.setItem(`${MENU_ORDER_CACHE_PREFIX}.${user.id}`, JSON.stringify(normalized));
    }
  };

  const clampWidth = (width: number) => Math.min(SIDEBAR_WIDTH_LIMITS.max, Math.max(SIDEBAR_WIDTH_LIMITS.min, width));
  const persistWidth = (width: number) => window.localStorage.setItem(sidebarWidthKey(user?.id), String(Math.round(width)));

  const beginResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (sidebarCollapsed) return;
    resizeStateRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startWidth: sidebarWidth,
      currentWidth: sidebarWidth,
      previousCursor: document.body.style.cursor,
      previousUserSelect: document.body.style.userSelect,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  };

  const continueResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    const state = resizeStateRef.current;
    if (!state || state.pointerId !== event.pointerId) return;
    const width = clampWidth(state.startWidth + event.clientX - state.startX);
    state.currentWidth = width;
    setSidebarWidth(width);
  };

  const finishResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    const state = resizeStateRef.current;
    if (!state || state.pointerId !== event.pointerId) return;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
    document.body.style.cursor = state.previousCursor;
    document.body.style.userSelect = state.previousUserSelect;
    persistWidth(state.currentWidth);
    resizeStateRef.current = null;
  };

  const resizeByKeyboard = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Home') {
      event.preventDefault();
      window.localStorage.removeItem(sidebarWidthKey(user?.id));
      setSidebarWidth(defaultSidebarWidth());
      return;
    }
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    const width = clampWidth(sidebarWidth + (event.key === 'ArrowRight' ? 16 : -16));
    setSidebarWidth(width);
    persistWidth(width);
  };

  const userMenuItems: MenuProps['items'] = [
    { key: 'username', label: user?.displayName ?? user?.username ?? '当前用户', disabled: true },
    { type: 'divider' },
    {
      key: 'adaptiveTheme',
      label: <span className="flex min-w-[140px] items-center justify-between gap-4"><span>{t('layout.adaptiveTheme')}</span>{autoThemeEnabled && <CheckOutlined className="text-[var(--brand)]" />}</span>,
    },
    { type: 'divider' },
    { key: 'logout', label: t('common.logout'), danger: true },
  ];

  const handleUserMenuClick: MenuProps['onClick'] = async ({ key }) => {
    if (key === 'adaptiveTheme') {
      setAutoThemeEnabled(!autoThemeEnabled);
      return;
    }
    if (key === 'logout') {
      await logout();
      navigate('/login', { replace: true });
    }
  };

  const pageTitles: Record<string, string> = {
    '/dashboard': t('layout.pageTitles.dashboard'),
    '/market': t('layout.pageTitles.market'),
    '/stockInformation': t('layout.pageTitles.stockInformation'),
    '/historyOrders': t('layout.pageTitles.historyOrders'),
    '/tradeRecords': t('layout.pageTitles.tradeRecords'),
    '/modelInfo': t('layout.pageTitles.modelInfo'),
    '/agentConversation': t('layout.pageTitles.agentConversation'),
    '/logs': t('layout.pageTitles.logs'),
  };
  const backendStatusLabel = backendStatus === 'online' ? t('layout.online') : backendStatus === 'offline' ? t('layout.offline') : t('layout.checking');
  const documentationActive = location.pathname === '/docs' || location.pathname.startsWith('/docs/');

  return (
    <PageHeaderContext.Provider value={setPageHeader}>
      <Layout className="app-shell" style={{ '--app-sidebar-width': `${sidebarWidth}px` } as CSSProperties}>
        <Sider trigger={null} collapsible collapsed={sidebarCollapsed} collapsedWidth={76} width={sidebarWidth} className="app-sidebar">
          <SystemBrand collapsed={sidebarCollapsed} status={backendStatus} statusLabel={backendStatusLabel} title={t('login.algoTrading')} />
          <Reorder.Group as="ul" axis="y" values={orderedMenuItems.map((item) => item.key)} onReorder={reorderMenu} className={`app-menu-list${sidebarCollapsed ? ' app-menu-list-collapsed' : ''}`}>
            {orderedMenuItems.map((item) => (
              <DraggableSidebarItem key={item.key} item={item} selected={location.pathname === item.key} collapsed={sidebarCollapsed} onSelect={navigate} />
            ))}
          </Reorder.Group>
          {!sidebarCollapsed && (
            <div
              className="app-sidebar-resize-handle"
              role="separator"
              aria-label="调整主菜单宽度"
              aria-orientation="vertical"
              tabIndex={0}
              onPointerDown={beginResize}
              onPointerMove={continueResize}
              onPointerUp={finishResize}
              onPointerCancel={finishResize}
              onKeyDown={resizeByKeyboard}
              onDoubleClick={() => {
                window.localStorage.removeItem(sidebarWidthKey(user?.id));
                setSidebarWidth(defaultSidebarWidth());
              }}
            />
          )}
        </Sider>
        <Layout className="app-main-layout">
          <Header className="app-header">
            <div className="app-header-identity">
              <Tooltip title={sidebarCollapsed ? t('layout.expandMenu') : t('layout.collapseMenu')}>
                <Button type="text" icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />} onClick={() => setSidebarCollapsed((value) => !value)} />
              </Tooltip>
              <div className="min-w-0 flex-1">
                <div className="app-page-title truncate">{documentationActive ? '系统文档' : pageTitles[location.pathname] ?? t('layout.title')}</div>
                <div className="truncate text-xs text-[var(--text-muted)]">{pageHeader?.subtitle ?? 'Stock Operations Console'}</div>
              </div>
            </div>
            <div className="app-header-tools">
              <Tooltip title="系统文档">
                <Button
                  type="text"
                  className={`app-documentation-button${documentationActive ? ' is-active' : ''}`}
                  icon={<BookOpenText size={18} />}
                  aria-label="打开系统文档"
                  aria-current={documentationActive ? 'page' : undefined}
                  onClick={() => navigate('/docs')}
                />
              </Tooltip>
              <Button type="text" className="app-header-language !h-8 !px-2 text-xs" onClick={() => void i18n.changeLanguage(i18n.language.startsWith('en') ? 'zh' : 'en')}>
                {i18n.language.startsWith('en') ? 'EN' : '中文'}
              </Button>
              <Tooltip title={mode === 'dark' ? t('layout.lightMode') : t('layout.darkMode')}>
                <Button type="text" className="app-theme-picker-button" icon={mode === 'dark' ? <SunOutlined /> : <MoonOutlined />} onClick={toggleMode} />
              </Tooltip>
              <Badge dot color="#22c55e"><Button type="text" icon={<BellOutlined />} aria-label="通知" /></Badge>
              <Dropdown menu={{ items: userMenuItems, onClick: handleUserMenuClick }} trigger={['click']}>
                <Button type="text" className="!h-8 !px-1" aria-label={t('layout.userMenu')}><Avatar size={30} icon={<UserOutlined />} className="app-header-avatar" /></Button>
              </Dropdown>
            </div>
          </Header>
          <Content className={`app-content${documentationActive ? ' app-content-documentation' : ''}`}>
            <div className={`app-route-page${documentationActive ? ' app-route-page-documentation' : ''}`}><Outlet /></div>
          </Content>
        </Layout>
      </Layout>
    </PageHeaderContext.Provider>
  );
};

export default DesktopLayout;
// AI_GENERATE_END ---
