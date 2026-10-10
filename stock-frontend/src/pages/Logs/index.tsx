// AI_GENERATE_START -
import React, { useEffect, useRef, useState } from 'react';
import { Button, Space, Switch, Tag, Typography } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

const { Text, Title } = Typography;

// 全局 WebSocket 与日志缓冲，确保只建立一次连接，并在多次进入页面时保留历史日志
let globalLogsWebSocket: WebSocket | null = null;
let globalLogsBuffer: string[] = [];
let globalIsConnected = false;

const MAX_LOG_LINES = 1000;

const Logs: React.FC = () => {
  const { t } = useTranslation();
  const [logs, setLogs] = useState<string[]>(() => globalLogsBuffer);
  const [isConnected, setIsConnected] = useState<boolean>(globalIsConnected);
  const [autoScroll, setAutoScroll] = useState(true);
  const logsContainerRef = useRef<HTMLDivElement | null>(null);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    const appendLog = (log: string) => {
      globalLogsBuffer = [...globalLogsBuffer, log].slice(-MAX_LOG_LINES);
      setLogs(globalLogsBuffer);
    };

    const connect = () => {
      const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
      const host = window.location.hostname;
      const isDevOn5173 = window.location.port === '5173';
      const port = isDevOn5173 ? '8080' : window.location.port;
      const wsUrl = `${protocol}://${host}${port ? `:${port}` : ''}/ws/logs`;

      if (
        globalLogsWebSocket &&
        (globalLogsWebSocket.readyState === WebSocket.OPEN ||
          globalLogsWebSocket.readyState === WebSocket.CONNECTING)
      ) {
        wsRef.current = globalLogsWebSocket;
        setIsConnected(globalLogsWebSocket.readyState === WebSocket.OPEN);
        globalLogsWebSocket.onmessage = (event: MessageEvent<string>) => appendLog(event.data);
        setLogs(globalLogsBuffer);
        return;
      }

      const ws = new WebSocket(wsUrl);
      globalLogsWebSocket = ws;
      wsRef.current = ws;

      ws.onopen = () => {
        globalIsConnected = true;
        setIsConnected(true);
        appendLog(t('logs.connected'));
      };

      ws.onmessage = (event: MessageEvent<string>) => appendLog(event.data);

      ws.onclose = () => {
        globalIsConnected = false;
        setIsConnected(false);
        appendLog(t('logs.reconnecting'));
        window.setTimeout(() => {
          if (wsRef.current) connect();
        }, 3000);
      };

      ws.onerror = () => {
        globalIsConnected = false;
        setIsConnected(false);
      };
    };

    connect();

    return () => {
      wsRef.current = null;
    };
  }, [t]);

  useEffect(() => {
    if (autoScroll && logsContainerRef.current) {
      logsContainerRef.current.scrollTop = logsContainerRef.current.scrollHeight;
    }
  }, [logs, autoScroll]);

  const clearLogs = () => {
    globalLogsBuffer = [];
    setLogs([]);
  };

  return (
    <div className="app-page h-[calc(100vh-112px)] min-h-[520px]">
      <div className="page-heading flex-shrink-0">
        <div>
          <Title level={2} className="page-title">{t('logs.title')}</Title>
          <Text className="page-subtitle">查看后端实时运行事件与连接状态</Text>
        </div>

        <Space wrap>
          <Tag color={isConnected ? 'blue' : 'red'}>
            {isConnected ? t('logs.live') : t('logs.offline')}
          </Tag>
          <Space className="rounded border border-[var(--border-color)] px-3 py-1.5">
            <span className="text-sm text-[var(--text-secondary)]">{t('logs.autoScroll')}</span>
            <Switch size="small" checked={autoScroll} onChange={setAutoScroll} />
          </Space>
          <Button icon={<DeleteOutlined />} onClick={clearLogs} danger>
            {t('logs.clear')}
          </Button>
        </Space>
      </div>

      <div className="relative flex min-h-0 flex-1 flex-col overflow-hidden rounded border border-[#23314a] bg-[#080d16] p-4 font-mono text-sm shadow-sm">
        <div
          ref={logsContainerRef}
          className="custom-scrollbar flex-1 overflow-y-auto pr-2"
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
// AI_GENERATE_END -