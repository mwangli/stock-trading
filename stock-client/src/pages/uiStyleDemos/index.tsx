// AI_GENERATE_START ------
import React, { useEffect } from 'react';
import {
  Bot, Contrast, FileClock, LayoutDashboard, Monitor, Moon,
  ScrollText, Smartphone, Sun,
} from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import DesktopPreview from './DesktopPreview';
import SwipeMobilePreview from './SwipeMobilePreview';
import {
  demoStyle, type DemoPageId, type DemoThemeMode, type DemoViewport,
} from './demoConfig';
import styles from './styles.module.css';

const pageIds = new Set<DemoPageId>(['overview', 'records', 'agent', 'logs']);
const themeModes = new Set<DemoThemeMode>(['light', 'dark']);
const viewportModes = new Set<DemoViewport>(['desktop', 'mobile', 'compare']);
const pageOptions = [
  { id: 'overview' as const, label: '收益总览', icon: LayoutDashboard },
  { id: 'records' as const, label: '交易记录', icon: FileClock },
  { id: 'agent' as const, label: 'AGENT 对话', icon: Bot },
  { id: 'logs' as const, label: '系统日志', icon: ScrollText },
];

const readPage = (value: string | null): DemoPageId => (
  value && pageIds.has(value as DemoPageId) ? value as DemoPageId : 'overview'
);
const readTheme = (value: string | null): DemoThemeMode => (
  value && themeModes.has(value as DemoThemeMode) ? value as DemoThemeMode : 'light'
);
const readViewport = (value: string | null): DemoViewport => (
  value && viewportModes.has(value as DemoViewport) ? value as DemoViewport : 'compare'
);

const UiStyleDemos: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const pageId = readPage(searchParams.get('page'));
  const themeMode = readTheme(searchParams.get('theme'));
  const viewport = readViewport(searchParams.get('viewport'));

  usePageHeader('Blue Ledger PC 完整工作台与移动端重点功能 Demo');

  useEffect(() => {
    if (window.location.hash !== '#demo-preview') return undefined;
    const frame = window.requestAnimationFrame(() => {
      document.getElementById('demo-preview')?.scrollIntoView({ block: 'start' });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [pageId, themeMode, viewport]);

  const updateQuery = (updates: Partial<Record<'page' | 'theme' | 'viewport', string>>) => {
    const next = new URLSearchParams(searchParams);
    next.delete('style');
    Object.entries(updates).forEach(([key, value]) => {
      if (value) next.set(key, value);
    });
    setSearchParams(next, { replace: true });
  };

  return (
    <div className={styles.page}>
      <section className={styles.intro}>
        <div>
          <h1>{demoStyle.name} · {demoStyle.chineseName}</h1>
          <p>{demoStyle.description}。Demo 使用静态示例数据，不连接或修改业务接口。</p>
        </div>
        <div className={styles.scopeNote}>
          <Contrast size={18} aria-hidden="true" />
          <span>PC 完整信息架构<br />移动端四个重点入口</span>
        </div>
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
            <SwipeMobilePreview pageId={pageId} themeMode={themeMode} onPageChange={(page) => updateQuery({ page })} />
          </div>
        )}
      </section>
    </div>
  );
};

export default UiStyleDemos;
// AI_GENERATE_END ------
