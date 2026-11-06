// AI_GENERATE_START ----
import React, {
  useEffect,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { Avatar, Button, Drawer, Layout, Menu, Tooltip, type MenuProps } from 'antd';
import { useTranslation } from 'react-i18next';
import {
  CheckOutlined,
  DesktopOutlined,
  FileTextOutlined,
  LeftOutlined,
  LineChartOutlined,
  MenuUnfoldOutlined,
  MessageOutlined,
  MoonOutlined,
  RightOutlined,
  SunOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useAppTheme } from '@shared/theme/AppThemeProvider';
import { useAuth } from '@shared/auth/AuthContext';
import { PageHeaderContext, type PageHeaderState } from './PageHeaderContext';

const { Header, Content } = Layout;

const MOBILE_ROUTES = ['/dashboard', '/tradeRecords', '/agentConversation', '/logs'] as const;

const SWIPE_EXCLUSION_SELECTOR = [
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

interface MobileMenuItem {
  key: typeof MOBILE_ROUTES[number];
  icon: ReactNode;
  label: string;
}

interface SwipeState {
  pointerId: number;
  startX: number;
  startY: number;
  startedAt: number;
}

interface EdgeFeedback {
  side: 'start' | 'end';
  sequence: number;
}

const MobileLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation();
  const { mode, autoThemeEnabled, setAutoThemeEnabled, toggleMode } = useAppTheme();
  const { user, logout } = useAuth();
  const reduceMotion = useReducedMotion();
  const swipeStateRef = useRef<SwipeState | null>(null);
  const edgeFeedbackTimerRef = useRef<number | null>(null);
  const [navigationOpen, setNavigationOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [swipeDirection, setSwipeDirection] = useState(1);
  const [edgeFeedback, setEdgeFeedback] = useState<EdgeFeedback | null>(null);
  const [pageHeader, setPageHeader] = useState<PageHeaderState | null>(null);

  const menuItems: MobileMenuItem[] = [
    { key: '/dashboard', icon: <DesktopOutlined />, label: t('layout.dashboard') },
    { key: '/tradeRecords', icon: <LineChartOutlined />, label: t('layout.tradeRecords') },
    { key: '/agentConversation', icon: <MessageOutlined />, label: t('layout.agentConversation') },
    { key: '/logs', icon: <FileTextOutlined />, label: t('layout.logs') },
  ];
  const pageIndex = MOBILE_ROUTES.findIndex((route) => route === location.pathname);

  const showEdgeFeedback = (side: EdgeFeedback['side']) => {
    if (edgeFeedbackTimerRef.current !== null) window.clearTimeout(edgeFeedbackTimerRef.current);
    setEdgeFeedback({ side, sequence: performance.now() });
    edgeFeedbackTimerRef.current = window.setTimeout(() => {
      setEdgeFeedback(null);
      edgeFeedbackTimerRef.current = null;
    }, 900);
  };

  useEffect(() => () => {
    if (edgeFeedbackTimerRef.current !== null) window.clearTimeout(edgeFeedbackTimerRef.current);
  }, []);

  const navigateToPage = (nextIndex: number, showBoundary = false) => {
    if (nextIndex < 0 || nextIndex >= MOBILE_ROUTES.length) {
      if (showBoundary) showEdgeFeedback(nextIndex < 0 ? 'start' : 'end');
      return;
    }
    if (nextIndex === pageIndex) return;
    setSwipeDirection(nextIndex > pageIndex ? 1 : -1);
    navigate(MOBILE_ROUTES[nextIndex]);
    setNavigationOpen(false);
  };

  const selectPage = (path: string) => {
    const nextIndex = MOBILE_ROUTES.findIndex((route) => route === path);
    if (nextIndex < 0) return;
    if (nextIndex === pageIndex) {
      setNavigationOpen(false);
      return;
    }
    navigateToPage(nextIndex);
  };

  const beginSwipe = (event: ReactPointerEvent<HTMLElement>) => {
    if (pageIndex < 0 || (event.pointerType === 'mouse' && event.button !== 0)) return;
    if ((event.target as HTMLElement).closest(SWIPE_EXCLUSION_SELECTOR)) {
      swipeStateRef.current = null;
      return;
    }
    swipeStateRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      startedAt: performance.now(),
    };
  };

  const continueSwipe = (event: ReactPointerEvent<HTMLElement>) => {
    const state = swipeStateRef.current;
    if (!state || state.pointerId !== event.pointerId) return;
    const distanceX = event.clientX - state.startX;
    const distanceY = event.clientY - state.startY;
    if (Math.abs(distanceY) > 12 && Math.abs(distanceY) > Math.abs(distanceX) * 1.1) {
      swipeStateRef.current = null;
    }
  };

  const finishSwipe = (event: ReactPointerEvent<HTMLElement>) => {
    const state = swipeStateRef.current;
    swipeStateRef.current = null;
    if (!state || state.pointerId !== event.pointerId) return;
    const distanceX = event.clientX - state.startX;
    const distanceY = event.clientY - state.startY;
    const duration = Math.max(1, performance.now() - state.startedAt);
    const velocityX = Math.abs(distanceX) / duration * 1000;
    if (Math.abs(distanceX) <= Math.abs(distanceY) * 1.25 || (Math.abs(distanceX) < 56 && velocityX < 450)) return;
    navigateToPage(distanceX < 0 ? pageIndex + 1 : pageIndex - 1, true);
  };

  const handleKeyDown = (event: ReactKeyboardEvent<HTMLElement>) => {
    if (pageIndex < 0 || (event.target as HTMLElement).closest(SWIPE_EXCLUSION_SELECTOR)) return;
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    navigateToPage(pageIndex + (event.key === 'ArrowRight' ? 1 : -1), true);
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
    setUserMenuOpen(false);
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
    '/tradeRecords': t('layout.pageTitles.tradeRecords'),
    '/agentConversation': t('layout.pageTitles.agentConversation'),
    '/logs': t('layout.pageTitles.logs'),
  };

  return (
    <PageHeaderContext.Provider value={setPageHeader}>
      <Layout className="app-shell">
        <Layout className="app-main-layout">
          <Header className="app-header">
            <div className="app-header-identity">
              <Tooltip title="打开页面菜单">
                <Button type="text" icon={<MenuUnfoldOutlined />} aria-label="打开页面菜单" aria-expanded={navigationOpen} onClick={() => setNavigationOpen(true)} />
              </Tooltip>
              <div className="min-w-0 flex-1">
                <div className="app-page-title truncate">{pageTitles[location.pathname] ?? t('layout.title')}</div>
                <div className="app-header-subtitle truncate text-xs text-[var(--text-muted)]">{pageHeader?.subtitle ?? 'Stock Operations Console'}</div>
              </div>
            </div>
            <div className="app-header-tools">
              <Tooltip title={mode === 'dark' ? t('layout.lightMode') : t('layout.darkMode')}>
                <Button type="text" className="app-theme-picker-button" icon={mode === 'dark' ? <SunOutlined /> : <MoonOutlined />} onClick={toggleMode} />
              </Tooltip>
              <Button type="text" className="app-mobile-user-menu-button !px-1" aria-label={t('layout.userMenu')} aria-expanded={userMenuOpen} onClick={() => setUserMenuOpen(true)}>
                <Avatar size={30} icon={<UserOutlined />} className="app-header-avatar" />
              </Button>
            </div>
          </Header>
          <Content
            className="app-content"
            tabIndex={pageIndex >= 0 ? 0 : undefined}
            aria-label={pageIndex >= 0 ? '移动页面，可使用左右方向键切换' : undefined}
            onPointerDown={beginSwipe}
            onPointerMove={continueSwipe}
            onPointerUp={finishSwipe}
            onPointerCancel={() => { swipeStateRef.current = null; }}
            onKeyDown={handleKeyDown}
          >
            <motion.div
              className="app-route-boundary-shell"
              animate={{
                x: reduceMotion || !edgeFeedback
                  ? 0
                  : edgeFeedback.side === 'start'
                    ? [0, 14, -3, 0]
                    : [0, -14, 3, 0],
              }}
              transition={{ duration: reduceMotion ? 0 : 0.34, ease: 'easeOut' }}
            >
              <AnimatePresence initial={false} custom={swipeDirection} mode="popLayout">
                <motion.div
                  className="app-route-page"
                  key={location.pathname}
                  custom={swipeDirection}
                  variants={{
                    enter: (direction: number) => ({ x: reduceMotion ? 0 : direction > 0 ? '14%' : '-14%', opacity: reduceMotion ? 1 : 0.7 }),
                    center: { x: 0, opacity: 1 },
                    exit: (direction: number) => ({ x: reduceMotion ? 0 : direction > 0 ? '-10%' : '10%', opacity: reduceMotion ? 1 : 0.55 }),
                  }}
                  initial="enter"
                  animate="center"
                  exit="exit"
                  transition={{ duration: reduceMotion ? 0 : 0.22, ease: [0.22, 1, 0.36, 1] }}
                >
                  <Outlet />
                </motion.div>
              </AnimatePresence>
            </motion.div>
            <AnimatePresence>
              {edgeFeedback && (
                <motion.div
                  key={edgeFeedback.sequence}
                  className={`app-swipe-edge-feedback is-${edgeFeedback.side}`}
                  role="status"
                  aria-live="polite"
                  initial={{ opacity: 0, x: reduceMotion ? 0 : edgeFeedback.side === 'start' ? -18 : 18, scale: reduceMotion ? 1 : 0.92 }}
                  animate={{ opacity: 1, x: 0, scale: 1 }}
                  exit={{ opacity: 0, scale: reduceMotion ? 1 : 0.96 }}
                  transition={{ duration: reduceMotion ? 0 : 0.18 }}
                >
                  {edgeFeedback.side === 'start' ? <LeftOutlined /> : <RightOutlined />}
                  <span>{edgeFeedback.side === 'start' ? '已经到最左' : '已经到最右'}</span>
                </motion.div>
              )}
            </AnimatePresence>
          </Content>
        </Layout>
        <Drawer rootClassName="app-mobile-navigation-drawer" title={t('layout.title')} placement="left" size="min(82vw, 300px)" open={navigationOpen} onClose={() => setNavigationOpen(false)}>
          <Menu
            className="app-mobile-navigation-menu"
            selectedKeys={[location.pathname]}
            items={menuItems.map((item) => ({ key: item.key, icon: item.icon, label: item.label }))}
            onClick={({ key }) => selectPage(key)}
          />
        </Drawer>
        <Drawer rootClassName="app-mobile-user-drawer" title={t('layout.userMenu')} placement="bottom" size="auto" open={userMenuOpen} onClose={() => setUserMenuOpen(false)}>
          <Menu className="app-mobile-user-menu" items={userMenuItems} selectable={false} onClick={handleUserMenuClick} />
        </Drawer>
      </Layout>
    </PageHeaderContext.Provider>
  );
};

export default MobileLayout;
// AI_GENERATE_END ----
