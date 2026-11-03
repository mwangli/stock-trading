// AI_GENERATE_START -
import React, { type ReactNode, useEffect, useState } from 'react';
import { Alert, Descriptions, Drawer } from 'antd';
import { useTranslation } from 'react-i18next';
import request from '@shared/utils/request';
import type { ApiResponse } from '@shared/utils/api';
import { formatDateTime } from '@shared/utils/dateTime';
import { localizedText } from '@shared/utils/localizedText';

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
  businessTitle?: string;
  businessItems?: StockDetailField[];
}

export interface StockDetailField {
  key: string;
  label: string;
  value: ReactNode;
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
const MARKET_CODE_PATTERN = /^(?:sh|sz|bj)\.\d{6}$/i;

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

const matchesStockCode = (candidate: unknown, selectedCode: string) => {
  const normalizedCandidate = String(candidate ?? '').trim().toLowerCase();
  const normalizedSelected = selectedCode.trim().toLowerCase();
  return normalizedCandidate === normalizedSelected
    || normalizedCandidate.endsWith(`.${normalizedSelected}`);
};

const StockDetailDrawer: React.FC<StockDetailDrawerProps> = ({ stock, onClose }) => {
  const { t } = useTranslation();
  const stockCode = stock?.code;
  const fallbackName = stock?.name;
  const providedDetails = stock?.details;
  const businessTitle = stock?.businessTitle;
  const businessItems = stock?.businessItems;
  const hasBusinessDetails = Boolean(businessItems?.length);
  const businessColumnCount = (businessItems?.length ?? 0) > 10 ? 2 : 1;
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

    if (hasBusinessDetails) {
      setDetail({ code: stockCode, name: fallbackName || stockCode });
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
        const instrumentResponse = await request.get('/market/instruments', {
          params: { keyword: stockCode, limit: 20 },
        }) as ApiResponse<Array<Record<string, unknown>>>;
        const instrument = instrumentResponse.success
          ? instrumentResponse.data.find((item) => matchesStockCode(item.code, stockCode))
          : undefined;
        const marketCode = String(instrument?.code ?? stockCode).trim().toLowerCase();
        const barsResponse = MARKET_CODE_PATTERN.test(marketCode)
          ? await request.get('/market/bars', {
            params: { code: marketCode, interval: 'day', limit: 2 },
          }) as ApiResponse<Array<Record<string, unknown>>>
          : null;
        const bars = barsResponse?.success ? barsResponse.data : [];
        const latest = bars[0];
        const previous = bars[1];
        const close = latest?.close == null ? undefined : Number(latest.close);
        const preclose = latest?.preclose == null
          ? (previous?.close == null ? undefined : Number(previous.close))
          : Number(latest.preclose);
        const resolvedDetail: StockDetail = {
          code: stockCode,
          name: String(instrument?.name ?? fallbackName ?? stockCode),
          market: instrument?.market == null ? undefined : String(instrument.market),
          price: close,
          changeAmount: close != null && preclose != null ? close - preclose : undefined,
          changePercent: close != null && preclose ? ((close - preclose) / preclose) * 100 : undefined,
          volume: latest?.volume == null ? undefined : Number(latest.volume),
          listedDate: instrument?.as_of_date == null ? undefined : String(instrument.as_of_date),
          updateTime: latest?.trade_date == null ? undefined : String(latest.trade_date),
        };
        if (active) {
          stockDetailCache.set(stockCode, resolvedDetail);
          setDetail(resolvedDetail);
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
  }, [fallbackName, hasBusinessDetails, providedDetails, stockCode]);

  const displayName = detail?.name || fallbackName || t('marketPage.details.title');
  const displayCode = detail?.code || stockCode;

  return (
    <Drawer
      open={Boolean(stock)}
      placement="right"
      size={hasBusinessDetails && businessColumnCount === 2
        ? 'min(640px, 100vw)'
        : 'min(480px, 100vw)'}
      rootClassName="stock-detail-drawer"
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
          {hasBusinessDetails ? (
            <Descriptions
              bordered
              size="small"
              column={businessColumnCount}
              title={businessTitle}
              labelStyle={{ width: businessColumnCount === 1 ? 112 : 96 }}
              items={businessItems?.map((item) => ({
                key: item.key,
                label: item.label,
                children: (
                  <span className="block min-w-0 whitespace-normal" style={{ overflowWrap: 'anywhere' }}>
                    {item.value}
                  </span>
                ),
              })) ?? []}
            />
          ) : (
            <>
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
                labelStyle={{ width: 112 }}
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
            </>
          )}
        </div>
      )}
    </Drawer>
  );
};

export default StockDetailDrawer;
// AI_GENERATE_END -
