// AI_GENERATE_START -----
import React, { useState } from 'react';
import { AnimatePresence, motion, type PanInfo, useDragControls, useReducedMotion } from 'framer-motion';
import { Bot, ChevronRight, FileClock, LayoutDashboard, Menu, ScrollText, Search, Send, X } from 'lucide-react';
import {
  agentMessages, chartAreaPoints, chartPoints, demoMetrics, demoStyle, logRows, tradeRows,
  type DemoPageId, type DemoThemeMode, toDemoCssVariables,
} from './demoConfig';
import styles from './mobile.module.css';

interface SwipeMobilePreviewProps {
  pageId: DemoPageId;
  themeMode: DemoThemeMode;
  onPageChange: (pageId: DemoPageId) => void;
}

const pages = [
  { id: 'overview' as const, label: '收益总览', subtitle: '账户与收益', icon: LayoutDashboard },
  { id: 'records' as const, label: '交易记录', subtitle: '最近成交', icon: FileClock },
  { id: 'agent' as const, label: 'AGENT 对话', subtitle: '智能问答', icon: Bot },
  { id: 'logs' as const, label: '系统日志', subtitle: '运行状态', icon: ScrollText },
];

const toneClass = (tone: 'positive' | 'negative' | 'neutral') => (
  tone === 'positive' ? styles.positive : tone === 'negative' ? styles.negative : styles.neutral
);

