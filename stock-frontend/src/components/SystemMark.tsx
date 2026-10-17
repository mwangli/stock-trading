// AI_GENERATE_START -
import React from 'react';

type SystemMarkSize = 'micro' | 'small' | 'medium' | 'large';

interface SystemMarkProps {
  size?: SystemMarkSize;
  className?: string;
  title?: string;
}

const sizeClassMap: Record<SystemMarkSize, string> = {
  micro: 'system-mark-micro',
  small: 'system-mark-small',
  medium: 'system-mark-medium',
  large: 'system-mark-large',
};

/**
 * AI 股票交易系统品牌标识，使用历史橙色股票上涨箭头图形。
 */
const SystemMark: React.FC<SystemMarkProps> = ({ size = 'medium', className = '', title }) => (
  <span className={`system-mark ${sizeClassMap[size]} ${className}`.trim()} aria-label={title} role={title ? 'img' : undefined}>
    <svg viewBox="0 0 1920 1024" aria-hidden={title ? undefined : true} focusable="false" preserveAspectRatio="xMidYMid meet">
      <path d="M187.008 701.184l-66.816 69.12L258.176 903.68l66.816-69.12L186.88 701.312zM706.56 302.592l68.992-66.816a96 96 0 0 0-137.984 0l68.992 66.816zM1100.8 709.76l-68.992 66.816a96 96 0 0 0 137.984 0L1100.8 709.76zM1664 128l68.992 66.816L1890.432 32H1664V128zM1326.08 32h-96v192h96V32zM324.992 834.816l450.56-465.536L637.44 235.776l-450.56 465.408L325.12 834.816zM637.44 369.28l394.24 407.296L1169.92 643.072l-394.24-407.296L637.44 369.28zM1169.92 776.576l563.2-581.76L1594.88 61.184l-563.2 581.888L1169.92 776.576zM1664 32h-337.92v192H1664V32z" fill="#FF5100" />
    </svg>
  </span>
);

export default SystemMark;
// AI_GENERATE_END -
