import React, { useEffect, useRef } from 'react';
import { notification } from 'antd';
import { useNotificationStore } from '../store/notificationStore';

const NotificationListener: React.FC = () => {
  const { addNotification } = useNotificationStore();
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const connect = () => {
    // Avoid multiple connections
    if (wsRef.current?.readyState === WebSocket.OPEN) return;

    const wsUrl = 'ws://localhost:8080/ws/notifications';
    const ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      console.log('Notification WebSocket connected');
    };

    ws.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        console.log('Received notification:', message);

        // Add to store
        addNotification({
          type: message.level || 'info', // map backend 'level' to frontend 'type'
          title: message.title,
          description: message.content,
        });

        // Show toast
        notification.open({
            message: message.title,
            description: message.content,
            type: message.level || 'info',
            duration: 4.5,
        });

      } catch (error) {
        console.error('Failed to parse notification message', error);
      }
    };

    ws.onclose = () => {
      console.log('Notification WebSocket closed, reconnecting in 5s...');
      wsRef.current = null;
      reconnectTimeoutRef.current = setTimeout(connect, 5000);
    };

    ws.onerror = (error) => {
      console.error('Notification WebSocket error', error);
      ws.close();
    };

    wsRef.current = ws;
  };

  useEffect(() => {
    connect();

    return () => {
      if (wsRef.current) {
        wsRef.current.close();
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
    };
  }, []);

  return null; // Logic only component
};

export default NotificationListener;
