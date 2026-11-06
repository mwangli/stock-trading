// AI_GENERATE_START --
import type { CSSProperties } from 'react';

export type DemoThemeMode = 'light' | 'dark';
export type DemoViewport = 'desktop' | 'mobile' | 'compare';
export type DemoPageId = 'overview' | 'records' | 'agent' | 'logs';
export type DemoMobileStyleId = 'edge' | 'dock' | 'tabs' | 'console';

export interface DemoTokens {
  canvas: string; surface: string; surfaceRaised: string; surfaceMuted: string;
  border: string; borderStrong: string; text: string; textSecondary: string; textMuted: string;
  accent: string; accentSoft: string; instrument: string; positive: string; negative: string;
  warning: string; shadow: string; radius: string; font: string; numberFont: string;
}

export interface DemoMobileStyle {
  id: DemoMobileStyleId;
  name: string;
  chineseName: string;
  description: string;
  feature: string;
  navigation: 'overlay' | 'bottom' | 'tabs' | 'rail';
  themes: Record<DemoThemeMode, DemoTokens>;
}

const systemSans = '"IBM Plex Sans", "Segoe UI", "Microsoft YaHei", Arial, sans-serif';
const numberMono = '"Cascadia Code", "SFMono-Regular", Consolas, monospace';

export const demoStyle = {
  id: 'ledger', name: 'Blue Ledger', chineseName: '蓝白极简账本',
  description: '蓝白机构配色与轻量账本布局，PC 保留完整工作台信息，移动端只展示高频重点。',
  themes: {
    light: {
      canvas: '#eef2f6', surface: '#ffffff', surfaceRaised: '#ffffff', surfaceMuted: '#f5f7fa',
      border: '#d7dee8', borderStrong: '#aeb9c8', text: '#172033', textSecondary: '#526075',
      textMuted: '#7d8999', accent: '#1f5fbf', accentSoft: '#e8f0fb', instrument: '#2176ae',
      positive: '#b42318', negative: '#067647', warning: '#b54708', shadow: '0 12px 28px rgb(24 39 75 / 10%)',
      radius: '7px', font: systemSans, numberFont: numberMono,
    },
    dark: {
      canvas: '#0b111b', surface: '#101925', surfaceRaised: '#14202e', surfaceMuted: '#182536',
      border: '#29384a', borderStrong: '#465970', text: '#edf3fb', textSecondary: '#b1bfd0',
      textMuted: '#7f8da0', accent: '#72a7ef', accentSoft: '#18345a', instrument: '#74b4df',
      positive: '#ff8f86', negative: '#5dd6a0', warning: '#ffb86b', shadow: '0 14px 34px rgb(0 0 0 / 28%)',
      radius: '7px', font: systemSans, numberFont: numberMono,
    },
  } satisfies Record<DemoThemeMode, DemoTokens>,
};

const createThemes = (
  light: Partial<DemoTokens>,
  dark: Partial<DemoTokens>,
): Record<DemoThemeMode, DemoTokens> => ({
  light: { ...demoStyle.themes.light, ...light },
  dark: { ...demoStyle.themes.dark, ...dark },
});

export const mobileDemoStyles: DemoMobileStyle[] = [
  {
    id: 'edge',
    name: 'Edge Ledger',
    chineseName: '全宽账本',
    description: '内容贴近屏幕边缘，用分隔线代替外层卡片，优先解决左右留白。',
    feature: '最大内容宽度 · 轻量菜单',
    navigation: 'overlay',
    themes: createThemes(
      { canvas: '#eef2f6', accent: '#1f5fbf', accentSoft: '#e8f0fb', radius: '4px' },
      { canvas: '#0b111b', accent: '#72a7ef', accentSoft: '#18345a', radius: '4px' },
    ),
  },
  {
    id: 'dock',
    name: 'Bottom Dock',
    chineseName: '底部导航',
    description: '底部常驻四入口，页面主体使用窄边距，适合高频单手切换。',
    feature: '单手操作 · 四入口直达',
    navigation: 'bottom',
    themes: createThemes(
      { canvas: '#f1f5f4', accent: '#0f766e', accentSoft: '#dff4ef', instrument: '#0e7490', radius: '8px' },
      { canvas: '#0b1414', surface: '#101d1c', surfaceRaised: '#132321', surfaceMuted: '#182b28', border: '#29413d', borderStrong: '#3d5b55', accent: '#5eead4', accentSoft: '#153e38', instrument: '#67e8f9', radius: '8px' },
    ),
  },
  {
    id: 'tabs',
    name: 'Top Tabs',
    chineseName: '顶部页签',
    description: '四页面固定在标题下方，主体采用满宽分区，浏览路径始终可见。',
    feature: '路径清晰 · 无抽屉操作',
    navigation: 'tabs',
    themes: createThemes(
      { canvas: '#f4f5f7', accent: '#475569', accentSoft: '#e7eaf0', instrument: '#0369a1', radius: '2px', shadow: '0 10px 24px rgb(15 23 42 / 8%)' },
      { canvas: '#0f1115', surface: '#15181e', surfaceRaised: '#191d24', surfaceMuted: '#20252e', border: '#303742', borderStrong: '#48515f', accent: '#cbd5e1', accentSoft: '#2b333e', instrument: '#7dd3fc', radius: '2px' },
    ),
  },
  {
    id: 'console',
    name: 'Data Console',
    chineseName: '紧凑控制台',
    description: '压缩装饰和垂直间距，以数据密度为先，适合交易与日志高频查看。',
    feature: '高密度 · 数值优先',
    navigation: 'rail',
    themes: createThemes(
      { canvas: '#f7f8fa', surface: '#ffffff', surfaceRaised: '#ffffff', surfaceMuted: '#eef1f4', border: '#cfd6df', borderStrong: '#8d99a8', accent: '#005f73', accentSoft: '#dff3f3', instrument: '#005f73', radius: '0px', shadow: '0 8px 20px rgb(15 23 42 / 8%)' },
      { canvas: '#07090c', surface: '#0c1015', surfaceRaised: '#10161d', surfaceMuted: '#151c24', border: '#26313d', borderStrong: '#526170', text: '#f2f7fb', textSecondary: '#bdc8d4', textMuted: '#7d8a98', accent: '#56d7df', accentSoft: '#123438', instrument: '#56d7df', radius: '0px' },
    ),
  },
];

