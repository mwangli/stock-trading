// AI_GENERATE_START ----
import React, { useEffect } from 'react';
import {
  Bot, Contrast, FileClock, LayoutDashboard, LayoutGrid, Monitor, Moon,
  ScrollText, Smartphone, Sun,
} from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import DesktopPreview from './DesktopPreview';
import SwipeMobilePreview from './SwipeMobilePreview';
import {
  mobileDemoStyles, type DemoMobileStyleId, type DemoPageId, type DemoThemeMode, type DemoViewport,
} from './demoConfig';
import styles from './styles.module.css';

const pageIds = new Set<DemoPageId>(['overview', 'records', 'agent', 'logs']);
const themeModes = new Set<DemoThemeMode>(['light', 'dark']);
const viewportModes = new Set<DemoViewport>(['desktop', 'mobile', 'compare']);
const mobileStyleIds = new Set<DemoMobileStyleId>(mobileDemoStyles.map((style) => style.id));
const pageOptions = [
  { id: 'overview' as const, label: '收益总览', icon: LayoutDashboard },
  { id: 'records' as const, label: '交易记录', icon: FileClock },
  { id: 'agent' as const, label: 'Agent 智能分析', icon: Bot },
  { id: 'logs' as const, label: '运行日志', icon: ScrollText },
];
const styleIcons = [Smartphone, LayoutDashboard, Contrast, ScrollText] as const;
const directStyleLabels: Record<DemoMobileStyleId, string> = {
  edge: '全宽',
  dock: '底栏',
  tabs: '页签',
  console: '控制台',
};

const readPage = (value: string | null): DemoPageId => (
  value && pageIds.has(value as DemoPageId) ? value as DemoPageId : 'overview'
);
const readTheme = (value: string | null): DemoThemeMode => (
  value && themeModes.has(value as DemoThemeMode) ? value as DemoThemeMode : 'light'
);
const readViewport = (value: string | null): DemoViewport => (
  value && viewportModes.has(value as DemoViewport) ? value as DemoViewport : 'mobile'
);
const readMobileStyle = (value: string | null): DemoMobileStyleId => (
  value && mobileStyleIds.has(value as DemoMobileStyleId) ? value as DemoMobileStyleId : 'edge'
);

