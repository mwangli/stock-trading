import React, { useEffect, useRef, useState } from 'react';
import { Badge, Button, Card, Empty, Space, Tag, Typography } from 'antd';
import { ClearOutlined, ExpandOutlined, CompressOutlined } from '@ant-design/icons';

const { Text } = Typography;

interface LogEntry {
  taskId: number;
  level: string;
  message: string;
  timestamp: string;
}

interface LogViewerProps {
  taskId?: number;
  autoScroll?: boolean;
}

const LogViewer: React.FC<LogViewerProps> = ({ taskId, autoScroll = true }) => {
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [isExpanded, setIsExpanded] = useState(false);
  const [isConnected, setIsConnected] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // 获取WebSocket URL
  const getWsUrl = () => {
    const isDev = process.env.NODE_ENV === 'development';
    const wsHost = isDev ? 'localhost:8080' : window.location.host;
    const path = taskId ? `/ws/collection-log/${taskId}` : '/ws/collection-log/0';
    return `ws://${wsHost}${path}`;
  };

  // 连接WebSocket
  useEffect(() => {
    const connectWebSocket = () => {
      if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        return;
      }

      const wsUrl = getWsUrl();
      console.log('LogViewer connecting to:', wsUrl);
      
      try {
        const ws = new WebSocket(wsUrl);
        wsRef.current = ws;

        ws.onopen = () => {
          console.log('LogViewer WebSocket connected');
          setIsConnected(true);
        };

        ws.onmessage = (event) => {
          try {
            const logData: LogEntry = JSON.parse(event.data);
            setLogs(prev => [...prev, logData].slice(-500));
          } catch (e) {
            console.error('Parse log error:', e);
          }
        };

        ws.onclose = () => {
          console.log('LogViewer WebSocket disconnected');
          setIsConnected(false);
          if (taskId) {
            reconnectTimeoutRef.current = setTimeout(connectWebSocket, 3000);
          }
        };

        ws.onerror = (error) => {
          console.error('LogViewer WebSocket error:', error);
          setIsConnected(false);
        };
      } catch (error) {
        console.error('WebSocket connection error:', error);
      }
    };

    connectWebSocket();

    return () => {
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
      if (wsRef.current) {
        wsRef.current.close();
      }
    };
  }, [taskId]);

  // 自动滚动
  useEffect(() => {
    if (autoScroll && containerRef.current && logs.length > 0) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
  }, [logs, autoScroll]);

  // 清除日志
  const clearLogs = () => {
    setLogs([]);
  };

  // 获取日志级别颜色
  const getLevelColor = (level: string) => {
    const colorMap: Record<string, string> = {
      INFO: 'blue',
      WARN: 'orange',
      ERROR: 'red',
      DEBUG: 'default',
    };
    return colorMap[level] || 'default';
  };

  // 格式化时间
  const formatTime = (timestamp: string) => {
    try {
      const date = new Date(timestamp.replace('T', ' '));
      return date.toLocaleTimeString('zh-CN', { 
        hour: '2-digit', 
        minute: '2-digit', 
        second: '2-digit' 
      });
    } catch {
      return timestamp;
    }
  };

  const height = isExpanded ? 500 : 200;

  return (
    <Card
      size="small"
      title={
        <Space>
          <span>执行日志</span>
          <Badge 
            status={isConnected ? "success" : "default"} 
            text={isConnected ? "已连接" : "未连接"} 
          />
          <Tag color={logs.length > 0 ? "processing" : "default"}>
            {logs.length} 条
          </Tag>
        </Space>
      }
      extra={
        <Space>
          <Button 
            size="small" 
            icon={<ClearOutlined />} 
            onClick={clearLogs}
            disabled={logs.length === 0}
          >
            清除
          </Button>
          <Button 
            size="small" 
            icon={isExpanded ? <CompressOutlined /> : <ExpandOutlined />}
            onClick={() => setIsExpanded(!isExpanded)}
          >
            {isExpanded ? "收起" : "展开"}
          </Button>
        </Space>
      }
      style={{ marginTop: 16 }}
    >
      <div 
        ref={containerRef}
        style={{
          height,
          overflow: 'auto',
          background: '#1e1e1e',
          padding: '8px 12px',
          borderRadius: '4px',
          fontFamily: 'monospace',
          fontSize: '12px',
        }}
      >
        {logs.length === 0 ? (
          <Empty 
            description="暂无日志" 
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            style={{ padding: '40px 0' }}
          />
        ) : (
          logs.map((log, index) => (
            <div key={index} style={{ marginBottom: '4px', color: '#d4d4d4' }}>
              <Text type="secondary" style={{ fontSize: '11px', marginRight: 8 }}>
                {formatTime(log.timestamp)}
              </Text>
              <Tag color={getLevelColor(log.level)} style={{ marginRight: 8 }}>
                {log.level}
              </Tag>
              <Text style={{ color: log.level === 'ERROR' ? '#f14c4c' : '#d4d4d4' }}>
                {log.message}
              </Text>
            </div>
          ))
        )}
      </div>
    </Card>
  );
};

export default LogViewer;
