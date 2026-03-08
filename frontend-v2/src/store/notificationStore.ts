import { create } from 'zustand';

export type NotificationType = 'success' | 'info' | 'warning' | 'error';

export interface Notification {
  id: string;
  type: NotificationType;
  title: string;
  description: string;
  timestamp: number;
  read: boolean;
}

interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  addNotification: (notification: Omit<Notification, 'id' | 'timestamp' | 'read'>) => void;
  markAsRead: (id: string) => void;
  markAllAsRead: () => void;
  clearAll: () => void;
}

const MOCK_NOTIFICATIONS: Notification[] = Array.from({ length: 20 }).map((_, i) => {
  const types: NotificationType[] = ['success', 'warning', 'info', 'error'];
  const titles = [
    '买入订单已执行',
    '卖出订单已执行',
    '高波动性预警',
    '止损触发',
    'RSI 超买信号',
    '布林带突破',
    '系统更新完成',
    '数据同步异常'
  ];
  const descriptions = [
    '成功买入 100 股 AAPL，成交价 $150.23',
    '成功卖出 50 股 TSLA，获利 12.5%',
    '市场波动率指数 (VIX) 已超过预设阈值，请注意风险控制。',
    '触发移动止损条件，已自动平仓保护利润。',
    'RSI 指标超过 70，建议关注卖出机会。',
    '价格突破布林带上轨，趋势可能反转。',
    '定期的系统维护已顺利完成，所有服务运行正常。',
    '与行情服务器的连接发生瞬时中断，正在尝试重新连接...'
  ];
  
  return {
    id: `${i + 1}`,
    type: types[i % 4],
    title: titles[i % 8],
    description: descriptions[i % 8],
    timestamp: Date.now() - 1000 * 60 * (i * 15 + 5), // Staggered times
    read: i > 4 // First 5 unread
  };
});

export const useNotificationStore = create<NotificationState>((set) => ({
  notifications: MOCK_NOTIFICATIONS,
  unreadCount: 5,

  addNotification: (notification) =>
    set((state) => {
      const newNotification: Notification = {
        ...notification,
        id: Math.random().toString(36).substr(2, 9),
        timestamp: Date.now(),
        read: false,
      };
      return {
        notifications: [newNotification, ...state.notifications],
        unreadCount: state.unreadCount + 1,
      };
    }),

  markAsRead: (id) =>
    set((state) => {
      const newNotifications = state.notifications.map((n) =>
        n.id === id ? { ...n, read: true } : n
      );
      return {
        notifications: newNotifications,
        unreadCount: newNotifications.filter((n) => !n.read).length,
      };
    }),

  markAllAsRead: () =>
    set((state) => ({
      notifications: state.notifications.map((n) => ({ ...n, read: true })),
      unreadCount: 0,
    })),

  clearAll: () => set({ notifications: [], unreadCount: 0 }),
}));
