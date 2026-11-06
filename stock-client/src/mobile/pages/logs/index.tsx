// AI_GENERATE_START ---------
import './styles.css';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Button } from 'antd';
import { ArrowDownOutlined, CaretRightOutlined, PauseOutlined } from '@ant-design/icons';
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
  const [paused, setPaused] = useState(false);
  const [showScrollToBottom, setShowScrollToBottom] = useState(false);
  const logsContainerRef = useRef<HTMLDivElement | null>(null);
  const mobileFollowRef = useRef(true);

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
    if (container && mobileFollowRef.current) {
      const frame = window.requestAnimationFrame(() => {
        container.scrollTop = Math.max(0, container.scrollHeight - container.clientHeight);
        setShowScrollToBottom(false);
      });
      return () => window.cancelAnimationFrame(frame);
    }
    return undefined;
  }, [logs]);

  const handleLogsScroll = useCallback(() => {
    const container = logsContainerRef.current;
    if (!container) return;

    const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight;
    const isAtBottom = distanceFromBottom <= BOTTOM_FOLLOW_THRESHOLD;
    mobileFollowRef.current = isAtBottom;
    setShowScrollToBottom(!isAtBottom);
  }, []);

  const scrollToBottom = useCallback(() => {
    const container = logsContainerRef.current;
    if (!container) return;

    mobileFollowRef.current = true;
    container.scrollTop = Math.max(0, container.scrollHeight - container.clientHeight);
    setShowScrollToBottom(false);
  }, []);

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
        <div className="logs-mobile-actions">
          <Button
            className="logs-pause-button"
            type="text"
            shape="circle"
            icon={paused ? <CaretRightOutlined /> : <PauseOutlined />}
            aria-label={t(paused ? 'logs.resume' : 'logs.pause')}
            aria-pressed={paused}
            title={t(paused ? 'logs.resume' : 'logs.pause')}
            onClick={() => setPaused((value) => !value)}
          />
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
// AI_GENERATE_END ---------
