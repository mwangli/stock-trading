import React, { useEffect, useRef, useState } from 'react';
import { Button, Tag, Space, Switch } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';


const Logs: React.FC = () => {
  const { t } = useTranslation();
  const [logs, setLogs] = useState<string[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [autoScroll, setAutoScroll] = useState(true);
  const logsEndRef = useRef<HTMLDivElement>(null);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    // Connect to WebSocket
    const connect = () => {
      // Connect to WebSocket
      const ws = new WebSocket('ws://localhost:8080/ws/logs');
      wsRef.current = ws;

      ws.onopen = () => {
        setIsConnected(true);
        setLogs(prev => [...prev, t('logs.connected')]);
      };

      ws.onmessage = (event) => {
        setLogs(prev => {
          // Limit logs to avoid memory issues (e.g., last 1000 lines)
          const newLogs = [...prev, event.data];
          if (newLogs.length > 1000) {
            return newLogs.slice(newLogs.length - 1000);
          }
          return newLogs;
        });
      };

      ws.onclose = () => {
        setIsConnected(false);
        setLogs(prev => [...prev, t('logs.reconnecting')]);
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

    return () => {
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    if (autoScroll && logsEndRef.current) {
      logsEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs, autoScroll]);

  const clearLogs = () => {
    setLogs([]);
  };

  return (
    <div className="h-[calc(100vh-120px)] flex flex-col gap-4">
      <div className="flex justify-between items-center flex-shrink-0">
        <h1 className="text-3xl font-bold !mb-0 !text-white font-mono">{t('logs.title')}</h1>

        <Space>
          <Tag color={isConnected ? 'green' : 'red'}>
            {isConnected ? t('logs.live') : t('logs.offline')}
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
        
        <div className="flex-1 overflow-y-auto custom-scrollbar pr-2 pt-2 pb-2">
          {logs.length === 0 && (
            <div className="text-gray-500 italic p-4 text-center">{t('logs.waiting')}</div>
          )}
          {logs.map((log, index) => (
            <div key={index} className="whitespace-pre-wrap break-all py-0.5 border-b border-white/5 last:border-0 hover:bg-white/5 transition-colors pl-2">
              <span className="text-[#00e396] mr-3 opacity-50 select-none">Op {index + 1}</span>
              <span className="text-gray-300 font-light tracking-wide">{log}</span>
            </div>
          ))}
          <div ref={logsEndRef} />
        </div>
        
        <div className="absolute bottom-0 left-0 w-full h-8 bg-gradient-to-t from-[#0a0c10] to-transparent pointer-events-none z-10" />
      </div>
    </div>
  );
};

export default Logs;