const SwipeMobilePreview: React.FC<SwipeMobilePreviewProps> = ({ pageId, themeMode, onPageChange }) => {
  const [direction, setDirection] = useState(1);
  const [menuOpen, setMenuOpen] = useState(false);
  const dragControls = useDragControls();
  const reduceMotion = useReducedMotion();
  const pageIndex = Math.max(0, pages.findIndex((page) => page.id === pageId));
  const activePage = pages[pageIndex];

  const goToPage = (nextIndex: number) => {
    const boundedIndex = Math.max(0, Math.min(pages.length - 1, nextIndex));
    if (boundedIndex === pageIndex) return;
    setDirection(boundedIndex > pageIndex ? 1 : -1);
    onPageChange(pages[boundedIndex].id);
    setMenuOpen(false);
  };

  const handleDragEnd = (_event: MouseEvent | TouchEvent | PointerEvent, info: PanInfo) => {
    if (Math.abs(info.offset.x) < 56 && Math.abs(info.velocity.x) < 450) return;
    goToPage(info.offset.x < 0 ? pageIndex + 1 : pageIndex - 1);
  };

  const handlePointerDown = (event: React.PointerEvent<HTMLElement>) => {
    if ((event.target as HTMLElement).closest('[data-no-swipe]')) return;
    dragControls.start(event);
  };

  const renderOverview = () => (
    <div className={styles.swipePageContent}>
      <section className={`${styles.balanceCard} ${styles.ledgerBalance}`}>
        <span>账户总资产</span><strong>¥414,230.00</strong><div><span>今日收益</span><b className={styles.positive}>+¥4,286.20&nbsp;&nbsp;+1.11%</b></div>
        <svg viewBox="0 0 312 100" role="img" aria-label="移动端资产走势示意图" preserveAspectRatio="none"><defs><linearGradient id={`swipe-area-${themeMode}`} x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="var(--demo-accent)" stopOpacity="0.24" /><stop offset="100%" stopColor="var(--demo-accent)" stopOpacity="0" /></linearGradient></defs><polygon points={chartAreaPoints} fill={`url(#swipe-area-${themeMode})`} /><polyline points={chartPoints} fill="none" stroke="var(--demo-accent)" strokeWidth="2.5" vectorEffect="non-scaling-stroke" /></svg>
      </section>
      <section className={styles.ledgerMetrics}>{demoMetrics.slice(0, 2).map((metric) => <article key={metric.label}><span>{metric.label}</span><strong>{metric.value}</strong><small>{metric.change}</small></article>)}</section>
      <section className={styles.ledgerSection}><div className={styles.ledgerSectionTitle}><h3>收益摘要</h3><span>今日</span></div>{demoMetrics.slice(2).map((metric) => <article className={styles.analysisRow} key={metric.label}><span>{metric.label}</span><strong>{metric.value}</strong><small className={toneClass(metric.tone)}>{metric.change}</small></article>)}</section>
    </div>
  );

  const renderRecords = () => (
    <div className={styles.swipePageContent}>
      <section className={styles.mobileLead}><span>今日交易</span><strong>{tradeRows.length} 笔</strong><small>按成交时间倒序</small></section>
      <section className={styles.ledgerSection}>{tradeRows.map((row) => <article className={styles.recordDetailRow} key={`${row.code}-${row.time}`}><span><strong>{row.name}</strong><small>{row.code} · {row.action} · {row.time.slice(11)}</small></span><span><strong>{row.amount}</strong><small className={toneClass(row.tone)}>{row.result}</small></span></article>)}</section>
    </div>
  );

  const renderAgent = () => (
    <div className={`${styles.swipePageContent} ${styles.mobileChatPage}`}>
      <section className={styles.mobileAgentHeader}><span><Bot size={18} /></span><div><strong>策略 Agent</strong><small>账户与运行数据已连接</small></div></section>
      <section className={styles.mobileMessages}>{agentMessages.slice(-3).map((message, index) => <article className={message.role === 'user' ? styles.mobileUserMessage : styles.mobileAgentMessage} key={`${message.time}-${index}`}><div><strong>{message.author}</strong><span>{message.time}</span></div><p>{message.content}</p></article>)}</section>
      <div className={styles.mobileComposer} data-no-swipe><input aria-label="Agent 问题" placeholder="输入问题" /><button type="button" aria-label="发送"><Send size={16} /></button></div>
    </div>
  );

  const renderLogs = () => (
    <div className={styles.swipePageContent}>
      <section className={styles.mobileLogStatus}><span className={styles.statusPulse} /><div><strong>系统运行正常</strong><small>最近更新 14:35:28</small></div></section>
      <section className={styles.mobileLogs}>{logRows.slice(0, 5).map((log, index) => <article key={`${log.time}-${index}`}><div><time>{log.time}</time><strong className={log.level === 'WARN' ? styles.warning : styles.info}>{log.level}</strong></div><code>{log.source}</code><p>{log.message}</p></article>)}</section>
    </div>
  );

  const content = { overview: renderOverview, records: renderRecords, agent: renderAgent, logs: renderLogs }[pageId]();

  return (
    <section className={`${styles.phone} ${styles.ledger} ${styles.swipePhone}`} style={toDemoCssVariables(demoStyle.themes[themeMode])} aria-label={`${activePage.label}移动端预览`} tabIndex={0} onKeyDown={(event) => { if (event.key === 'ArrowLeft') goToPage(pageIndex - 1); if (event.key === 'ArrowRight') goToPage(pageIndex + 1); }}>
      <div className={styles.statusBar}><span>14:36</span><span>5G&nbsp;&nbsp;92%</span></div>
      <header className={styles.swipeHeader}><div><h2>{activePage.label}</h2><p>{activePage.subtitle}</p></div><div className={styles.headerActions} data-no-swipe><button type="button" aria-label="搜索"><Search size={18} /></button><button type="button" aria-label={menuOpen ? '关闭页面菜单' : '打开页面菜单'} aria-expanded={menuOpen} onClick={() => setMenuOpen((open) => !open)}>{menuOpen ? <X size={19} /> : <Menu size={19} />}</button></div></header>
      <div className={styles.swipeViewport}><AnimatePresence initial={false} custom={direction}><motion.main className={styles.swipePage} key={activePage.id} custom={direction} variants={{ enter: (move: number) => ({ x: reduceMotion ? 0 : move > 0 ? '28%' : '-28%', opacity: reduceMotion ? 1 : 0.45 }), center: { x: 0, opacity: 1 }, exit: (move: number) => ({ x: reduceMotion ? 0 : move > 0 ? '-28%' : '28%', opacity: reduceMotion ? 1 : 0.45 }) }} initial="enter" animate="center" exit="exit" transition={{ duration: reduceMotion ? 0 : 0.22, ease: [0.22, 1, 0.36, 1] }} drag={reduceMotion ? false : 'x'} dragControls={dragControls} dragListener={false} dragConstraints={{ left: 0, right: 0 }} dragElastic={0.16} dragSnapToOrigin onPointerDown={handlePointerDown} onDragEnd={handleDragEnd}>{content}</motion.main></AnimatePresence></div>
      <div className={styles.pageIndicator} aria-label={`当前第 ${pageIndex + 1} 页，共 ${pages.length} 页`} data-no-swipe>{pages.map((page, index) => <button className={index === pageIndex ? styles.pageDotActive : styles.pageDot} type="button" key={page.id} aria-label={`切换到${page.label}`} aria-current={index === pageIndex ? 'page' : undefined} onClick={() => goToPage(index)} />)}</div>
      <AnimatePresence>{menuOpen && <motion.nav className={styles.swipeMenu} aria-label="Demo 移动页面菜单" data-no-swipe initial={{ opacity: reduceMotion ? 1 : 0, y: reduceMotion ? 0 : -8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: reduceMotion ? 1 : 0, y: reduceMotion ? 0 : -8 }} transition={{ duration: reduceMotion ? 0 : 0.16 }}>{pages.map(({ id, label, subtitle, icon: Icon }, index) => <button className={index === pageIndex ? styles.swipeMenuActive : undefined} type="button" key={id} onClick={() => goToPage(index)}><Icon size={18} /><span><strong>{label}</strong><small>{subtitle}</small></span><ChevronRight size={16} /></button>)}</motion.nav>}</AnimatePresence>
    </section>
  );
};

export default SwipeMobilePreview;
// AI_GENERATE_END -----
