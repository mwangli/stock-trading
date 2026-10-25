// AI_GENERATE_START -
import React from 'react';
import { Tooltip } from 'antd';
import SystemMark from './SystemMark';

type SystemStatus = 'checking' | 'online' | 'offline';

interface SystemBrandProps {
  collapsed?: boolean;
  status: SystemStatus;
  statusLabel: string;
  title: string;
  className?: string;
}

/**
 * 系统统一品牌区域，保证侧边栏与文档页顶部使用相同的标识、名称和状态展示。
 */
const SystemBrand: React.FC<SystemBrandProps> = ({
  collapsed = false,
  status,
  statusLabel,
  title,
  className = '',
}) => (
  <div className={`app-brand${collapsed ? ' app-brand-collapsed' : ''} ${className}`.trim()} title={title}>
    <SystemMark size="small" title={title} />
    {!collapsed && (
      <div className="app-brand-copy">
        <div className="app-brand-title">
          <strong>{title}</strong>
          <Tooltip title={statusLabel}>
            <span
              className={`app-brand-status-dot is-${status}`}
              role="status"
              aria-label={statusLabel}
            />
          </Tooltip>
        </div>
      </div>
    )}
  </div>
);

export default SystemBrand;
// AI_GENERATE_END -
