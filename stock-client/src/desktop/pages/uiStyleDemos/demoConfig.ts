// AI_GENERATE_START -
import type { CSSProperties } from 'react';

export type DemoThemeMode = 'light' | 'dark';
export type DemoViewport = 'desktop' | 'mobile' | 'compare';
export type DemoPageId = 'overview' | 'records' | 'agent' | 'logs';

export interface DemoTokens {
  canvas: string; surface: string; surfaceRaised: string; surfaceMuted: string;
  border: string; borderStrong: string; text: string; textSecondary: string; textMuted: string;
  accent: string; accentSoft: string; instrument: string; positive: string; negative: string;
  warning: string; shadow: string; radius: string; font: string; numberFont: string;
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
// AI_GENERATE_END -
