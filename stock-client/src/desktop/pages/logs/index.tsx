// AI_GENERATE_START ----
import './styles.css';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Space, Switch, Tag } from 'antd';
import { ArrowDownOutlined, PauseCircleOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import request from '@shared/utils/request';
import type { ApiResponse } from '@shared/utils/api';

type LogState = 'offline' | 'missing' | 'empty' | 'live' | 'error';

type LogSnapshot = {
  available: boolean;
  path: string;
  lines: string[];
  error: string | null;
};

let globalLogsBuffer: string[] = [];
let globalLogState: LogState = 'offline';
let globalLogPath = '';
let globalLogError = '';

const MAX_LOG_LINES = 1000;
const BOTTOM_FOLLOW_THRESHOLD = 24;

const Logs: React.FC = () => {
  const { t } = useTranslation();
  const [logs, setLogs] = useState<string[]>(() => globalLogsBuffer);
  const [logState, setLogState] = useState<LogState>(globalLogState);
  const [logPath, setLogPath] = useState(globalLogPath);
  const [logError, setLogError] = useState(globalLogError);
  const [autoScroll, setAutoScroll] = useState(true);
  const [paused, setPaused] = useState(false);
  const [showScrollToBottom, setShowScrollToBottom] = useState(false);
  const logsContainerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (paused) return undefined;

    let active = true;
    const loadLogs = async () => {
      try {
        const response = await request.get('/operations/logs', {
          params: { lines: MAX_LOG_LINES },
        }) as ApiResponse<LogSnapshot>;
        if (!response.success || !response.data) {
          throw new Error(response.message || 'Log request failed');
        }
        if (!active) return;

        const snapshot = response.data;
        const nextLogs = (snapshot.lines ?? []).slice(-MAX_LOG_LINES);
        const nextState: LogState = snapshot.error
          ? 'error'
          : !snapshot.available
            ? 'missing'
            : nextLogs.length === 0
              ? 'empty'
              : 'live';

        globalLogsBuffer = nextLogs;
        globalLogState = nextState;
        globalLogPath = snapshot.path ?? '';
        globalLogError = snapshot.error ?? '';
        setLogs(nextLogs);
        setLogState(nextState);
        setLogPath(globalLogPath);
        setLogError(globalLogError);
      } catch {
        if (!active) return;
        globalLogState = 'offline';
        globalLogError = '';
        setLogState('offline');
        setLogError('');
      }
    };

    void loadLogs();
    const timer = window.setInterval(() => void loadLogs(), 3000);

    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [paused]);

  useEffect(() => {
    const container = logsContainerRef.current;
    if (container && autoScroll) {
      container.scrollTop = container.scrollHeight;
      setShowScrollToBottom(false);
    }
  }, [logs, autoScroll]);

  const handleLogsScroll = useCallback(() => {
    const container = logsContainerRef.current;
    if (!container) return;

    const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight;
    const isAtBottom = distanceFromBottom <= BOTTOM_FOLLOW_THRESHOLD;
    setShowScrollToBottom(!isAtBottom);
    if (!isAtBottom) setAutoScroll(false);
  }, []);

  const scrollToBottom = useCallback(() => {
    const container = logsContainerRef.current;
    if (!container) return;

    container.scrollTop = container.scrollHeight;
    setShowScrollToBottom(false);
  }, []);

  const status = {
    offline: { color: 'red', label: t('logs.offline') },
    missing: { color: 'gold', label: t('logs.missing') },
    empty: { color: 'default', label: t('logs.empty') },
    live: { color: 'blue', label: t('logs.live') },
    error: { color: 'red', label: t('logs.error') },
  }[logState];
  const emptyMessage = logState === 'offline'
    ? t('logs.offlineHint')
    : logState === 'missing'
      ? t('logs.missingHint', { path: logPath })
      : logState === 'error'
        ? t('logs.errorHint', { error: logError })
        : t('logs.waiting');

  usePageHeader(t('logs.subtitle'));

  return (
    <div className="app-page logs-page h-full min-h-[520px]">
      <div className="logs-toolbar flex flex-wrap items-center justify-end gap-3">
        <Tag color={status.color}>{status.label}</Tag>
        <div className="logs-desktop-actions">
          <Space className="rounded border border-[var(--border-color)] px-3 py-1.5">
            <span className="text-sm text-[var(--text-secondary)]">{t('logs.autoScroll')}</span>
            <Switch size="small" checked={autoScroll} onChange={setAutoScroll} />
          </Space>
          <Button
            icon={paused ? <PlayCircleOutlined /> : <PauseCircleOutlined />}
            aria-pressed={paused}
            onClick={() => setPaused((value) => !value)}
          >
            {t(paused ? 'logs.resume' : 'logs.pause')}
          </Button>
          {showScrollToBottom && (
            <Button
              className="logs-toolbar-bottom-button"
              type="text"
              shape="circle"
              icon={<ArrowDownOutlined />}
              aria-label="回到底部"
              title="回到底部"
              onClick={scrollToBottom}
            />
          )}
        </div>
      </div>
      <div className="logs-console relative flex min-h-0 flex-1 flex-col overflow-hidden font-mono text-sm">
        <div
          ref={logsContainerRef}
          className="logs-scroll-viewport custom-scrollbar flex-1"
          onScroll={handleLogsScroll}
        >
          {logs.length === 0 && (
            <div className="p-4 text-center italic text-slate-500">{emptyMessage}</div>
          )}
          {logs.map((log, index) => (
            <div
              key={`${index}-${log.slice(0, 16)}`}
              className="logs-line"
            >
              <span className="logs-line-content">{log}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default Logs;
// AI_GENERATE_END ----
