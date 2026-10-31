// AI_GENERATE_START----------------------------------------------------------------
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
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Avatar, Badge, Button, Dropdown, Layout, Tooltip, type MenuProps } from 'antd';
import { useTranslation } from 'react-i18next';
import { Reorder, useDragControls } from 'framer-motion';
import {
  BellOutlined,
  DesktopOutlined,
  StockOutlined,
  MenuUnfoldOutlined,
  MenuFoldOutlined,
  HistoryOutlined,
  FileTextOutlined,
  CheckOutlined,
  MoonOutlined,
  SunOutlined,
  UserOutlined,
  HolderOutlined,
  MessageOutlined,
  ReadOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons';
import { useAppTheme } from '../theme/AppThemeProvider';
import { PageHeaderContext, type PageHeaderState } from './PageHeaderContext';
import { checkBackendHealth } from '../utils/backendHealth';
import { useAuth } from '../auth/AuthContext';
import SystemBrand from '../components/SystemBrand';
import request from '../utils/request';
import { type ApiResponse } from '../utils/api';

const { Header, Content, Sider } = Layout;

const DEFAULT_MENU_ORDER = [
  '/dashboard',
  '/market',
  '/orderRecords',
  '/agentConversation',
  '/logs',
];
const MENU_ORDER_CACHE_PREFIX = 'stockTrading.menuOrder';
const SIDEBAR_WIDTH_CACHE_PREFIX = 'stockTrading.sidebarWidth';
const SIDEBAR_WIDTH_LIMITS = { min: 160, max: 360 } as const;

interface SidebarResizeState {
  pointerId: number;
  startX: number;
  startWidth: number;
  currentWidth: number;
  previousCursor: string;
  previousUserSelect: string;
}

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
  if (!Array.isArray(menuOrder)) {
    return DEFAULT_MENU_ORDER;
  }

  const migratedOrder = menuOrder.flatMap((key) => {
    if (key === '/historyOrders' || key === '/tradeRecords' || key === '/transactions') {
      return ['/orderRecords'];
    }
    if (key === '/stockInformation') {
      return ['/market'];
    }
    return [key];
  });
  const validKeys = migratedOrder.filter(
    (key): key is string => typeof key === 'string' && DEFAULT_MENU_ORDER.includes(key),
  );
  const uniqueKeys = [...new Set(validKeys)];
  return [...uniqueKeys, ...DEFAULT_MENU_ORDER.filter((key) => !uniqueKeys.includes(key))];
};

const getMenuOrderCacheKey = (userId: number) => MENU_ORDER_CACHE_PREFIX + '.' + userId;

const getSidebarWidthCacheKey = (userId: number | undefined) => (
  `${SIDEBAR_WIDTH_CACHE_PREFIX}.${userId ?? 'anonymous'}`
);

const getDefaultSidebarWidth = () => Math.min(
  220,
  Math.max(SIDEBAR_WIDTH_LIMITS.min, Math.round(window.innerWidth * 0.15)),
);

const readCachedSidebarWidth = (userId: number | undefined) => {
  const value = Number(window.localStorage.getItem(getSidebarWidthCacheKey(userId)));
  return Number.isFinite(value) && value >= SIDEBAR_WIDTH_LIMITS.min && value <= SIDEBAR_WIDTH_LIMITS.max
    ? value
    : getDefaultSidebarWidth();
};

const readCachedMenuOrder = (userId: number | undefined): string[] | null => {
  if (userId === undefined) {
    return null;
  }
  try {
    const cachedValue = window.localStorage.getItem(getMenuOrderCacheKey(userId));
    if (cachedValue === null) {
      return null;
    }
    const parsedMenuOrder: unknown = JSON.parse(cachedValue);
    if (!Array.isArray(parsedMenuOrder)) {
      window.localStorage.removeItem(getMenuOrderCacheKey(userId));
      return null;
    }
    return normalizeMenuOrder(parsedMenuOrder);
  } catch {
    window.localStorage.removeItem(getMenuOrderCacheKey(userId));
    return null;
  }
};

