import React, { useEffect, useRef, useState } from 'react';
import { Button, Tag, Space, Switch } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

// 全局 WebSocket 与日志缓冲，确保只建立一次连接，并在多次进入页面时保留历史日志
let globalLogsWebSocket: WebSocket | null = null;
let globalLogsBuffer: string[] = [];
let globalIsConnected = false;

const MAX_LOG_LINES = 1000;

const Logs: React.FC = () => {
  const { t } = useTranslation();
  // 初始值来自全局缓冲，这样切换菜单回来时还能看到历史日志
  const [logs, setLogs] = useState<string[]>(() => globalLogsBuffer);
  const [isConnected, setIsConnected] = useState<boolean>(globalIsConnected);
  const [autoScroll, setAutoScroll] = useState(true);
  const logsContainerRef = useRef<HTMLDivElement | null>(null);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    // Connect to WebSocket
    const connect = () => {
      // 根据当前环境动态计算 WebSocket 地址：
      // - 开发环境：前端 5173，后端 8080
      // - 生产环境：同源 /ws/logs
      const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
      const host = window.location.hostname;
      const isDevOn5173 = window.location.port === '5173';
      const port = isDevOn5173 ? '8080' : window.location.port;
      const wsUrl = `${protocol}://${host}${port ? `:${port}` : ''}/ws/logs`;
      // 如果已经有全局连接且处于 OPEN/CONNECTING 状态，则复用
      if (
        globalLogsWebSocket &&
        (globalLogsWebSocket.readyState === WebSocket.OPEN ||
          globalLogsWebSocket.readyState === WebSocket.CONNECTING)
      ) {
        wsRef.current = globalLogsWebSocket;
        setIsConnected(globalLogsWebSocket.readyState === WebSocket.OPEN);
        // 覆盖事件回调到当前组件实例，并使用全局缓冲更新
        globalLogsWebSocket.onmessage = (event) => {
          globalLogsBuffer = [...globalLogsBuffer, event.data];
          if (globalLogsBuffer.length > MAX_LOG_LINES) {
            globalLogsBuffer = globalLogsBuffer.slice(globalLogsBuffer.length - MAX_LOG_LINES);
          }
          setLogs(globalLogsBuffer);
        };
        // 复用连接时直接展示当前缓冲内容
        setLogs(globalLogsBuffer);
        return;
      }

      // 创建新的全局 WebSocket 连接
      const ws = new WebSocket(wsUrl);
      globalLogsWebSocket = ws;
      wsRef.current = ws;

      ws.onopen = () => {
        // eslint-disable-next-line no-console
        console.debug('[Logs] WebSocket opened:', wsUrl);
        globalIsConnected = true;
        setIsConnected(true);
        globalLogsBuffer = [...globalLogsBuffer, t('logs.connected')];
        setLogs(globalLogsBuffer);
      };

      ws.onmessage = (event) => {
        // eslint-disable-next-line no-console
        console.debug('[Logs] WebSocket message:', event.data);
        globalLogsBuffer = [...globalLogsBuffer, event.data];
        if (globalLogsBuffer.length > MAX_LOG_LINES) {
          globalLogsBuffer = globalLogsBuffer.slice(globalLogsBuffer.length - MAX_LOG_LINES);
        }
        setLogs(globalLogsBuffer);
      };

      ws.onclose = () => {
        globalIsConnected = false;
        setIsConnected(false);
        globalLogsBuffer = [...globalLogsBuffer, t('logs.reconnecting')];
        setLogs(globalLogsBuffer);
        // Only reconnect if component is still mounted
        setTimeout(() => {
            if (wsRef.current) connect(); 
        }, 3000);
      };

      ws.onerror = (error) => {
        console.error('WebSocket error:', error);
        setIsConnected(false);
      };
    };

    connect();

    // 不在卸载时关闭 WebSocket，只移除本地引用，保持全局长连接
    return () => {
      wsRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (autoScroll && logsContainerRef.current) {
      const el = logsContainerRef.current;
      el.scrollTop = el.scrollHeight;
    }
  }, [logs, autoScroll]);

  const clearLogs = () => {
    setLogs([]);
  };

  return (
    <div className="h-[calc(100vh-180px)] flex flex-col gap-4">
      <div className="flex justify-between items-center flex-shrink-0">
        <h1 className="text-3xl font-bold !mb-0 !text-white font-mono">{t('logs.title')}</h1>

        <Space>
          <Tag color={isConnected ? 'green' : 'red'}>
            {isConnected ? '在线' : '离线'}
          </Tag>
          <Space className="bg-white/5 p-1 rounded-lg px-3">
            <span className="text-white/70 text-sm">{t('logs.autoScroll')}</span>
            <Switch size="small" checked={autoScroll} onChange={setAutoScroll} />
          </Space>
          <Button 
            icon={<DeleteOutlined />} 
            onClick={clearLogs}
            danger
            ghost
          >
            {t('logs.clear')}
          </Button>
        </Space>
      </div>

      <div className="flex-1 bg-[#0a0c10] rounded-xl p-4 overflow-hidden flex flex-col border border-white/10 font-mono text-sm shadow-2xl relative">
        <div className="absolute top-0 left-0 w-full h-8 bg-gradient-to-b from-[#0a0c10] to-transparent pointer-events-none z-10" />
        
        <div
          ref={logsContainerRef}
          className="flex-1 overflow-y-auto custom-scrollbar pr-2 pt-2 pb-2"
        >
          {logs.length === 0 && (
            <div className="text-gray-500 italic p-4 text-center">{t('logs.waiting')}</div>
          )}
          {logs.map((log, index) => (
            <div
              key={index}
              className="whitespace-pre-wrap break-all py-0.5 border-b border-white/5 last:border-0 hover:bg-white/5 transition-colors pl-2"
            >
              <span className="text-[#00e396] mr-3 opacity-50 select-none">Op {index + 1}</span>
              <span className="text-gray-300 font-light tracking-wide">{log}</span>
            </div>
          ))}
        </div>
        
        <div className="absolute bottom-0 left-0 w-full h-8 bg-gradient-to-t from-[#0a0c10] to-transparent pointer-events-none z-10" />
      </div>
    </div>
  );
};

export default Logs;
