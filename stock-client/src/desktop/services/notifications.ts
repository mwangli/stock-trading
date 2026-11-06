// AI_GENERATE_START -
import type { ApiResponse } from '@shared/utils/api';
import { readApiData } from '@shared/utils/api';
import request from '@shared/utils/request';

export type NotificationCategory = 'SYSTEM' | 'TRADE';
export type NotificationLevel = 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR';

export interface NotificationItem {
  id: number;
  category: NotificationCategory;
  level: NotificationLevel;
  title: string;
  content: string;
  source: string;
  createdAt: string;
}

export interface NotificationFeed {
  available: boolean;
  items: NotificationItem[];
  latestId: number;
  cursorId: number;
  unreadCount: number;
}

interface NotificationQuery {
  afterId?: number;
  seenId: number;
  limit?: number;
}

export const fetchNotifications = async ({ afterId, seenId, limit = 50 }: NotificationQuery) => {
  const response = await request.get<unknown, ApiResponse<NotificationFeed>>('/notifications', {
    params: { afterId, seenId, limit },
  });
  return readApiData(response, '通知查询失败');
};
// AI_GENERATE_END -
