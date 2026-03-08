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
  
  const { notifications, unreadCount, markAsRead, markAllAsRead, clearAll } = useNotificationStore();

  const handleOpenChange = (newOpen: boolean) => {
    setOpen(newOpen);
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

      <div className="max-h-[600px] overflow-y-auto custom-scrollbar p-2">
        {notifications.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-8 text-gray-500">
            <BellOutlined className="text-4xl mb-2 opacity-20" />
            <Text type="secondary">{t('notifications.empty')}</Text>
          </div>
        ) : (
          <List
            pagination={{
              position: 'bottom',
              align: 'center',
              pageSize: 5,
              size: 'small',
              className: 'text-white mt-2 mb-2',
            }}
            itemLayout="horizontal"
            dataSource={notifications}
            renderItem={(item) => (
              <List.Item 
                className={`mx-3 mb-2 rounded-xl border border-white/5 hover:bg-white/[0.08] hover:border-white/10 transition-all duration-300 cursor-pointer ${!item.read ? 'bg-white/[0.04]' : 'bg-[#1e1e2d] opacity-80'}`}
                onClick={() => markAsRead(item.id)}
              >
                <List.Item.Meta
                  avatar={
                    <div className="mt-1.5 p-2 rounded-full bg-white/5">
                      {getIcon(item.type)}
                    </div>
                  }
                  title={
                    <div className="flex justify-between items-start mb-1">
                      <Text className={`text-[15px] ${!item.read ? 'text-white font-semibold' : 'text-gray-400 font-medium'}`}>
                        {item.title}
                      </Text>
                      {!item.read && <div className="w-2.5 h-2.5 rounded-full bg-[#00e396] mt-2 ml-3 shrink-0 shadow-[0_0_8px_#00e396]" />}
                    </div>
                  }
                  description={
                    <div className="flex flex-col gap-1.5">
                      <Text className="text-sm text-gray-400 leading-relaxed line-clamp-2">
                        {item.description}
                      </Text>
                      <Text className="text-xs text-gray-500 font-mono mt-0.5">
                        {formatDistanceToNow(item.timestamp, { 
                          addSuffix: true,
                          locale: i18n.language === 'zh' ? zhCN : enUS 
                        })}
                      </Text>
                    </div>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </div>
      
      <div className="p-2 border-t border-white/10 text-center bg-white/5">
        <Button type="link" size="small" className="text-gray-400 hover:text-white text-xs">
          {t('notifications.viewAll')}
        </Button>
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
