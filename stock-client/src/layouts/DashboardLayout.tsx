// AI_GENERATE_START--------------------------------------------------------------------------
import './DashboardLayout.css';
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
import { Avatar, Badge, Button, Drawer, Dropdown, Layout, Menu, Tooltip, type MenuProps } from 'antd';
import { useTranslation } from 'react-i18next';
import { AnimatePresence, motion, Reorder, useDragControls, useReducedMotion } from 'framer-motion';
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
  LineChartOutlined,
  FundProjectionScreenOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { useAppTheme } from '../theme/AppThemeProvider';
import { PageHeaderContext, type PageHeaderState } from './PageHeaderContext';
import { checkBackendHealth } from '../utils/backendHealth';
import { useAuth } from '../auth/AuthContext';
import SystemBrand from '../components/SystemBrand';

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
const MOBILE_SWIPE_ROUTES = [
  '/dashboard',
  '/tradeRecords',
  '/agentConversation',
  '/logs',
] as const;
const PAGE_SWIPE_EXCLUSION_SELECTOR = [
  'a',
  'button',
  'input',
  'textarea',
  'select',
  '[contenteditable="true"]',
  '[role="button"]',
  '[role="slider"]',
  '[role="tab"]',
  '[data-no-page-swipe]',
  '.ant-drawer',
  '.ant-modal',
  '.ant-table',
  '.order-records-swipe-area',
  'canvas',
].join(',');
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

