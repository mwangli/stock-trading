import React, { useState } from 'react';
import { Badge, Popover, List, Button, Typography, Space } from 'antd';
import { 
  BellOutlined, 
  CheckCircleOutlined, 
  ExclamationCircleOutlined, 
  InfoCircleOutlined, 
  CloseCircleOutlined,
  DeleteOutlined,
  CheckOutlined
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useNotificationStore } from '../store/notificationStore';
import { formatDistanceToNow } from 'date-fns';
import { zhCN, enUS } from 'date-fns/locale';

const { Text } = Typography;

const NotificationBell: React.FC = () => {
  const { t, i18n } = useTranslation();
  const [open, setOpen] = useState(false);
  const [displayCount, setDisplayCount] = useState(10);
  
  const { notifications, unreadCount, markAsRead, markAllAsRead, clearAll } = useNotificationStore();

  const handleOpenChange = (newOpen: boolean) => {
    setOpen(newOpen);
    if (!newOpen) {
      setDisplayCount(10);
    }
  };

  const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
    const target = e.currentTarget;
    const { scrollTop, scrollHeight, clientHeight } = target;
    if (scrollHeight - scrollTop - clientHeight < 50) {
      if (displayCount < notifications.length) {
        setDisplayCount(prev => Math.min(prev + 10, notifications.length));
      }
    }
  };

  const getIcon = (type: string) => {
    switch (type) {
      case 'success':
        return <CheckCircleOutlined className="text-[#00e396] text-lg" />;
      case 'warning':
        return <ExclamationCircleOutlined className="text-[#feb019] text-lg" />;
      case 'error':
        return <CloseCircleOutlined className="text-[#ff4560] text-lg" />;
      default:
        return <InfoCircleOutlined className="text-[#008ffb] text-lg" />;
    }
  };

  const content = (
    <div className="w-[420px] flex flex-col bg-[#1e1e2d] border border-white/10 rounded-xl shadow-2xl backdrop-blur-md">
      <div className="flex justify-between items-center px-5 py-4 border-b border-white/10 bg-white/5">
        <span className="font-bold text-white text-base">{t('notifications.title')}</span>
        <Space>
          <Button 
            type="text" 
            size="small" 
            icon={<CheckOutlined />} 
            onClick={() => markAllAsRead()}
            disabled={unreadCount === 0}
            className="text-gray-400 hover:text-white"
            title={t('notifications.markAllRead')}
          />
          <Button 
            type="text" 
            size="small" 
            icon={<DeleteOutlined />} 
            onClick={() => clearAll()}
            disabled={notifications.length === 0}
            className="text-gray-400 hover:text-red-500"
            title={t('notifications.clearAll')}
          />
        </Space>
      </div>

      <div 
        className="max-h-[600px] overflow-y-scroll custom-scrollbar-none"
        onScroll={handleScroll}
      >
        {notifications.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 text-gray-500">
            <BellOutlined className="text-4xl mb-3 opacity-20" />
            <Text type="secondary">{t('notifications.empty')}</Text>
          </div>
        ) : (
          <List
            itemLayout="horizontal"
            dataSource={notifications.slice(0, displayCount)}
            renderItem={(item) => (
              <List.Item 
                className={`mx-2 my-1.5 px-3 py-3 rounded-lg hover:bg-white/[0.08] transition-all duration-200 cursor-pointer ${!item.read ? 'bg-white/[0.04]' : 'opacity-80'}`}
                onClick={() => markAsRead(item.id)}
              >
                <div className="flex items-start gap-3 w-full">
                  <div className="p-2 rounded-full bg-white/5 shrink-0">
                    {getIcon(item.type)}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex justify-between items-start mb-1">
                      <Text className={`text-sm truncate ${!item.read ? 'text-white font-semibold' : 'text-gray-400'}`}>
                        {item.title}
                      </Text>
                      {!item.read && <div className="w-2 h-2 rounded-full bg-[#00e396] mt-1.5 ml-2 shrink-0 shadow-[0_0_6px_#00e396]" />}
                    </div>
                    <Text className="text-xs text-gray-500 line-clamp-2 block">
                      {item.description}
                    </Text>
                    <Text className="text-[10px] text-gray-600 font-mono mt-1 block">
                      {formatDistanceToNow(item.timestamp, { 
                        addSuffix: true,
                        locale: i18n.language === 'zh' ? zhCN : enUS 
                      })}
                    </Text>
                  </div>
                </div>
              </List.Item>
            )}
          />
        )}
        {displayCount < notifications.length && (
          <div className="text-center py-3 text-gray-500 text-sm">
            {t('notifications.loadMore') || '向下滚动加载更多...'}
          </div>
        )}
      </div>
    </div>
  );

  return (
    <Popover
      content={content}
      trigger="click"
      open={open}
      onOpenChange={handleOpenChange}
      placement="bottomRight"
      overlayClassName="glass-popover p-0"
      arrow={false}
    >
      <div className="relative cursor-pointer group">
        <Badge count={unreadCount} size="default" offset={[-4, 4]} color="#00e396" className="shadow-lg">
          <Button 
            type="text" 
            icon={<BellOutlined className="text-2xl transition-transform duration-300 group-hover:rotate-12" />} 
            className="text-gray-400 group-hover:text-white group-hover:bg-white/10 w-12 h-12 flex items-center justify-center rounded-full border-none transition-all duration-300"
          />
        </Badge>
      </div>
    </Popover>
  );
};

export default NotificationBell;
