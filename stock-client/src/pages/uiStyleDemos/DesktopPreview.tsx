// AI_GENERATE_START ----
import React from 'react';
import {
  BarChart3, Bell, Bot, CandlestickChart, CheckCircle2, ChevronRight,
  CircleDollarSign, Clock3, FileClock, Filter, LayoutDashboard, RefreshCw,
  Search, Send, Settings, SlidersHorizontal, SquareTerminal,
} from 'lucide-react';
import {
  agentMessages, chartAreaPoints, chartPoints, demoMetrics, demoStyle, logRows,
  marketRows, tradeRows, type DemoPageId, type DemoThemeMode, toDemoCssVariables,
} from './demoConfig';
import styles from './desktop.module.css';

interface DesktopPreviewProps {
  pageId: DemoPageId;
  themeMode: DemoThemeMode;
  onPageChange: (pageId: DemoPageId) => void;
}

const pageMeta: Record<DemoPageId, { title: string; subtitle: string }> = {
  overview: { title: '收益总览', subtitle: '账户、收益、行情与近期交易' },
  records: { title: '交易记录', subtitle: '筛选成交记录并复盘收益结果' },
  agent: { title: 'AGENT 对话', subtitle: '结合行情、收益、交易和日志进行问答' },
  logs: { title: '系统日志', subtitle: '查看运行状态与任务执行信息' },
};

const menuItems = [
  { id: 'overview' as const, label: '收益总览', icon: LayoutDashboard },
  { label: '行情中心', icon: CandlestickChart },
  { id: 'records' as const, label: '交易记录', icon: FileClock },
  { id: 'agent' as const, label: 'AGENT 对话', icon: Bot },
  { id: 'logs' as const, label: '系统日志', icon: SquareTerminal },
];

const toneClass = (tone: 'positive' | 'negative' | 'neutral') => (
  tone === 'positive' ? styles.positive : tone === 'negative' ? styles.negative : styles.neutral
);

