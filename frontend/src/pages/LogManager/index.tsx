import React, { useEffect, useRef, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Badge, Button, Card, Space, Tag, Typography, message } from 'antd';
import { ClearOutlined, SyncOutlined, CloudServerOutlined } from '@ant-design/icons';

const { Text } = Typography;

interface LogEntry {
  taskId: number;
  level: string;
  message: string;
  timestamp: string;
}

const LogManagerPage: React.FC = () => {
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [connectionError, setConnectionError] = useState<string>('');
  const [logCount, setLogCount] = useState(0);
  const wsRef = useRef<WebSocket | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [autoScroll, setAutoScroll] = useState(true);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const reconnectAttempts = useRef(0);

  // 获取WebSocket URL - 直接连接后端（已配置CORS）
  const getWsUrl = () => {
    const wsUrl = `ws://localhost:8080/ws/collection-log/0`;
    console.log('WebSocket URL:', wsUrl);
    return wsUrl;
  };

  // 连接WebSocket
  const connectWebSocket = () => {
    // 如果已有连接，先关闭
    if (wsRef.current) {
      if (wsRef.current.readyState === WebSocket.OPEN || wsRef.current.readyState === WebSocket.CONNECTING) {
        wsRef.current.close();
      }
    }

    const wsUrl = getWsUrl();
    console.log('Attempting to connect to WebSocket:', wsUrl);
    setConnectionError('');
    
    try {
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;

      ws.onopen = () => {
        console.log('WebSocket connected successfully');
        setIsConnected(true);
        setConnectionError('');
        reconnectAttempts.current = 0;
        message.success('日志服务已连接');
      };

      ws.onmessage = (event) => {
        try {
          const logData: LogEntry = JSON.parse(event.data);
          setLogs(prev => {
            const newLogs = [...prev, logData].slice(-1000);
            return newLogs;
          });
          setLogCount(prev => prev + 1);
        } catch (e) {
          console.error('Parse log error:', e);
        }
      };

      ws.onclose = (event) => {
        console.log('WebSocket disconnected:', event.code, event.reason, 'wasClean:', event.wasClean);
        setIsConnected(false);
        
        // 打印更详细的关闭信息
        if (event.code !== 1000) {
          setConnectionError(`连接断开 (code: ${event.code}, reason: ${event.reason || 'unknown'})`);
        }
        
        // 自动重连（最多重连5次）
        if (reconnectAttempts.current < 5) {
          const delay = Math.min(3000 * Math.pow(2, reconnectAttempts.current), 30000);
          reconnectAttempts.current++;
          console.log(`Auto-reconnecting in ${delay}ms (attempt ${reconnectAttempts.current})`);
          reconnectTimeoutRef.current = setTimeout(() => {
            connectWebSocket();
          }, delay);
        } else {
          setConnectionError('重连次数已达上限，请手动重试');
        }
      };

      ws.onerror = (error) => {
        console.error('WebSocket error:', error);
        setIsConnected(false);
        setConnectionError('WebSocket连接错误');
      };
    } catch (error) {
      console.error('WebSocket connection exception:', error);
      setConnectionError(`连接异常: ${error}`);
      setIsConnected(false);
    }
  };

  // 手动重连
  const handleReconnect = () => {
    console.log('Manual reconnect clicked');
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
    }
    reconnectAttempts.current = 0;
    connectWebSocket();
  };

  // 连接WebSocket
  useEffect(() => {
    connectWebSocket();

    return () => {
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
      if (wsRef.current) {
        wsRef.current.close();
      }
    };
  }, []);

  // 自动滚动
  useEffect(() => {
    if (autoScroll && containerRef.current && logs.length > 0) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
  }, [logs, autoScroll]);

  // 监听滚动事件
  const handleScroll = () => {
    if (containerRef.current) {
      const { scrollTop, scrollHeight, clientHeight } = containerRef.current;
      // 如果用户滚动到接近底部，自动滚动开启
      setAutoScroll(scrollHeight - scrollTop - clientHeight < 50);
    }
  };

  // 滚动到底部
  const scrollToBottom = () => {
    if (containerRef.current) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
      setAutoScroll(true);
    }
  };

  // 清除日志
  const clearLogs = () => {
    setLogs([]);
    setLogCount(0);
  };

  // 获取日志级别颜色
  const getLevelColor = (level: string) => {
    const colorMap: Record<string, string> = {
      INFO: '#1890ff',
      WARN: '#faad14',
      ERROR: '#f5222d',
      DEBUG: '#8c8c8c',
    };
    return colorMap[level] || '#8c8c8c';
  };

  // 获取日志级别背景色
  const getLevelBgColor = (level: string) => {
    const colorMap: Record<string, string> = {
      INFO: 'rgba(24, 144, 255, 0.1)',
      WARN: 'rgba(250, 173, 20, 0.1)',
      ERROR: 'rgba(245, 34, 45, 0.1)',
      DEBUG: 'rgba(140, 140, 140, 0.1)',
    };
    return colorMap[level] || 'rgba(140, 140, 140, 0.1)';
  };

  // 格式化时间
  const formatTime = (timestamp: string) => {
    try {
      const date = new Date(timestamp.replace('T', ' '));
      return date.toLocaleTimeString('zh-CN', { 
        hour: '2-digit', 
        minute: '2-digit', 
        second: '2-digit',
        hour12: false
      });
    } catch {
      return timestamp;
    }
  };

  return (
    <PageContainer
      header={{
        title: '日志管理',
        subTitle: '实时监控商品采集任务执行日志',
      }}
    >
      <Card>
        <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
          <Space>
            <Badge 
              status={isConnected ? "success" : "error"} 
              text={isConnected ? "已连接" : "未连接"} 
            />
            {connectionError && (
              <Tag color="red">{connectionError}</Tag>
            )}
            <Tag color={logCount > 0 ? "processing" : "default"}>
              共 {logCount} 条日志
            </Tag>
            <Tag color={logs.length > 0 ? "blue" : "default"}>
              当前显示 {logs.length} 条
            </Tag>
          </Space>
          <Space>
            {!autoScroll && (
              <Button size="small" type="primary" onClick={scrollToBottom}>
                滚动到底部
              </Button>
            )}
            <Button 
              size="small" 
              icon={<ClearOutlined />} 
              onClick={clearLogs}
              disabled={logs.length === 0}
            >
              清除日志
            </Button>
            <Button icon={<SyncOutlined />} onClick={handleReconnect}>
              重连
            </Button>
          </Space>
        </Space>
        
        <div 
          ref={containerRef}
          onScroll={handleScroll}
          style={{
            height: 'calc(100vh - 280px)',
            minHeight: 400,
            overflow: 'auto',
            background: '#1e1e1e',
            padding: '12px 16px',
            borderRadius: '6px',
            fontFamily: "'Consolas', 'Monaco', 'Courier New', monospace",
            fontSize: '13px',
            lineHeight: 1.6,
          }}
        >
          {logs.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '60px 0', color: '#8c8c8c' }}>
              <CloudServerOutlined style={{ fontSize: 48, marginBottom: 16 }} />
              <div>等待采集任务执行...</div>
              <div style={{ fontSize: 12, marginTop: 8 }}>
                {isConnected ? '已连接到日志服务器' : '正在连接...'}
              </div>
            </div>
          ) : (
            logs.map((log, index) => (
              <div 
                key={index} 
                style={{ 
                  marginBottom: '2px',
                  padding: '4px 8px',
                  borderRadius: '3px',
                  background: getLevelBgColor(log.level),
                }}
              >
                <Text type="secondary" style={{ fontSize: '11px', marginRight: 8 }}>
                  [{formatTime(log.timestamp)}]
                </Text>
                <Text 
                  strong 
                  style={{ 
                    color: getLevelColor(log.level), 
                    marginRight: 8,
                    fontSize: '12px'
                  }}
                >
                  [{log.level}]
                </Text>
                <Text style={{ color: log.level === 'ERROR' ? '#f14c4c' : '#d4d4d4' }}>
                  {log.message}
                </Text>
              </div>
            ))
          )}
        </div>
      </Card>
    </PageContainer>
  );
};

export default LogManagerPage;