interface PageSwipeState {
  pointerId: number;
  startX: number;
  startY: number;
  startedAt: number;
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
    if (key === '/orderRecords' || key === '/transactions') {
      return ['/historyOrders', '/tradeRecords'];
    }
    if (key === '/modelOperations') {
      return ['/modelInfo'];
    }
    return [key];
  });
  const validKeys = migratedOrder.filter(
    (key): key is string => typeof key === 'string' && DEFAULT_MENU_ORDER.includes(key),
  );
  const uniqueKeys = [...new Set(validKeys)];
  const completedOrder = [...uniqueKeys, ...DEFAULT_MENU_ORDER.filter((key) => !uniqueKeys.includes(key))];
  if (!uniqueKeys.includes('/stockInformation')) {
    const stockInformationIndex = completedOrder.indexOf('/stockInformation');
    completedOrder.splice(stockInformationIndex, 1);
    const marketIndex = completedOrder.indexOf('/market');
    completedOrder.splice(marketIndex + 1, 0, '/stockInformation');
  }
  return completedOrder;
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
  const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false);
  const [mobileUserMenuOpen, setMobileUserMenuOpen] = useState(false);
  const [swipeDirection, setSwipeDirection] = useState(1);
  const navigate = useNavigate();
  const location = useLocation();
  const { t, i18n } = useTranslation();
  const { mode, autoThemeEnabled, setAutoThemeEnabled, toggleMode } = useAppTheme();
  const { user, logout } = useAuth();
  const sidebarResizeStateRef = useRef<SidebarResizeState | null>(null);
  const pageSwipeStateRef = useRef<PageSwipeState | null>(null);
  const reduceMotion = useReducedMotion();
  const [pageHeader, setPageHeader] = useState<PageHeaderState | null>(null);
  const [menuOrder, setMenuOrder] = useState<string[]>(() => readCachedMenuOrder(user?.id) ?? DEFAULT_MENU_ORDER);
  const [sidebarWidth, setSidebarWidth] = useState(() => readCachedSidebarWidth(user?.id));
  const [backendStatus, setBackendStatus] = useState<'checking' | 'online' | 'offline'>('checking');

  const backendStatusLabel = backendStatus === 'online'
    ? t('layout.online')
    : backendStatus === 'offline'
      ? t('layout.offline')
      : t('layout.checking');

  useEffect(() => {
    const cachedMenuOrder = readCachedMenuOrder(user?.id);
    setSidebarWidth(readCachedSidebarWidth(user?.id));
    setMenuOrder(cachedMenuOrder ?? DEFAULT_MENU_ORDER);
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
    { key: '/stockInformation', icon: <UnorderedListOutlined />, label: t('layout.stockInformation') },
    { key: '/historyOrders', icon: <HistoryOutlined />, label: t('layout.historyOrders') },
    { key: '/tradeRecords', icon: <LineChartOutlined />, label: t('layout.tradeRecords') },
    { key: '/modelInfo', icon: <FundProjectionScreenOutlined />, label: t('layout.modelInfo') },
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

  const mobileMenuItems = useMemo(() => {
    const itemMap = new Map(menuItems.map((item) => [item.key, item]));
    return MOBILE_SWIPE_ROUTES
      .map((key) => itemMap.get(key))
      .filter((item): item is SidebarMenuItem => Boolean(item));
  }, [menuItems]);

  const mobilePageIndex = MOBILE_SWIPE_ROUTES.findIndex((route) => route === location.pathname);

  const reorderMenu = (nextOrder: string[]) => {
    const normalizedOrder = normalizeMenuOrder(nextOrder);
    setMenuOrder(normalizedOrder);
    cacheMenuOrder(user?.id, normalizedOrder);
  };

  const selectMenuItem = (key: string) => {
    const nextMobileIndex = MOBILE_SWIPE_ROUTES.findIndex((route) => route === key);
    if (isMobile && mobilePageIndex >= 0 && nextMobileIndex >= 0 && nextMobileIndex !== mobilePageIndex) {
      setSwipeDirection(nextMobileIndex > mobilePageIndex ? 1 : -1);
    }
    navigate(key);
    if (isMobile) {
      setSidebarCollapsed(true);
      setMobileNavigationOpen(false);
    }
  };

  const navigateToMobilePage = (nextIndex: number) => {
    if (nextIndex < 0 || nextIndex >= MOBILE_SWIPE_ROUTES.length || nextIndex === mobilePageIndex) {
      return;
    }
    setSwipeDirection(nextIndex > mobilePageIndex ? 1 : -1);
    navigate(MOBILE_SWIPE_ROUTES[nextIndex]);
    setMobileNavigationOpen(false);
  };

  const beginPageSwipe = (event: ReactPointerEvent<HTMLElement>) => {
    if (!isMobile || mobilePageIndex < 0 || (event.pointerType === 'mouse' && event.button !== 0)) {
      return;
    }
    if ((event.target as HTMLElement).closest(PAGE_SWIPE_EXCLUSION_SELECTOR)) {
      pageSwipeStateRef.current = null;
      return;
    }
    pageSwipeStateRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      startedAt: performance.now(),
    };
  };

  const continuePageSwipe = (event: ReactPointerEvent<HTMLElement>) => {
    const state = pageSwipeStateRef.current;
    if (!state || state.pointerId !== event.pointerId) {
      return;
    }
    const distanceX = event.clientX - state.startX;
    const distanceY = event.clientY - state.startY;
    if (Math.abs(distanceY) > 12 && Math.abs(distanceY) > Math.abs(distanceX) * 1.1) {
      pageSwipeStateRef.current = null;
    }
  };

  const finishPageSwipe = (event: ReactPointerEvent<HTMLElement>) => {
    const state = pageSwipeStateRef.current;
    pageSwipeStateRef.current = null;
    if (!state || state.pointerId !== event.pointerId) {
      return;
    }
    const distanceX = event.clientX - state.startX;
    const distanceY = event.clientY - state.startY;
    const duration = Math.max(1, performance.now() - state.startedAt);
    const velocityX = Math.abs(distanceX) / duration * 1000;
    const horizontalGesture = Math.abs(distanceX) > Math.abs(distanceY) * 1.25;
    if (!horizontalGesture || (Math.abs(distanceX) < 56 && velocityX < 450)) {
      return;
    }
    navigateToMobilePage(distanceX < 0 ? mobilePageIndex + 1 : mobilePageIndex - 1);
  };

  const handleMobilePageKeyDown = (event: ReactKeyboardEvent<HTMLElement>) => {
    if (!isMobile || mobilePageIndex < 0 || (event.target as HTMLElement).closest(PAGE_SWIPE_EXCLUSION_SELECTOR)) {
      return;
    }
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
      return;
    }
    event.preventDefault();
    navigateToMobilePage(mobilePageIndex + (event.key === 'ArrowRight' ? 1 : -1));
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
    setMobileUserMenuOpen(false);
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
    '/stockInformation': t('layout.pageTitles.stockInformation'),
    '/historyOrders': t('layout.pageTitles.historyOrders'),
    '/tradeRecords': t('layout.pageTitles.tradeRecords'),
    '/modelInfo': t('layout.pageTitles.modelInfo'),
    '/logs': t('layout.pageTitles.logs'),
    '/agentConversation': t('layout.pageTitles.agentConversation'),
  };
  const appShellStyle = { '--app-sidebar-width': `${sidebarWidth}px` } as CSSProperties;


  return (
    <PageHeaderContext.Provider value={setPageHeader}>
      <Layout className="app-shell" style={appShellStyle}>

        <Sider
          id="primary-navigation"
          trigger={null}
          collapsible
          collapsed={sidebarCollapsed}
          collapsedWidth={isMobile ? 0 : 76}
          breakpoint="lg"
          onBreakpoint={(broken) => {
            setIsMobile(broken);
            setMobileNavigationOpen(false);
            setMobileUserMenuOpen(false);
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

      <Layout className="app-main-layout">
        <Header className="app-header">
            <div className="app-header-identity">
              {isMobile ? (
                <Tooltip title="打开页面菜单">
                  <Button
                    type="text"
                    icon={<MenuUnfoldOutlined />}
                    aria-label="打开页面菜单"
                    aria-expanded={mobileNavigationOpen}
                    onClick={() => setMobileNavigationOpen(true)}
                  />
                </Tooltip>
              ) : (
                <Tooltip title={sidebarCollapsed ? t('layout.expandMenu') : t('layout.collapseMenu')}>
                  <Button
                    type="text"
                    icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                    aria-controls="primary-navigation"
                    aria-expanded={!sidebarCollapsed}
                    onClick={() => setSidebarCollapsed((collapsed) => !collapsed)}
                  />
                </Tooltip>
              )}
              <div className="min-w-0 flex-1">
                <div className="app-page-title truncate">
                  {pageTitles[location.pathname] ?? t('layout.title')}
                </div>
                <div className="truncate text-xs text-[var(--text-muted)]">
                  {pageHeader?.subtitle ?? 'Stock Operations Console'}
                </div>
              </div>
            </div>

          <div className="app-header-tools">
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
            {isMobile ? (
              <Button
                type="text"
                className="app-mobile-user-menu-button !px-1"
                aria-label={t('layout.userMenu')}
                aria-expanded={mobileUserMenuOpen}
                onClick={() => setMobileUserMenuOpen(true)}
              >
                <Avatar size={30} icon={<UserOutlined />} className="app-header-avatar" />
              </Button>
            ) : (
              <Dropdown menu={{ items: userMenuItems, onClick: handleUserMenuClick }} trigger={['click']}>
                <Button type="text" className="!h-8 !px-1" aria-label={t('layout.userMenu')}>
                  <Avatar size={30} icon={<UserOutlined />} className="app-header-avatar" />
                </Button>
              </Dropdown>
            )}
          </div>
        </Header>

        <Content
          className="app-content"
          tabIndex={isMobile && mobilePageIndex >= 0 ? 0 : undefined}
          aria-label={isMobile && mobilePageIndex >= 0 ? '移动页面，可使用左右方向键切换' : undefined}
          onPointerDown={beginPageSwipe}
          onPointerMove={continuePageSwipe}
          onPointerUp={finishPageSwipe}
          onPointerCancel={() => { pageSwipeStateRef.current = null; }}
          onKeyDown={handleMobilePageKeyDown}
        >
          <AnimatePresence initial={false} custom={swipeDirection} mode="popLayout">
            <motion.div
              className="app-route-page"
              key={location.pathname}
              custom={swipeDirection}
              variants={{
                enter: (direction: number) => ({
                  x: reduceMotion || !isMobile ? 0 : direction > 0 ? '14%' : '-14%',
                  opacity: reduceMotion || !isMobile ? 1 : 0.7,
                }),
                center: { x: 0, opacity: 1 },
                exit: (direction: number) => ({
                  x: reduceMotion || !isMobile ? 0 : direction > 0 ? '-10%' : '10%',
                  opacity: reduceMotion || !isMobile ? 1 : 0.55,
                }),
              }}
              initial="enter"
              animate="center"
              exit="exit"
              transition={{ duration: reduceMotion || !isMobile ? 0 : 0.22, ease: [0.22, 1, 0.36, 1] }}
            >
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </Content>
      </Layout>
      {isMobile && mobilePageIndex >= 0 && (
        <nav className="app-mobile-page-indicator" aria-label="移动页面导航">
          {mobileMenuItems.map((item, index) => {
            const selected = index === mobilePageIndex;
            return (
              <button
                key={item.key}
                type="button"
                className={selected ? 'app-mobile-page-dot app-mobile-page-dot-active' : 'app-mobile-page-dot'}
                aria-label={`切换到${item.label}`}
                aria-current={selected ? 'page' : undefined}
                onClick={() => navigateToMobilePage(index)}
              />
            );
          })}
        </nav>
      )}
      {isMobile && (
        <>
          <Drawer
            rootClassName="app-mobile-navigation-drawer"
            title={t('layout.title')}
            placement="left"
            size="min(84vw, 320px)"
            open={mobileNavigationOpen}
            onClose={() => setMobileNavigationOpen(false)}
          >
            <Menu
              className="app-mobile-navigation-menu"
              selectedKeys={[location.pathname]}
              items={orderedMenuItems.map((item) => ({ key: item.key, icon: item.icon, label: item.label }))}
              onClick={({ key }) => selectMenuItem(key)}
            />
          </Drawer>
          <Drawer
            rootClassName="app-mobile-user-drawer"
            title={t('layout.userMenu')}
            placement="bottom"
            size="auto"
            open={mobileUserMenuOpen}
            onClose={() => setMobileUserMenuOpen(false)}
          >
            <Menu
              className="app-mobile-user-menu"
              items={userMenuItems}
              selectable={false}
              onClick={handleUserMenuClick}
            />
          </Drawer>
        </>
      )}
      </Layout>
    </PageHeaderContext.Provider>
  );
};

export default DashboardLayout;
// AI_GENERATE_END--------------------------------------------------------------------------
