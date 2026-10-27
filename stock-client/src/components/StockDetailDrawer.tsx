// AI_GENERATE_START -
import React, { useEffect, useState } from 'react';
import { Alert, Descriptions, Drawer } from 'antd';
import { useTranslation } from 'react-i18next';
import request from '../utils/request';
import type { ApiResponse } from '../utils/api';
import { formatDateTime } from '../utils/dateTime';
import { localizedText } from '../utils/localizedText';

export interface StockDetail {
  id?: string;
  code: string;
  name: string;
  market?: string;
  price?: number;
  changeAmount?: number;
  changePercent?: number;
  volume?: number;
  totalMarketValue?: number;
  turnoverRate?: number;
  volumeRatio?: number;
  industryCode?: number;
  listedDate?: string;
  updateTime?: string;
}

export interface StockDetailSelection {
  code: string;
  name?: string;
  details?: StockDetail;
}

interface StockDetailDrawerProps {
  stock: StockDetailSelection | null;
  onClose: () => void;
}

const MARKET_LABELS: Record<string, [string, string]> = {
  SH: ['上海', 'Shanghai'],
  SZ: ['深圳', 'Shenzhen'],
  BJ: ['北京', 'Beijing'],
};

const SECURITY_CATEGORY_LABELS: Record<number, [string, string]> = {
  0: ['深市主板', 'Shenzhen Main Board'],
  2: ['深市主板（原中小板）', 'Shenzhen Main Board'],
  9: ['沪市主板', 'Shanghai Main Board'],
  18: ['创业板', 'ChiNext'],
};

const compactAmountFormatters = {
  zh: new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 2 }),
  en: new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 2 }),
};

const stockDetailCache = new Map<string, StockDetail>();

const formatNumber = (value?: number, digits = 2) => value == null ? '-' : Number(value).toFixed(digits);
const formatPercent = (value?: number) => value == null ? '-' : `${Number(value).toFixed(2)}%`;

const formatMarket = (value?: string) => {
  if (!value) return '-';
  const labels = MARKET_LABELS[value.toUpperCase()];
  return labels ? localizedText(labels[0], labels[1]) : value;
};

const formatSecurityCategory = (value?: number) => {
  if (value == null) return '-';
  const labels = SECURITY_CATEGORY_LABELS[value];
  return labels ? localizedText(labels[0], labels[1]) : localizedText(`其他类别（${value}）`, `Other (${value})`);
};

const formatCompactAmount = (value?: number) => value == null
  ? '-'
  : localizedText(
    `¥${compactAmountFormatters.zh.format(Number(value))}`,
    `¥${compactAmountFormatters.en.format(Number(value))}`,
  );

const formatFullAmount = (value?: number) => value == null
  ? '-'
  : `¥${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;

const StockDetailDrawer: React.FC<StockDetailDrawerProps> = ({ stock, onClose }) => {
  const { t } = useTranslation();
  const stockCode = stock?.code;
  const fallbackName = stock?.name;
  const providedDetails = stock?.details;
  const [detail, setDetail] = useState<StockDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    if (!stockCode) {
      setDetail(null);
      setError(null);
      setLoading(false);
      return () => {
        active = false;
      };
    }

    if (providedDetails) {
      stockDetailCache.set(stockCode, providedDetails);
      setDetail(providedDetails);
      setError(null);
      setLoading(false);
      return () => {
        active = false;
      };
    }

    const cachedDetail = stockDetailCache.get(stockCode);
    if (cachedDetail) {
      setDetail(cachedDetail);
      setError(null);
      setLoading(false);
      return () => {
        active = false;
      };
    }

    setDetail({ code: stockCode, name: fallbackName || stockCode });
    setError(null);
    setLoading(true);
    void (async () => {
      try {
        const response = await request.get(`/stockData/code/${encodeURIComponent(stockCode)}`) as ApiResponse<StockDetail>;
        if (!response.success || !response.data) {
          throw new Error(response.message || localizedText('股票详情读取失败', 'Failed to load stock details'));
        }
        if (active) {
          stockDetailCache.set(stockCode, response.data);
          setDetail(response.data);
        }
      } catch (cause) {
        if (active) {
          setError(cause instanceof Error ? cause.message : localizedText('股票详情读取失败', 'Failed to load stock details'));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    })();

    return () => {
      active = false;
    };
  }, [fallbackName, providedDetails, stockCode]);

  const displayName = detail?.name || fallbackName || t('marketPage.details.title');
  const displayCode = detail?.code || stockCode;

  return (
    <Drawer
      open={Boolean(stock)}
      placement="right"
      width={480}
      loading={loading}
      title={displayCode ? (
        <div className="flex min-w-0 items-baseline gap-3">
          <span className="truncate text-base font-semibold text-[var(--text-primary)]">{displayName}</span>
          <span className="shrink-0 font-mono text-sm font-normal text-[var(--text-secondary)]">{displayCode}</span>
        </div>
      ) : t('marketPage.details.title')}
      onClose={onClose}
    >
      {error && <Alert className="mb-4" type="error" showIcon message={error} />}
      {detail && (
        <div className="flex flex-col gap-5">
          <div className="border-b border-[var(--border-color)] pb-5">
            <div className="text-xs text-[var(--text-secondary)]">{t('marketPage.details.price')}</div>
            <div className="mt-2 flex items-end gap-3">
              <span className="font-mono text-3xl font-semibold text-[var(--text-primary)]">
                {detail.price == null ? '-' : `¥${formatNumber(detail.price, 3)}`}
              </span>
              <span className={`pb-1 font-mono text-sm font-semibold ${(detail.changePercent ?? 0) >= 0 ? 'text-[#ef4444]' : 'text-[#22c55e]'}`}>
                {detail.changePercent != null && detail.changePercent > 0 ? '+' : ''}{formatPercent(detail.changePercent)}
              </span>
            </div>
          </div>
          <Descriptions
            bordered
            size="small"
            column={1}
            labelStyle={{ width: 128 }}
            items={[
              { key: 'market', label: t('marketPage.details.market'), children: formatMarket(detail.market) },
              { key: 'category', label: t('marketPage.details.category'), children: formatSecurityCategory(detail.industryCode) },
              { key: 'changeAmount', label: t('marketPage.details.changeAmount'), children: formatNumber(detail.changeAmount, 3) },
              { key: 'marketCap', label: t('marketPage.details.marketCap'), children: <span title={formatFullAmount(detail.totalMarketValue)}>{formatCompactAmount(detail.totalMarketValue)}</span> },
              { key: 'turnover', label: t('marketPage.details.turnover'), children: formatPercent(detail.turnoverRate) },
              { key: 'volumeRatio', label: t('marketPage.details.volumeRatio'), children: formatNumber(detail.volumeRatio) },
              { key: 'listedDate', label: t('marketPage.details.listedDate'), children: detail.listedDate?.slice(0, 10) || '-' },
              { key: 'updatedAt', label: t('marketPage.details.updatedAt'), children: formatDateTime(detail.updateTime) },
            ]}
          />
        </div>
      )}
    </Drawer>
  );
};

export default StockDetailDrawer;
// AI_GENERATE_END -