const cacheMenuOrder = (userId: number | undefined, menuOrder: string[]) => {
  if (userId === undefined) {
    return;
  }
  window.localStorage.setItem(getMenuOrderCacheKey(userId), JSON.stringify(menuOrder));
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
  const { mode, autoThemeEnabled, setAutoThemeEnabled, toggleMode } = useAppTheme();
  const { user, logout } = useAuth();
  const sidebarResizeStateRef = useRef<SidebarResizeState | null>(null);
  const [pageHeader, setPageHeader] = useState<PageHeaderState | null>(null);
  const [menuOrder, setMenuOrder] = useState<string[]>(() => readCachedMenuOrder(user?.id) ?? DEFAULT_MENU_ORDER);
  const [sidebarWidth, setSidebarWidth] = useState(() => readCachedSidebarWidth(user?.id));
  const [backendStatus, setBackendStatus] = useState<'checking' | 'online' | 'offline'>('checking');
  const isDocumentationPage = location.pathname === '/docs' || location.pathname.startsWith('/docs/');
  const documentationActionLabel = isDocumentationPage
    ? t('layout.returnToSystem')
    : t('layout.documentationBrowse');
  const backendStatusLabel = backendStatus === 'online'
    ? t('layout.online')
    : backendStatus === 'offline'
      ? t('layout.offline')
      : t('layout.checking');

  useEffect(() => {
    let active = true;
    const cachedMenuOrder = readCachedMenuOrder(user?.id);
    setSidebarWidth(readCachedSidebarWidth(user?.id));
    setMenuOrder(cachedMenuOrder ?? DEFAULT_MENU_ORDER);
    void (request.get('/userPreferences/menuOrder') as unknown as Promise<ApiResponse<UserMenuPreferenceResponse>>)
      .then((response) => {
        if (!active) {
          return;
        }
        if (!response.success) {
          return;
        }
        const resolvedMenuOrder = normalizeMenuOrder(response.data?.menuOrder);
        cacheMenuOrder(user?.id, resolvedMenuOrder);
        setMenuOrder(resolvedMenuOrder);
      })
      .catch(() => undefined);
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
    const timer = window.setInterval(() => void checkBackendStatus(), 10_000);
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
    { key: '/orderRecords', icon: <HistoryOutlined />, label: t('layout.orderRecords') },
    { key: '/logs', icon: <FileTextOutlined />, label: t('layout.logs') },
    { key: '/agentConversation', icon: <MessageOutlined />, label: t('layout.agentConversation') },
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
    void (request.put('/userPreferences/menuOrder', { menuOrder: normalizedOrder }) as unknown as Promise<ApiResponse<UserMenuPreferenceResponse>>)
      .then((response) => {
        if (response.success) {
          cacheMenuOrder(user?.id, normalizeMenuOrder(response.data?.menuOrder));
        }
      })
      .catch(() => undefined);
  };

  const selectMenuItem = (key: string) => {
    navigate(key);
    if (isMobile) {
      setSidebarCollapsed(true);
    }
  };

  const resolveSidebarWidth = (width: number) => Math.min(
    SIDEBAR_WIDTH_LIMITS.max,
    Math.max(SIDEBAR_WIDTH_LIMITS.min, width),
  );

  const persistSidebarWidth = (width: number) => {
    window.localStorage.setItem(getSidebarWidthCacheKey(user?.id), String(Math.round(width)));
  };

  const beginSidebarResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (sidebarCollapsed || isMobile) {
      return;
    }
    sidebarResizeStateRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startWidth: sidebarWidth,
      currentWidth: sidebarWidth,
      previousCursor: window.document.body.style.cursor,
      previousUserSelect: window.document.body.style.userSelect,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
    window.document.body.style.cursor = 'col-resize';
    window.document.body.style.userSelect = 'none';
  };

  const continueSidebarResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    const state = sidebarResizeStateRef.current;
    if (!state || state.pointerId !== event.pointerId) {
      return;
    }
    const width = resolveSidebarWidth(state.startWidth + event.clientX - state.startX);
    state.currentWidth = width;
    setSidebarWidth(width);
  };

  const finishSidebarResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    const state = sidebarResizeStateRef.current;
    if (!state || state.pointerId !== event.pointerId) {
      return;
    }
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    window.document.body.style.cursor = state.previousCursor;
    window.document.body.style.userSelect = state.previousUserSelect;
    persistSidebarWidth(state.currentWidth);
    sidebarResizeStateRef.current = null;
  };

  const resizeSidebarByKeyboard = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Home') {
      event.preventDefault();
      window.localStorage.removeItem(getSidebarWidthCacheKey(user?.id));
      setSidebarWidth(getDefaultSidebarWidth());
      return;
    }
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
      return;
    }
    event.preventDefault();
    const width = resolveSidebarWidth(sidebarWidth + (event.key === 'ArrowRight' ? 16 : -16));
    setSidebarWidth(width);
    persistSidebarWidth(width);
  };

  const resetSidebarWidth = () => {
    window.localStorage.removeItem(getSidebarWidthCacheKey(user?.id));
    setSidebarWidth(getDefaultSidebarWidth());
  };

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'username',
      label: user?.displayName ?? user?.username ?? '当前用户',
      disabled: true,
    },
    { type: 'divider' },
    {
      key: 'adaptiveTheme',
      label: (
        <span className="flex min-w-[140px] items-center justify-between gap-4">
          <span>{t('layout.adaptiveTheme')}</span>
          {autoThemeEnabled && <CheckOutlined className="text-[var(--brand)]" />}
        </span>
      ),
    },
    { type: 'divider' },
    { key: 'logout', label: t('common.logout'), danger: true },
  ];

  const handleUserMenuClick: MenuProps['onClick'] = async ({ key }) => {
    if (key === 'adaptiveTheme') {
      setAutoThemeEnabled(!autoThemeEnabled);
      return;
    }
    if (key !== 'logout') {
      return;
    }
    await logout();
    navigate('/login', { replace: true });
  };

  const pageTitles: Record<string, string> = {
    '/dashboard': t('layout.pageTitles.dashboard'),
    '/market': t('layout.pageTitles.market'),
    '/orderRecords': t('layout.pageTitles.orderRecords'),
    '/logs': t('layout.pageTitles.logs'),
    '/agentConversation': t('layout.pageTitles.agentConversation'),
  };
  const appShellStyle = { '--app-sidebar-width': `${sidebarWidth}px` } as CSSProperties;


  return (
    <PageHeaderContext.Provider value={setPageHeader}>
      <Layout className="app-shell" style={appShellStyle}>
      {!isDocumentationPage && isMobile && !sidebarCollapsed && (
        <button
          type="button"
          className="app-sidebar-backdrop"
          aria-label="关闭主菜单"
          onClick={() => setSidebarCollapsed(true)}
        />
      )}
      {!isDocumentationPage && (
        <Sider
          id="primary-navigation"
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
          width={sidebarWidth}
          className="app-sidebar"
        >
          <SystemBrand
            collapsed={sidebarCollapsed}
            status={backendStatus}
            statusLabel={backendStatusLabel}
            title={t('login.algoTrading')}
          />

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
          {!sidebarCollapsed && !isMobile && (
            <div
              className="app-sidebar-resize-handle"
              role="separator"
              aria-label="调整主菜单宽度"
              aria-orientation="vertical"
              tabIndex={0}
              title="拖动调整宽度，双击恢复默认"
              onPointerDown={beginSidebarResize}
              onPointerMove={continueSidebarResize}
              onPointerUp={finishSidebarResize}
              onPointerCancel={finishSidebarResize}
              onKeyDown={resizeSidebarByKeyboard}
              onDoubleClick={resetSidebarWidth}
            />
          )}
        </Sider>
      )}

      <Layout className="app-main-layout">
        <Header className={`app-header${isDocumentationPage ? ' app-header-documentation-page' : ''}`}>
          {isDocumentationPage ? (
            <SystemBrand
              className="app-header-brand"
              status={backendStatus}
              statusLabel={backendStatusLabel}
              title={t('login.algoTrading')}
            />
          ) : (
            <div className="app-header-identity">
              <Tooltip title={sidebarCollapsed ? t('layout.expandMenu') : t('layout.collapseMenu')}>
                <Button
                  type="text"
                  icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                  aria-controls="primary-navigation"
                  aria-expanded={!sidebarCollapsed}
                  onClick={() => setSidebarCollapsed((collapsed) => !collapsed)}
                />
              </Tooltip>
              <div className="min-w-0 flex-1">
                <div className="app-page-title truncate">
                  {pageTitles[location.pathname] ?? t('layout.title')}
                </div>
                <div className="truncate text-xs text-[var(--text-muted)]">
                  {pageHeader?.subtitle ?? 'Stock Operations Console'}
                </div>
              </div>
            </div>
          )}

          <div className="app-header-tools">
            <Tooltip title={documentationActionLabel}>
              <Button
                type="text"
                className="app-header-documentation"
                onClick={() => navigate(isDocumentationPage ? '/dashboard' : '/docs')}
                icon={isDocumentationPage ? <ArrowLeftOutlined /> : <ReadOutlined />}
                aria-label={documentationActionLabel}
              >
                <span className="app-header-documentation-label">
                  {documentationActionLabel}
                </span>
              </Button>
            </Tooltip>
            <Button
              type="text"
              className="app-header-language !h-8 !px-2 text-xs"
              onClick={changeLanguage}
            >
              {i18n.language === 'en' ? 'EN' : '中文'}
            </Button>
            <Tooltip title={mode === 'dark' ? t('layout.lightMode') : t('layout.darkMode')}>
              <Button
                type="text"
                className="app-theme-picker-button"
                icon={mode === 'dark' ? <SunOutlined /> : <MoonOutlined />}
                onClick={toggleMode}
                aria-label={mode === 'dark' ? t('layout.lightMode') : t('layout.darkMode')}
              />
            </Tooltip>
            <Badge dot color="#22c55e" className="app-header-notification">
              <Button type="text" icon={<BellOutlined />} />
            </Badge>
            <Dropdown menu={{ items: userMenuItems, onClick: handleUserMenuClick }} trigger={['click']}>
              <Button type="text" className="!h-8 !px-1" aria-label={t('layout.userMenu')}>
                <Avatar size={30} icon={<UserOutlined />} className="app-header-avatar" />
              </Button>
            </Dropdown>
          </div>
        </Header>

        <Content className={`app-content${isDocumentationPage ? ' app-content-documentation' : ''}`}>
          <Outlet />
        </Content>
      </Layout>
      </Layout>
    </PageHeaderContext.Provider>
  );
};

export default DashboardLayout;
// AI_GENERATE_END----------------------------------------------------------------