const DesktopPreview: React.FC<DesktopPreviewProps> = ({ pageId, themeMode, onPageChange }) => {
  const tokens = demoStyle.themes[themeMode];
  const meta = pageMeta[pageId];

  const renderOverview = () => (
    <>
      <div className={styles.metricGrid}>
        {demoMetrics.map((metric) => (
          <article className={styles.metric} key={metric.label}>
            <span>{metric.label}</span><strong>{metric.value}</strong><small className={toneClass(metric.tone)}>{metric.change}</small>
          </article>
        ))}
      </div>
      <div className={styles.primaryGrid}>
        <article className={styles.panel}>
          <div className={styles.panelHeader}>
            <div><h3>资产走势</h3><span>最近 30 个交易日</span></div>
            <div className={styles.periodTabs}><button type="button">7日</button><button className={styles.periodActive} type="button">30日</button><button type="button">90日</button></div>
          </div>
          <div className={styles.chartSummary}><div><span>总资产</span><strong>¥414,230.00</strong></div><span className={styles.positive}>+6.28%</span></div>
          <div className={styles.chart}>
            <span className={styles.chartLabelTop}>420k</span><span className={styles.chartLabelBottom}>380k</span>
            <svg viewBox="0 0 312 100" role="img" aria-label="资产走势示意图" preserveAspectRatio="none">
              <defs><linearGradient id={`desktop-area-${themeMode}`} x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="var(--demo-accent)" stopOpacity="0.3" /><stop offset="100%" stopColor="var(--demo-accent)" stopOpacity="0" /></linearGradient></defs>
              <polygon points={chartAreaPoints} fill={`url(#desktop-area-${themeMode})`} /><polyline points={chartPoints} fill="none" stroke="var(--demo-accent)" strokeWidth="2" vectorEffect="non-scaling-stroke" />
            </svg>
          </div>
        </article>
        <article className={styles.panel}>
          <div className={styles.panelHeader}><div><h3>行情关注</h3><span>自选股票</span></div><button className={styles.textButton} type="button">全部 <ChevronRight size={14} /></button></div>
          <div className={styles.watchList}>{marketRows.map((row, index) => <article className={index === 0 ? styles.watchActive : styles.watchRow} key={row.code}><span><strong>{row.name}</strong><small>{row.code}</small></span><span><strong>{row.price}</strong><small className={toneClass(row.tone)}>{row.change}</small></span></article>)}</div>
        </article>
      </div>
      <article className={styles.tablePanel}>
        <div className={styles.panelHeader}><div><h3>今日交易</h3><span>{tradeRows.length} 笔记录</span></div><button className={styles.textButton} type="button" onClick={() => onPageChange('records')}>交易记录 <ChevronRight size={14} /></button></div>
        <div className={styles.table}><div className={styles.tableHead}><span>股票</span><span>操作</span><span>时间</span><span>成交金额</span><span>结果</span></div>{tradeRows.slice(0, 3).map((row) => <div className={styles.tableRow} key={`${row.code}-${row.time}`}><span><strong>{row.name}</strong><small>{row.code}</small></span><span className={styles.action}><CircleDollarSign size={14} />{row.action}</span><span><Clock3 size={14} />{row.time.slice(11)}</span><span>{row.amount}</span><strong className={toneClass(row.tone)}>{row.result}</strong></div>)}</div>
      </article>
    </>
  );

  const renderRecords = () => (
    <>
      <div className={styles.recordSummary}>{demoMetrics.slice(2).map((metric) => <article key={metric.label}><span>{metric.label}</span><strong>{metric.value}</strong><small className={toneClass(metric.tone)}>{metric.change}</small></article>)}<article><span>记录数量</span><strong>{tradeRows.length}</strong><small>当前筛选</small></article></div>
      <div className={styles.filterBar}>
        <label><Search size={14} /><input aria-label="搜索股票" placeholder="股票代码或名称" /></label>
        <button type="button"><Clock3 size={14} />2026-10-01 至 2026-11-02</button><button type="button"><Filter size={14} />全部状态</button><button className={styles.primaryButton} type="button">查询</button>
      </div>
      <article className={`${styles.tablePanel} ${styles.recordTablePanel}`}>
        <div className={styles.recordTabs}><button className={styles.recordTabActive} type="button">历史订单</button><button type="button">交易记录</button><span>{tradeRows.length} 条结果</span></div>
        <div className={styles.recordTable}><div className={styles.recordTableHead}><span>股票</span><span>方向</span><span>成交时间</span><span>成交价格</span><span>数量</span><span>成交金额</span><span>净收益</span><span>收益率</span></div>{tradeRows.map((row) => <div className={styles.recordTableRow} key={`record-${row.code}-${row.time}`}><span><strong>{row.name}</strong><small>{row.code}</small></span><span>{row.action}</span><span>{row.time}</span><span>{row.price}</span><span>{row.quantity}</span><span>{row.amount}</span><strong className={toneClass(row.tone)}>{row.result}</strong><strong className={toneClass(row.tone)}>{row.rate}</strong></div>)}</div>
        <div className={styles.pagination}><span>共 {tradeRows.length} 条</span><button type="button">上一页</button><button className={styles.pageActive} type="button">1</button><button type="button">下一页</button></div>
      </article>
    </>
  );

  const renderAgent = () => (
    <div className={styles.agentWorkspace}>
      <aside className={styles.sessionPanel}><div className={styles.subPanelHeader}><strong>会话记录</strong><button type="button" aria-label="新建会话">+</button></div>{['今日收益复盘', '运行状态检查', '交易记录分析'].map((title, index) => <button className={index === 0 ? styles.sessionActive : styles.sessionItem} type="button" key={title}><strong>{title}</strong><small>{index === 0 ? '14:32' : '11月1日'}</small></button>)}</aside>
      <section className={styles.chatPanel}>
        <div className={styles.subPanelHeader}><span className={styles.agentIdentity}><Bot size={18} /><span><strong>策略 Agent</strong><small>已连接账户与运行数据</small></span></span><button type="button" aria-label="刷新会话"><RefreshCw size={15} /></button></div>
        <div className={styles.messageList}>{agentMessages.map((message, index) => <article className={message.role === 'user' ? styles.userMessage : styles.agentMessage} key={`${message.time}-${index}`}><div><strong>{message.author}</strong><span>{message.time}</span></div><p>{message.content}</p></article>)}</div>
        <div className={styles.composer}><textarea aria-label="Agent 问题" placeholder="询问收益、交易记录或运行异常" /><button type="button" aria-label="发送"><Send size={16} /></button></div>
      </section>
      <aside className={styles.promptPanel}><div className={styles.subPanelHeader}><strong>问题示例</strong></div>{['今天的收益来自哪些交易？', '检查是否存在异常日志', '总结当前持仓风险'].map((prompt) => <button type="button" key={prompt}><Bot size={14} /><span>{prompt}</span></button>)}</aside>
    </div>
  );

  const renderLogs = () => (
    <div className={styles.logsWorkspace}>
      <div className={styles.logsToolbar}><span className={styles.liveStatus}><CheckCircle2 size={14} />实时连接</span><label><Search size={14} /><input aria-label="搜索日志" placeholder="搜索日志内容" /></label><button type="button"><SlidersHorizontal size={14} />级别</button><button type="button"><RefreshCw size={14} />刷新</button></div>
      <div className={styles.logsGrid}>
        <section className={styles.logConsole}>{logRows.map((log, index) => <div className={styles.logLine} key={`${log.time}-${index}`}><span>#{String(index + 1).padStart(4, '0')}</span><time>{log.time}</time><strong className={log.level === 'WARN' ? styles.warning : styles.info}>{log.level}</strong><code>{log.source}</code><p>{log.message}</p></div>)}</section>
        <aside className={styles.logSummary}><div className={styles.subPanelHeader}><strong>运行摘要</strong></div><article><span>连接状态</span><strong className={styles.healthy}>正常</strong></article><article><span>日志行数</span><strong>{logRows.length}</strong></article><article><span>最近更新</span><strong>14:35:28</strong></article><article><span>自动跟随</span><strong>已开启</strong></article></aside>
      </div>
    </div>
  );

  const content = { overview: renderOverview, records: renderRecords, agent: renderAgent, logs: renderLogs }[pageId]();

  return (
    <section className={`${styles.preview} ${styles.ledger}`} style={toDemoCssVariables(tokens)} aria-label={`${meta.title} PC 端预览`}>
      <aside className={styles.sidebar}>
        <div className={styles.brand}><span className={styles.brandMark}><BarChart3 size={17} /></span><div><strong>Stock Console</strong><span>Blue Ledger</span></div></div>
        <nav className={styles.navigation} aria-label="Demo PC 导航">{menuItems.map(({ id, label, icon: Icon }) => id ? <button className={pageId === id ? styles.navActive : styles.navItem} key={label} type="button" onClick={() => onPageChange(id)}><Icon size={16} /><span>{label}</span></button> : <span className={styles.navItem} key={label}><Icon size={16} /><span>{label}</span></span>)}</nav>
        <div className={styles.sidebarFooter}><span className={styles.onlineDot} /><div><strong>数据服务正常</strong><span>刚刚同步</span></div></div>
      </aside>
      <div className={styles.workspace}>
        <header className={styles.header}><div><h2>{meta.title}</h2><p>{meta.subtitle} · 2026-11-02 14:35</p></div><div className={styles.headerTools}><label className={styles.searchBox}><Search size={15} /><input aria-label="全局搜索" placeholder="搜索" /></label><button type="button" aria-label="通知"><Bell size={17} /></button><button type="button" aria-label="设置"><Settings size={17} /></button><span className={styles.avatar}>SC</span></div></header>
        <main className={styles.content}>{content}</main>
      </div>
    </section>
  );
};

export default DesktopPreview;
// AI_GENERATE_END ----