const UiStyleDemos: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const pageId = readPage(searchParams.get('page'));
  const themeMode = readTheme(searchParams.get('theme'));
  const viewport = readViewport(searchParams.get('viewport'));
  const mobileStyleId = readMobileStyle(searchParams.get('style'));
  const selectedMobileStyle = mobileDemoStyles.find((style) => style.id === mobileStyleId) ?? mobileDemoStyles[0];
  const galleryMode = searchParams.get('mode') === 'gallery';
  const captureMode = searchParams.get('mode') === 'capture';
  const selectedStyleIndex = mobileDemoStyles.findIndex((style) => style.id === mobileStyleId);

  usePageHeader('移动端全宽 UI 方案评审');

  useEffect(() => {
    if (window.location.hash !== '#demo-preview') return undefined;
    const frame = window.requestAnimationFrame(() => {
      document.getElementById('demo-preview')?.scrollIntoView({ block: 'start' });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [mobileStyleId, pageId, themeMode, viewport]);

  const updateQuery = (updates: Partial<Record<'page' | 'theme' | 'viewport' | 'style' | 'mode', string>>) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(updates).forEach(([key, value]) => {
      if (value) next.set(key, value);
    });
    setSearchParams(next, { replace: true });
  };

  if (!galleryMode) {
    return (
      <div className={styles.directPreviewPage}>
        {!captureMode && <header className={styles.directToolbar}>
          <div className={styles.directHeading}>
            <span>方案 0{selectedStyleIndex + 1}</span>
            <strong>{selectedMobileStyle.chineseName}</strong>
          </div>
          <div className={styles.directActions}>
            <button type="button" aria-label={themeMode === 'light' ? '切换深色主题' : '切换浅色主题'} onClick={() => updateQuery({ theme: themeMode === 'light' ? 'dark' : 'light' })}>
              {themeMode === 'light' ? <Moon size={17} aria-hidden="true" /> : <Sun size={17} aria-hidden="true" />}
            </button>
            <button type="button" onClick={() => updateQuery({ mode: 'gallery' })}>
              <LayoutGrid size={17} aria-hidden="true" /><span>方案说明</span>
            </button>
          </div>
          <nav className={styles.directStyleTabs} aria-label="切换移动端 UI 方案">
            {mobileDemoStyles.map((mobileStyle) => (
              <button
                className={mobileStyle.id === mobileStyleId ? styles.directStyleActive : undefined}
                type="button"
                key={mobileStyle.id}
                aria-pressed={mobileStyle.id === mobileStyleId}
                aria-label={`预览${mobileStyle.chineseName}`}
                onClick={() => updateQuery({ style: mobileStyle.id, viewport: 'mobile' })}
              >
                {directStyleLabels[mobileStyle.id]}
              </button>
            ))}
          </nav>
        </header>}
        <main className={styles.directPreviewStage}>
          <SwipeMobilePreview key={mobileStyleId} pageId={pageId} themeMode={themeMode} styleId={mobileStyleId} onPageChange={(page) => updateQuery({ page })} />
        </main>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <section className={styles.intro}>
        <div>
          <h1>移动端全宽 UI · 4 套评审 Demo</h1>
          <p>针对页面左右留白重新设计内容容器与导航方式。每套方案均包含收益总览、交易记录、Agent 智能分析和运行日志，使用静态示例数据，不连接或修改业务接口。</p>
        </div>
        <div className={styles.scopeNote}>
          <Contrast size={18} aria-hidden="true" />
          <span>当前方案：{selectedMobileStyle.chineseName}<br />{selectedMobileStyle.feature}</span>
        </div>
      </section>

      <section className={styles.stylePicker} aria-label="移动端 UI 方案">
        {mobileDemoStyles.map((mobileStyle, index) => {
          const Icon = styleIcons[index] ?? Smartphone;
          const selected = mobileStyle.id === mobileStyleId;
          return (
            <button
              className={selected ? styles.styleOptionActive : styles.styleOption}
              type="button"
              key={mobileStyle.id}
              aria-pressed={selected}
              onClick={() => updateQuery({ style: mobileStyle.id, viewport: 'mobile', mode: 'preview' })}
            >
              <span className={styles.styleOptionIndex}>0{index + 1}</span>
              <Icon size={20} aria-hidden="true" />
              <strong>{mobileStyle.chineseName}</strong>
              <small>{mobileStyle.name}</small>
              <p>{mobileStyle.description}</p>
              <em>{mobileStyle.feature}</em>
            </button>
          );
        })}
      </section>

      <section className={styles.controlBar}>
        <div className={styles.pageTabs} aria-label="Demo 页面">
          {pageOptions.map(({ id, label, icon: Icon }) => (
            <button className={pageId === id ? styles.pageTabActive : undefined} type="button" key={id} aria-pressed={pageId === id} onClick={() => updateQuery({ page: id })}>
              <Icon size={16} aria-hidden="true" />{label}
            </button>
          ))}
        </div>
        <div className={styles.controls}>
          <div className={styles.segmented} aria-label="主题模式">
            <button className={themeMode === 'light' ? styles.segmentActive : undefined} type="button" aria-pressed={themeMode === 'light'} onClick={() => updateQuery({ theme: 'light' })}><Sun size={15} />浅色</button>
            <button className={themeMode === 'dark' ? styles.segmentActive : undefined} type="button" aria-pressed={themeMode === 'dark'} onClick={() => updateQuery({ theme: 'dark' })}><Moon size={15} />深色</button>
          </div>
          <div className={styles.segmented} aria-label="预览尺寸">
            <button className={viewport === 'desktop' ? styles.segmentActive : undefined} type="button" aria-pressed={viewport === 'desktop'} onClick={() => updateQuery({ viewport: 'desktop' })}><Monitor size={15} />PC</button>
            <button className={viewport === 'mobile' ? styles.segmentActive : undefined} type="button" aria-pressed={viewport === 'mobile'} onClick={() => updateQuery({ viewport: 'mobile' })}><Smartphone size={15} />移动端</button>
            <button className={viewport === 'compare' ? styles.segmentActive : undefined} type="button" aria-pressed={viewport === 'compare'} onClick={() => updateQuery({ viewport: 'compare' })}><Contrast size={15} />对比</button>
          </div>
        </div>
      </section>

      <section id="demo-preview" className={`${styles.previewGrid} ${styles[viewport]}`}>
        {viewport !== 'mobile' && (
          <div className={styles.previewColumn}>
            <div className={styles.previewLabel}><Monitor size={16} aria-hidden="true" /><span>PC 完整工作台</span><small>完整菜单、筛选和辅助信息</small></div>
            <DesktopPreview pageId={pageId} themeMode={themeMode} onPageChange={(page) => updateQuery({ page })} />
          </div>
        )}
        {viewport !== 'desktop' && (
          <div className={styles.previewColumn}>
            <div className={styles.previewLabel}><Smartphone size={16} aria-hidden="true" /><span>移动端重点功能</span><small>滑动切换四个入口</small></div>
            <SwipeMobilePreview key={mobileStyleId} pageId={pageId} themeMode={themeMode} styleId={mobileStyleId} onPageChange={(page) => updateQuery({ page })} />
          </div>
        )}
      </section>
    </div>
  );
};

export default UiStyleDemos;
// AI_GENERATE_END ----
