// AI_GENERATE_START---------
import './styles.css';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Space, Switch, Tag } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import request from '../../utils/request';
import type { ApiResponse } from '../../utils/api';

let globalLogsBuffer: string[] = [];
let globalIsConnected = false;

const MAX_LOG_LINES = 1000;
const MOBILE_BREAKPOINT_QUERY = '(max-width: 767px)';
const BOTTOM_FOLLOW_THRESHOLD = 24;

const Logs: React.FC = () => {
  const { t } = useTranslation();
  const [logs, setLogs] = useState<string[]>(() => globalLogsBuffer);
  const [isConnected, setIsConnected] = useState<boolean>(globalIsConnected);
  const [autoScroll, setAutoScroll] = useState(true);
  const logsContainerRef = useRef<HTMLDivElement | null>(null);
  const isMobileRef = useRef(window.matchMedia(MOBILE_BREAKPOINT_QUERY).matches);
  const mobileFollowRef = useRef(true);

  useEffect(() => {
    const mediaQuery = window.matchMedia(MOBILE_BREAKPOINT_QUERY);
    const handleViewportChange = (event: MediaQueryListEvent) => {
      isMobileRef.current = event.matches;
      if (!event.matches) return;

      mobileFollowRef.current = true;
      window.requestAnimationFrame(() => {
        const container = logsContainerRef.current;
        if (container) container.scrollTop = container.scrollHeight;
      });
    };

    mediaQuery.addEventListener('change', handleViewportChange);
    return () => mediaQuery.removeEventListener('change', handleViewportChange);
  }, []);

  useEffect(() => {
    let active = true;
    const loadLogs = async () => {
      try {
        const response = await request.get('/operations/logs', {
          params: { lines: MAX_LOG_LINES },
        }) as ApiResponse<string[]>;
        if (!response.success) {
          throw new Error(response.message || '日志读取失败');
        }
        if (!active) return;
        globalLogsBuffer = (response.data ?? []).slice(-MAX_LOG_LINES);
        globalIsConnected = true;
        setIsConnected(true);
        setLogs(globalLogsBuffer);
      } catch {
        if (!active) return;
        globalIsConnected = false;
        setIsConnected(false);
      }
    };

    void loadLogs();
    const timer = window.setInterval(() => void loadLogs(), 3000);

    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    const container = logsContainerRef.current;
    const shouldFollow = isMobileRef.current ? mobileFollowRef.current : autoScroll;
    if (container && shouldFollow) container.scrollTop = container.scrollHeight;
  }, [logs, autoScroll]);

  const handleLogsScroll = useCallback(() => {
    const container = logsContainerRef.current;
    if (!container || !isMobileRef.current) return;

    const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight;
    mobileFollowRef.current = distanceFromBottom <= BOTTOM_FOLLOW_THRESHOLD;
  }, []);

  const clearLogs = useCallback(() => {
    globalLogsBuffer = [];
    setLogs([]);
  }, []);

  usePageHeader(t('logs.subtitle'));

  return (
    <div className="app-page h-full min-h-[520px]">
      <div className="logs-toolbar flex flex-wrap items-center justify-end gap-3">
        <Tag color={isConnected ? 'blue' : 'red'}>
          {isConnected ? t('logs.live') : t('logs.offline')}
        </Tag>
        <div className="logs-desktop-actions">
          <Space className="rounded border border-[var(--border-color)] px-3 py-1.5">
            <span className="text-sm text-[var(--text-secondary)]">{t('logs.autoScroll')}</span>
            <Switch size="small" checked={autoScroll} onChange={setAutoScroll} />
          </Space>
          <Button icon={<DeleteOutlined />} onClick={clearLogs}>
            {t('logs.clear')}
          </Button>
        </div>
      </div>
      <div className="logs-console relative flex min-h-0 flex-1 flex-col overflow-hidden rounded border border-[#23314a] bg-[#080d16] p-4 font-mono text-sm shadow-sm">
        <div
          ref={logsContainerRef}
          className="custom-scrollbar flex-1 overflow-y-auto pr-2"
          onScroll={handleLogsScroll}
        >
          {logs.length === 0 && (
            <div className="p-4 text-center italic text-slate-500">{t('logs.waiting')}</div>
          )}
          {logs.map((log, index) => (
            <div
              key={`${index}-${log.slice(0, 16)}`}
              className="border-b border-white/5 py-1 pl-2 transition-colors last:border-0 hover:bg-white/5"
            >
              <span className="mr-3 select-none text-blue-400 opacity-70">#{String(index + 1).padStart(4, '0')}</span>
              <span className="whitespace-pre-wrap break-all font-light text-slate-300">{log}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default Logs;
// AI_GENERATE_END---------