export const getMobileDemoStyle = (styleId: DemoMobileStyleId): DemoMobileStyle => (
  mobileDemoStyles.find((style) => style.id === styleId) ?? mobileDemoStyles[0]
);

export const demoMetrics = [
  { label: '持仓市值', value: '¥287,430.00', change: '+1.82%', tone: 'positive' as const },
  { label: '可用资金', value: '¥126,800.00', change: '31.4%', tone: 'neutral' as const },
  { label: '今日收益', value: '+¥4,286.20', change: '+1.11%', tone: 'positive' as const },
  { label: '本月收益', value: '+¥18,942.60', change: '+4.73%', tone: 'positive' as const },
];

export const marketRows = [
  { code: '600519', name: '贵州茅台', price: '1,421.30', change: '+2.46%', volume: '32.8亿', tone: 'positive' as const },
  { code: '300750', name: '宁德时代', price: '276.84', change: '+1.18%', volume: '41.2亿', tone: 'positive' as const },
  { code: '000858', name: '五粮液', price: '132.09', change: '-0.63%', volume: '18.7亿', tone: 'negative' as const },
  { code: '601318', name: '中国平安', price: '57.42', change: '+0.34%', volume: '25.1亿', tone: 'positive' as const },
];

export const tradeRows = [
  { code: '600519', name: '贵州茅台', action: '卖出', time: '2026-11-02 14:32', price: '1,421.30', quantity: '20', amount: '¥28,426.00', result: '+¥684.20', rate: '+2.47%', tone: 'positive' as const },
  { code: '300750', name: '宁德时代', action: '买入', time: '2026-11-02 13:18', price: '276.84', quantity: '50', amount: '¥13,842.00', result: '持仓中', rate: '-', tone: 'neutral' as const },
  { code: '000858', name: '五粮液', action: '卖出', time: '2026-11-02 10:06', price: '132.09', quantity: '200', amount: '¥26,418.00', result: '-¥192.00', rate: '-0.72%', tone: 'negative' as const },
  { code: '601318', name: '中国平安', action: '买入', time: '2026-11-01 14:48', price: '57.42', quantity: '300', amount: '¥17,226.00', result: '持仓中', rate: '-', tone: 'neutral' as const },
];

export const agentMessages = [
  { role: 'user' as const, author: '当前用户', time: '14:28', content: '总结今天的收益与主要交易。' },
  { role: 'assistant' as const, author: '策略 Agent', time: '14:29', content: '今日收益 +¥4,286.20，主要贡献来自贵州茅台卖出记录；五粮液交易产生小幅亏损。当前可用资金 ¥126,800.00。' },
  { role: 'user' as const, author: '当前用户', time: '14:31', content: '还有哪些运行风险需要关注？' },
  { role: 'assistant' as const, author: '策略 Agent', time: '14:32', content: '建议继续关注数据同步状态和未完成订单。系统日志显示行情更新与账户同步均已完成。' },
];

export const logRows = [
  { time: '14:35:28', level: 'INFO', source: 'market.sync', message: '行情数据同步完成，共更新 5 个关注标的。' },
  { time: '14:34:51', level: 'INFO', source: 'account.sync', message: '账户资产与可用资金读取完成。' },
  { time: '14:32:16', level: 'INFO', source: 'trade.fill', message: '600519 卖出成交记录已写入。' },
  { time: '14:30:42', level: 'WARN', source: 'agent.context', message: 'Agent 上下文接近摘要阈值，已压缩历史消息。' },
  { time: '14:28:09', level: 'INFO', source: 'agent.chat', message: 'Agent 回答生成完成，已关联收益与交易证据。' },
  { time: '14:25:33', level: 'INFO', source: 'system.health', message: '后端健康检查通过。' },
];

export const chartPoints = '0,74 24,68 48,72 72,54 96,58 120,39 144,45 168,28 192,35 216,16 240,22 264,8 288,18 312,4';
export const chartAreaPoints = `${chartPoints} 312,96 0,96`;

export const toDemoCssVariables = (tokens: DemoTokens): CSSProperties => ({
  '--demo-canvas': tokens.canvas, '--demo-surface': tokens.surface,
  '--demo-surface-raised': tokens.surfaceRaised, '--demo-surface-muted': tokens.surfaceMuted,
  '--demo-border': tokens.border, '--demo-border-strong': tokens.borderStrong,
  '--demo-text': tokens.text, '--demo-text-secondary': tokens.textSecondary,
  '--demo-text-muted': tokens.textMuted, '--demo-accent': tokens.accent,
  '--demo-accent-soft': tokens.accentSoft, '--demo-instrument': tokens.instrument,
  '--demo-positive': tokens.positive, '--demo-negative': tokens.negative,
  '--demo-warning': tokens.warning, '--demo-shadow': tokens.shadow,
  '--demo-radius': tokens.radius, '--demo-font': tokens.font,
  '--demo-number-font': tokens.numberFont,
} as CSSProperties);
// AI_GENERATE_END --
