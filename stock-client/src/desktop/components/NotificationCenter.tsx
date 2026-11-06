// AI_GENERATE_START --
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Badge, Button, Empty, Popover, Segmented } from 'antd';
import {
  BellOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  ExclamationCircleFilled,
  InfoCircleFilled,
} from '@ant-design/icons';
import {
  fetchNotifications,
  type NotificationCategory,
  type NotificationItem,
  type NotificationLevel,
} from '../services/notifications';
import styles from './NotificationCenter.module.css';

const POLL_INTERVAL_MS = 5_000;
const NOTIFICATION_LIMIT = 100;
type NotificationFilter = 'ALL' | NotificationCategory;

interface NotificationCenterProps {
  userId?: number;
}

const storageKey = (userId?: number) =>
  'stockTrading.notifications.lastSeen.' + (userId ?? 'anonymous');

const readSeenId = (userId?: number) => {
  const value = Number(window.localStorage.getItem(storageKey(userId)));
  return Number.isSafeInteger(value) && value > 0 ? value : 0;
};

const mergeItems = (current: NotificationItem[], incoming: NotificationItem[]) => {
  const items = new Map<number, NotificationItem>();
  [...incoming, ...current].forEach((item) => items.set(item.id, item));
  return [...items.values()].sort((left, right) => right.id - left.id).slice(0, NOTIFICATION_LIMIT);
};

const formatTime = (value: string) => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
};

const categoryLabel = (category: NotificationCategory) =>
  category === 'TRADE' ? '交易通知' : '系统通知';

const levelIcon = (level: NotificationLevel) => {
  if (level === 'SUCCESS') return <CheckCircleFilled />;
  if (level === 'WARNING') return <ExclamationCircleFilled />;
  if (level === 'ERROR') return <CloseCircleFilled />;
  return <InfoCircleFilled />;
};

const levelClassName = (level: NotificationLevel) => {
  if (level === 'SUCCESS') return styles.success;
  if (level === 'WARNING') return styles.warning;
  if (level === 'ERROR') return styles.error;
  return styles.info;
};

const NotificationCenter: React.FC<NotificationCenterProps> = ({ userId }) => {
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState<NotificationFilter>('ALL');
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [latestId, setLatestId] = useState(0);
  const [unreadCount, setUnreadCount] = useState(0);
  const [available, setAvailable] = useState(true);
  const loadingRef = useRef(false);
  const cursorIdRef = useRef(0);
  const seenIdRef = useRef(0);

  useEffect(() => {
    let active = true;
    seenIdRef.current = readSeenId(userId);
    cursorIdRef.current = 0;
    setItems([]);
    setLatestId(0);
    setUnreadCount(0);
    setAvailable(true);

    const refresh = async () => {
      if (loadingRef.current) return;
      loadingRef.current = true;
      const afterId = cursorIdRef.current > 0 ? cursorIdRef.current : undefined;
      try {
        const feed = await fetchNotifications({
          afterId,
          seenId: seenIdRef.current,
          limit: NOTIFICATION_LIMIT,
        });
        if (!active) return;
        setAvailable(feed.available);
        if (!feed.available) return;
        setItems((current) => afterId === undefined ? feed.items : mergeItems(current, feed.items));
        setLatestId(feed.latestId);
        setUnreadCount(feed.unreadCount);
        cursorIdRef.current = Math.max(cursorIdRef.current, feed.cursorId);
      } catch {
        if (active) setAvailable(false);
      } finally {
        loadingRef.current = false;
      }
    };

    void refresh();
    const timer = window.setInterval(() => void refresh(), POLL_INTERVAL_MS);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [userId]);

  const filteredItems = useMemo(
    () => filter === 'ALL' ? items : items.filter((item) => item.category === filter),
    [filter, items],
  );

  const markAllRead = () => {
    window.localStorage.setItem(storageKey(userId), String(latestId));
    seenIdRef.current = latestId;
    setUnreadCount(0);
  };

  const panel = (
    <section className={styles.panel} aria-label="通知中心" role="dialog">
      <div className={styles.header}>
        <h2 className={styles.title}>通知中心</h2>
        <Button
          type="link"
          className={styles.readButton}
          disabled={unreadCount === 0}
          onClick={markAllRead}
        >
          全部已读
        </Button>
      </div>
      <div className={styles.filters}>
        <Segmented<NotificationFilter>
          block
          size="small"
          value={filter}
          options={[
            { label: '全部', value: 'ALL' },
            { label: '交易', value: 'TRADE' },
            { label: '系统', value: 'SYSTEM' },
          ]}
          onChange={setFilter}
        />
      </div>
      {!available && <div className={styles.serviceState}>通知服务暂不可用</div>}
      <div className={styles.list}>
        {filteredItems.length === 0 ? (
          <Empty className={styles.empty} image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无通知" />
        ) : filteredItems.map((item) => (
          <article className={styles.item} key={item.id}>
            <span className={[styles.icon, levelClassName(item.level)].join(' ')} aria-hidden="true">
              {levelIcon(item.level)}
            </span>
            <div>
              <div className={styles.itemHeader}>
                <span className={styles.itemTitle}>{item.title}</span>
                <time className={styles.time} dateTime={item.createdAt}>{formatTime(item.createdAt)}</time>
              </div>
              <p className={styles.content}>{item.content}</p>
              <div className={styles.meta}>
                <span className={styles.category}>{categoryLabel(item.category)}</span>
                <span>{item.source}</span>
              </div>
            </div>
          </article>
        ))}
      </div>
    </section>
  );

  const badgeCount = unreadCount > 99 ? '99+' : unreadCount;
  return (
    <span className="app-header-notification">
      <Popover
        content={panel}
        trigger="click"
        placement="bottomRight"
        classNames={{ root: styles.popover }}
        open={open}
        onOpenChange={setOpen}
      >
        <Badge className={styles.badge} count={badgeCount} color="#dc2626" offset={[-3, 4]}>
          <Button
            className={styles.trigger}
            type="text"
            icon={<BellOutlined />}
            aria-label="通知"
            aria-haspopup="dialog"
            aria-expanded={open}
          />
        </Badge>
      </Popover>
    </span>
  );
};

export default NotificationCenter;
// AI_GENERATE_END --
