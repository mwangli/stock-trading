import React, { useEffect } from 'react';
import { notification } from 'antd';
import { useNotificationStore } from '../store/notificationStore';
import { subscribe } from '../utils/notificationWebSocket';

/**
 * 订阅全局单例 WebSocket 推送，避免因 React Strict Mode / 路由导致重复建连与关闭
 */
const NotificationListener: React.FC = () => {
  const addNotification = useNotificationStore((s) => s.addNotification);

  useEffect(() => {
    const unsubscribe = subscribe((message: Record<string, unknown>) => {
      const title = (message.title as string) ?? '';
      const content = (message.content as string) ?? '';
      const type = ((message.level as string) ?? 'info') as 'info' | 'success' | 'warning' | 'error';

      addNotification({ type, title, description: content });
      notification.open({
        message: title,
        description: content,
        type,
        duration: 4.5,
      });
    });
    return unsubscribe;
  }, [addNotification]);

  return null;
};

export default NotificationListener;
