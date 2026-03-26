import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Tag, Table, Spin, Empty, Tooltip, message, Input, Select, Modal, Tabs } from 'antd';
const { TextArea } = Input;
import type { ColumnsType } from 'antd/es/table';
import {
  ThunderboltOutlined,
  ReloadOutlined,
  HistoryOutlined,
  ExperimentOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  CloudServerOutlined,
  CodeOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { getLstmModelList, getLstmModelResult, type LstmModelListItem, type LstmModelListParams, type LstmModelResult } from '../api/lstm';
import { getSentimentHealth, analyzeSentiment, reloadSentimentModel, type SentimentHealth, type SentimentAnalyzeResult } from '../api/sentiment';

interface SentimentTestSample {
  label: 'positive' | 'neutral' | 'negative';
  text: string;
}

const SENTIMENT_TEST_SAMPLES: SentimentTestSample[] = [
  {
    label: 'positive',
    text: '国家发改委发布新一轮资本市场改革举措，拟进一步降低交易成本、优化退市机制，多项政策将于下月起正式落地。受此消息提振，A股市场情绪明显回暖，多家券商研报集体上调对明年盈利增速的预期。',
  },
  {
    label: 'positive',
    text: '某新能源龙头公司公告称，其最新一代动力电池已与全球多家主流车企达成长期供货协议，预计未来三年订单金额超千亿元。公司表示，新产品毛利率有望显著高于现有型号。',
  },
  {
    label: 'negative',
    text: '受海外需求持续疲软影响，最新公布的出口数据大幅低于市场预期，多项制造业景气指数跌至近两年低位。分析人士指出，外需走弱叠加企业信心不足，短期内经济下行压力仍然较大。',
  },
  {
    label: 'negative',
    text: '某互联网平台因多项数据安全与广告合规问题被监管部门立案调查，公司可能面临高额罚款及业务整改。消息公布后，市场担忧其盈利能力将受到长期压制，股价盘中一度跳水。',
  },
  {
    label: 'neutral',
    text: '某白酒企业发布三季度业绩预告，预计营收与净利润与去年同期基本持平。公司表示，本期销售节奏与去年存在一定差异，但整体渠道库存处于合理水平，产品结构调整仍在推进中。',
  },
  {
    label: 'neutral',
    text: '近期人民币汇率在双向波动中保持基本稳定，一方面外部环境仍存在不确定性，另一方面国内经济数据有所改善。业内人士认为，短期市场情绪或继续反复，但长期汇率基础条件并未发生根本变化。',
  },
  {
    label: 'negative',
    text: '受突发公共卫生事件影响，多地线下消费场景被迫关闭，旅游、餐饮、线下零售等行业订单大面积取消。部分中小企业出现现金流紧张甚至停业风险，市场避险情绪快速升温，资金加速流向避险资产。',
  },
  {
    label: 'positive',
    text: '多项关键经济数据连续三个月超出预期，制造业与服务业景气度同步回升。国际评级机构上调了对本国经济增长的预测，并表示当前改革进程有望夯实中长期增长潜力，提振投资者信心。',
  },
  {
    label: 'negative',
    text: '由于原材料价格阶段性上行，部分制造业企业成本压力有所抬升，利润空间被进一步压缩。虽然目前整体行业仍保持增长，但不少企业管理层在调研中表达了对明年盈利的不确定担忧。',
  },
  {
    label: 'positive',
    text: '交通、基建等领域一批重大项目集中开工，地方专项债资金加速使用，有望带动相关产业链订单稳步增加。分析认为，这将对稳投资、稳就业形成一定支撑，但政策效果仍需时间进一步体现。',
  },
];

/** Mock 模型列表，用于接口不可用或空数据时展示比对效果 */
function getMockLstmList(): LstmModelListItem[] {
  const now = new Date();
  const fmt = (d: Date, daysAgo: number) => {
    const x = new Date(d);
    x.setDate(x.getDate() - daysAgo);
    return x.toISOString();
  };
  const toScore = (v: number) => Math.max(0, Math.min(100, (1 - v) * 100));

  const makeItem = (
    idx: number,
    stockCode: string,
    stockName: string,
    epoch: number,
    score: number | null,
    daysAgo: number,
    modelVersion: string | null = 'v1'
  ): LstmModelListItem => {
    const ts = fmt(now, daysAgo);
    const baseScore = score != null ? toScore(score) : null;
    return {
      id: -idx, // 负数 ID 标记为 Mock 数据
      stockCode,
      stockName,
      trained: true,
      training: false,
      lastTrainTime: ts,
      lastDurationSeconds: null,
      lastEpochs: epoch,
      lastTrainLoss: null,
      lastValLoss: null,
      lastModelId: null,
      createdAt: ts,
      updatedAt: ts,
      modelName: stockCode,
      name: stockName,
      epoch,
      modelVersion,
      profitAmount: null,
      score: baseScore,
    };
  };

  return [
    makeItem(1, '600519', '贵州茅台', 100, 0.0281, 1),
    makeItem(2, '000858', '五粮液', 80, 0.0356, 2),
    makeItem(3, '601318', '中国平安', 120, 0.0245, 3, 'v2'),
    makeItem(4, '000333', '美的集团', 60, 0.0489, 5),
    makeItem(5, '300750', '宁德时代', 150, 0.0189, 7),
    makeItem(6, '600036', '招商银行', 90, 0.0312, 10),
    makeItem(7, '000001', '平安银行', 70, null, 14, 'v2'),
    makeItem(8, '601012', '隆基绿能', 110, 0.0267, 21),
  ];
}

/** 相对时间文案（依赖 i18n，在组件内调用时传入 t） */
function formatRelativeTime(
  dateStr: string,
  t: (key: string, opts?: { n?: number }) => string
): string {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return '';
  const now = Date.now();
  const diffMs = now - d.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  const diffHour = Math.floor(diffMs / 3600000);
  const diffDay = Math.floor(diffMs / 86400000);
  if (diffMin < 1) return t('common.timeJustNow');
  if (diffMin < 60) return t('common.timeMinutesAgo', { n: diffMin });
  if (diffHour < 24) return t('common.timeHoursAgo', { n: diffHour });
  if (diffDay < 7) return t('common.timeDaysAgo', { n: diffDay });
  return '';
}

const Strategies: React.FC = () => {
  const { t } = useTranslation();
  const [lstmList, setLstmList] = useState<LstmModelListItem[]>(() => getMockLstmList());
  const [lstmTotal, setLstmTotal] = useState(() => getMockLstmList().length);
  const [lstmLoading, setLstmLoading] = useState(true);
  const [lstmError, setLstmError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [sortBy, setSortBy] = useState<'createdAt' | 'epoch' | 'valLoss'>('createdAt');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');
  const [sentimentHealth, setSentimentHealth] = useState<SentimentHealth | null>(null);
  const [sentimentLoading, setSentimentLoading] = useState(false);
  const [resultModalVisible, setResultModalVisible] = useState(false);
  const [resultModalLoading, setResultModalLoading] = useState(false);
  const [resultModalData, setResultModalData] = useState<LstmModelResult | null>(null);
  const [sentimentTestModalVisible, setSentimentTestModalVisible] = useState(false);
  const [sentimentTestText, setSentimentTestText] = useState('');
  const [sentimentTestLoading, setSentimentTestLoading] = useState(false);
  const [sentimentTestResult, setSentimentTestResult] = useState<SentimentAnalyzeResult | null>(null);
  const [sentimentReloading, setSentimentReloading] = useState(false);
  const [sentimentSampleIndex, setSentimentSampleIndex] = useState(0);

  const fetchLstmList = useCallback(async (opts?: { isRefresh?: boolean; page?: number; pageSize?: number; keyword?: string; sortBy?: 'createdAt' | 'epoch' | 'valLoss'; sortOrder?: 'asc' | 'desc' }) => {
    const isRefresh = opts?.isRefresh ?? false;
    const page = opts?.page ?? current;
    const ps = opts?.pageSize ?? pageSize;
    const kw = opts?.keyword !== undefined ? opts.keyword : keyword;
    const sb = opts?.sortBy ?? sortBy;
    const so = opts?.sortOrder ?? sortOrder;
    if (isRefresh) setRefreshing(true);
    else setLstmLoading(true);
    setLstmError(null);
    try {
      const params: LstmModelListParams = { current: page, pageSize: ps, sortBy: sb, sortOrder: so };
      if (kw.trim()) params.keyword = kw.trim();
      const result = await getLstmModelList(params);
      const list = result.list?.length ? result.list : getMockLstmList();
      const total = result.list?.length ? result.total : list.length;
      setLstmList(list);
      setLstmTotal(total);
      setLstmError(null);
      if (opts?.page !== undefined) setCurrent(opts.page);
      if (opts?.pageSize !== undefined) setPageSize(opts.pageSize);
      if (opts?.keyword !== undefined) setKeyword(opts.keyword);
      if (opts?.sortBy !== undefined) setSortBy(opts.sortBy);
      if (opts?.sortOrder !== undefined) setSortOrder(opts.sortOrder);
      if (isRefresh) message.success(t('models.lstm.refreshSuccess'));
    } catch {
      const mock = getMockLstmList();
      setLstmList(mock);
      setLstmTotal(mock.length);
      setLstmError(null);
    } finally {
      setLstmLoading(false);
      setRefreshing(false);
    }
  }, [t, current, pageSize, keyword, sortBy, sortOrder]);

  const fetchSentimentHealth = useCallback(async () => {
    setSentimentLoading(true);
    try {
      const health = await getSentimentHealth();
      setSentimentHealth(health);
    } catch {
      setSentimentHealth({ status: 'DOWN', service: 'Sentiment', modelLoaded: false });
    } finally {
      setSentimentLoading(false);
    }
  }, []);

  const handleReloadSentiment = useCallback(async () => {
    if (sentimentReloading) return;
    setSentimentReloading(true);
    try {
      const res = await reloadSentimentModel();
      if (res.success) {
        message.success(res.message || t('models.sentiment.reloadSuccess'));
        await fetchSentimentHealth();
      } else {
        message.error(res.message || t('models.sentiment.reloadFailed'));
      }
    } catch {
      message.error(t('models.sentiment.reloadFailed'));
    } finally {
      setSentimentReloading(false);
    }
  }, [sentimentReloading, fetchSentimentHealth, t]);

  useEffect(() => {
    fetchLstmList();
  }, []);

  useEffect(() => {
    fetchSentimentHealth();
  }, [fetchSentimentHealth]);

  const formatDate = useCallback((s: string) => {
    if (!s) return '—';
    try {
      const d = new Date(s);
      return Number.isNaN(d.getTime()) ? s : d.toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' });
    } catch {
      return s;
    }
  }, []);

  const openResultModal = useCallback((modelId: string | number, record?: LstmModelListItem) => {
    setResultModalVisible(true);
    setResultModalData(null);
    if (typeof record?.id === 'number' && record.id < 0) {
      setResultModalData({
        modelId: String(record.id),
        modelName: record.modelName,
        profitAmount: record.profitAmount ?? null,
        score: record.score ?? null,
      });
      setResultModalLoading(false);
      return;
    }
    setResultModalLoading(true);
    getLstmModelResult(String(modelId))
      .then((data) => setResultModalData(data))
      .catch(() => message.error(t('models.lstm.listError')))
      .finally(() => setResultModalLoading(false));
  }, [t]);

  const resultTableColumns = useMemo(() => [
    { title: t('models.resultModal.metric'), dataIndex: 'metric', key: 'metric', width: 120 },
    { title: t('models.resultModal.value'), dataIndex: 'value', key: 'value' },
  ], [t]);

  const resultTableData = useMemo(() => {
    if (!resultModalData) return [];
    const hasProfit = resultModalData.profitAmount != null;
    const hasScore = resultModalData.score != null;
    if (!hasProfit && !hasScore) return [];
    const rows: { key: string; metric: string; value: string }[] = [];
    rows.push({
      key: 'profit',
      metric: t('models.resultModal.profitAmount'),
      value: hasProfit ? `${Number(resultModalData.profitAmount).toFixed(2)} 元` : '—',
    });
    rows.push({
      key: 'score',
      metric: t('models.resultModal.score'),
      value: hasScore ? Number(resultModalData.score).toFixed(1) : '—',
    });
    return rows;
  }, [resultModalData, t]);

  const lstmColumnWidth = 100;
  const lstmColumns: ColumnsType<LstmModelListItem> = useMemo(() => [
    {
      title: t('models.list.code'),
      dataIndex: 'modelName',
      key: 'modelName',
      width: lstmColumnWidth,
      render: (v: string) => {
        const text = v ?? '—';
        const cell = <span className="font-mono text-[#00e396]">{text}</span>;
        return text.length > 16 ? <Tooltip title={text}>{cell}</Tooltip> : cell;
      },
    },
    {
      title: t('models.list.name'),
      dataIndex: 'name',
      key: 'name',
      width: lstmColumnWidth,
      ellipsis: true,
      render: (v: string) => {
        const text = v ?? '—';
        const cell = <span className="text-white/90">{text}</span>;
        return text.length > 12 ? <Tooltip title={text}>{cell}</Tooltip> : cell;
      },
    },
    { title: t('models.list.version'), dataIndex: 'modelVersion', key: 'modelVersion', width: lstmColumnWidth, render: (v: string) => <span className="text-gray-400">{v ?? '—'}</span> },
    { title: t('models.lstm.epochs'), dataIndex: 'epoch', key: 'epoch', width: lstmColumnWidth, render: (v: number) => <span className="font-mono text-gray-300">{v ?? '—'}</span> },
    {
      title: t('models.list.profitAmount'),
      dataIndex: 'profitAmount',
      key: 'profitAmount',
      width: lstmColumnWidth,
      render: (v: number | undefined | null) => (v != null ? <span className="font-mono text-[#00e396]/90">{v.toFixed(2)} 元</span> : <span className="text-gray-500">—</span>),
    },
    {
      title: t('models.list.score'),
      dataIndex: 'score',
      key: 'score',
      width: lstmColumnWidth,
      render: (v: number | undefined | null) => (v != null ? <span className="font-mono text-amber-400/90">{v.toFixed(1)}</span> : <span className="text-gray-500">—</span>),
    },
    {
      title: t('models.list.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: lstmColumnWidth,
      render: (v: string) => {
        const abs = formatDate(v);
        const rel = formatRelativeTime(v, t);
        return (
          <span className="text-gray-400 text-sm block">
            <span>{abs}</span>
            {rel ? <span className="block text-xs text-gray-500 mt-0.5">{rel}</span> : null}
          </span>
        );
      },
    },
    {
      title: t('models.list.status'),
      key: 'status',
      width: lstmColumnWidth,
      render: (_: unknown, record: LstmModelListItem) => {
        const isTraining = record.training;
        const isTrained = record.trained;
        let color: 'success' | 'processing' | 'default' = 'default';
        let icon: React.ReactNode;
        let label: string;

        if (isTraining) {
          color = 'processing';
          icon = <SyncOutlined spin className="mr-1" />;
          label = t('models.list.statusTraining');
        } else if (isTrained) {
          color = 'success';
          icon = <CheckCircleOutlined className="mr-1" />;
          label = t('models.list.statusDeployed');
        } else {
          color = 'default';
          icon = <CloseCircleOutlined className="mr-1" />;
          label = t('models.list.statusNotTrained');
        }
        return (
          <Tag color={color} className="border-none">
            {icon}
            {label}
          </Tag>
        );
      },
    },
    {
      title: t('models.list.action'),
      key: 'action',
      width: lstmColumnWidth,
      render: (_: unknown, record: LstmModelListItem) => (
        <Button
          type="link"
          size="small"
          className="text-[#00e396] hover:text-[#00c985] p-0"
          onClick={() => openResultModal(record.id, record)}
        >
          {t('models.actions.result')}
        </Button>
      ),
    },
  ], [t, formatDate, openResultModal]);

  const models = [
    {
      id: 'lstm',
      title: t('models.lstm.title'),
      desc: t('models.lstm.desc'),
      status: 'online',
      color: '#00e396',
      icon: <ThunderboltOutlined />,
      stats: [
        { label: t('models.lstm.accuracy'), value: '87.4%', color: '#00e396' },
        { label: t('models.lstm.loss'), value: '0.024', color: '#feb019' },
        { label: t('models.lstm.epochs'), value: '500', color: '#fff' }
      ]
    },
    {
      id: 'sentiment-rules',
      title: t('models.rules.title'),
      desc: t('models.rules.desc'),
      status: 'offline',
      color: '#a3a3a3',
      icon: <CodeOutlined />,
      stats: [
        { label: t('models.rules.score'), value: '72', color: '#00e396' },
        { label: t('models.rules.matches'), value: '5', color: '#feb019' },
        { label: t('models.rules.coverage'), value: '86%', color: '#a3a3a3' }
      ]
    }
  ];

  const handleTableChange = useCallback((page: number, size: number) => {
    fetchLstmList({ page, pageSize: size });
  }, [fetchLstmList]);

  const handleSearch = useCallback((value: string) => {
    setKeyword(value);
    fetchLstmList({ keyword: value, page: 1 });
  }, [fetchLstmList]);

  const sentimentModels = useMemo(() => {
    const base = models.find((m) => m.id === 'sentiment-rules');
    if (!base) return [];
    return [base, { ...base, id: `${base.id}-clone` }];
  }, [models]);

  const tabItems = useMemo(
    () => [
      {
        key: 'lstm',
        label: (
          <span className="flex items-center gap-2">
            <ThunderboltOutlined className="text-[#00e396]" />
            LSTM 模型
          </span>
        ),
        children: (
          <div className="space-y-6 pt-2">
            <motion.div
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              className="flex flex-wrap gap-4 items-center justify-between"
            >
              <div className="glass px-5 py-3 rounded-xl border border-white/10 flex items-center gap-3">
                <ThunderboltOutlined className="text-[#00e396]" />
                <span className="text-gray-400 text-sm">{t('models.summary.lstmCount')}</span>
                <span className="text-xl font-bold text-white">{lstmTotal}</span>
              </div>
              <Button
                type="primary"
                size="small"
                icon={<ReloadOutlined spin={refreshing} />}
                className="bg-[#00e396] text-black border-none font-bold hover:bg-[#00c985] shrink-0"
                onClick={() => message.info(t('models.actions.retrain'))}
              >
                {t('models.actions.retrain')}
              </Button>
            </motion.div>
            <motion.div
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              className="glass rounded-2xl border border-white/10 overflow-hidden"
            >
              <div className="px-6 py-4 border-b border-white/10 flex items-center justify-between">
          <h2 className="text-lg font-bold flex items-center gap-2">
            <ThunderboltOutlined className="text-[#00e396]" />
            {t('models.lstm.listTitle')}
            {lstmList.length > 0 && lstmList.every((m) => typeof m.id === 'number' && m.id < 0) && (
              <Tag color="orange" className="border-none text-xs font-normal">
                当前为 Mock 数据，仅用于展示表格比对效果
              </Tag>
            )}
          </h2>
          <div className="flex items-center gap-2">
            <Button
              type="text"
              size="small"
              icon={<ReloadOutlined spin={refreshing} />}
              onClick={() => fetchLstmList({ isRefresh: true })}
              loading={refreshing}
              disabled={lstmLoading}
              className="text-gray-400 hover:text-[#00e396] shrink-0"
            />
          </div>
        </div>
        <div className="px-6 py-3 bg-white/[0.02] border-b border-white/5 flex items-center gap-3 flex-nowrap">
          <Input.Search
            placeholder={t('models.list.searchPlaceholder')}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onSearch={handleSearch}
            allowClear
            size="middle"
            className="w-48 sm:w-56 [&_.ant-input-group-addon]:bg-white/5 [&_.ant-input-group-addon]:border-white/10 [&_.ant-input]:bg-white/5 [&_.ant-input]:border-white/10"
          />
            <Select
              value={`${sortBy}-${sortOrder}`}
              onChange={(v) => {
                const [sb, so] = v.split('-') as ['createdAt' | 'epoch' | 'valLoss', 'asc' | 'desc'];
                setSortBy(sb);
                setSortOrder(so);
                fetchLstmList({ sortBy: sb, sortOrder: so, page: 1 });
              }}
              options={[
                { value: 'createdAt-desc', label: t('models.list.sortCreatedAt') + ' ↓' },
                { value: 'createdAt-asc', label: t('models.list.sortCreatedAt') + ' ↑' },
                { value: 'epoch-desc', label: t('models.list.sortEpoch') + ' ↓' },
                { value: 'epoch-asc', label: t('models.list.sortEpoch') + ' ↑' },
                { value: 'valLoss-asc', label: t('models.list.sortValLoss') + ' ↑' },
                { value: 'valLoss-desc', label: t('models.list.sortValLoss') + ' ↓' },
              ]}
            size="middle"
            className="w-36 shrink-0 [&_.ant-select-selector]:bg-white/5 [&_.ant-select-selector]:border-white/10"
          />
        </div>
        {lstmList.length > 0 && (() => {
          const withScore = lstmList.filter((m) => m.score != null);
          const avgScore = withScore.length > 0
            ? withScore.reduce((a, m) => a + (m.score ?? 0), 0) / withScore.length
            : null;
          return (
            <div className="px-6 py-2 flex flex-wrap items-center gap-4 text-sm border-b border-white/5 bg-white/[0.02]">
              <span className="text-gray-400">{t('models.effect.title')}:</span>
              <span className="text-white/80">
                {t('models.effect.withMetrics')} <span className="font-mono text-[#00e396]">{withScore.length}</span> / {lstmList.length}
              </span>
              {avgScore != null && (
                <span className="text-white/80">
                  {t('models.effect.avgScore')} <span className="font-mono text-amber-400/90">{avgScore.toFixed(1)}</span>
                </span>
              )}
            </div>
          );
        })()}
        <div className="p-4">
          {lstmLoading && lstmList.length === 0 ? (
            <div className="flex justify-center py-12">
              <Spin size="large" tip={t('common.loading')} />
            </div>
          ) : lstmError ? (
            <Empty description={lstmError} className="py-8">
              <Button type="primary" size="small" onClick={() => fetchLstmList()}>{t('common.retry')}</Button>
            </Empty>
          ) : lstmList.length === 0 ? (
            <Empty
              className="py-8"
              description={
                <span>
                  <span className="block">{t('models.lstm.listEmpty')}</span>
                  <span className="block text-sm text-gray-500 mt-1">{t('models.lstm.listEmptyHint')}</span>
                </span>
              }
            />
          ) : (
            <Table
              rowKey="id"
              columns={lstmColumns}
              dataSource={lstmList}
              size="small"
              loading={refreshing}
              pagination={{
                current,
                pageSize,
                total: lstmTotal,
                showSizeChanger: true,
                pageSizeOptions: ['10', '20', '50'],
                showTotal: (total) => t('models.list.total', { count: total }),
                onChange: handleTableChange,
              }}
              className="models-table"
              scroll={{ x: 960 }}
            />
          )}
        </div>
            </motion.div>
          </div>
        ),
      },
      {
        key: 'sentiment',
        label: (
          <span className="flex items-center gap-2">
            <CloudServerOutlined className="text-[#00b3f0]" />
            情感模型
          </span>
        ),
        children: (
          <div className="space-y-6 pt-2">
            <motion.div
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              className="flex flex-wrap gap-4"
            >
              <div className="glass px-5 py-3 rounded-xl border border-white/10 flex items-center gap-3">
                <CloudServerOutlined className="text-[#00b3f0]" />
                <span className="text-gray-400 text-sm">{t('models.summary.sentimentStatus')}</span>
                <div className="flex items-center gap-3">
                  {sentimentLoading ? (
                    <Spin size="small" />
                  ) : (
                    <Tag
                      color={sentimentHealth?.status === 'UP' ? 'success' : 'default'}
                      className="border-none flex items-center gap-1.5"
                    >
                      {sentimentHealth?.status === 'UP' ? (
                        <CheckCircleOutlined />
                      ) : (
                        <CloseCircleOutlined />
                      )}
                      <span>
                        {sentimentHealth?.status === 'UP'
                          ? t('models.summary.sentimentOnline')
                          : t('models.summary.sentimentOffline')}
                      </span>
                    </Tag>
                  )}
                </div>
              </div>
            </motion.div>
              <motion.div
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.05 }}
                className="glass rounded-2xl border border-white/10 overflow-hidden p-6 min-h-[260px]"
            >
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-bold flex items-center gap-2">
                  <CodeOutlined className="text-[#a3a3a3]" />
                  {t('models.rules.title')}
                </h2>
                <div className="flex items-center gap-2">
                  {sentimentLoading ? (
                    <Spin size="small" />
                  ) : (
                    <Tag
                      color={sentimentHealth?.status === 'UP' ? 'success' : 'default'}
                      className="border-none flex items-center gap-1.5"
                    >
                      {sentimentHealth?.status === 'UP' ? (
                        <CheckCircleOutlined />
                      ) : (
                        <CloseCircleOutlined />
                      )}
                      <span>
                        {sentimentHealth?.status === 'UP'
                          ? t('models.summary.sentimentOnline')
                          : t('models.summary.sentimentOffline')}
                      </span>
                    </Tag>
                  )}
                  <Button
                    type="primary"
                    size="small"
                    icon={<ReloadOutlined spin={sentimentReloading} />}
                    loading={sentimentReloading}
                    className="bg-[#00e396] text-black border-none font-bold hover:bg-[#00c985] shrink-0"
                    onClick={handleReloadSentiment}
                  >
                    {t('models.actions.retrain')}
                  </Button>
                </div>
              </div>
              <p className="text-gray-400 text-sm mb-4">{t('models.rules.desc')}</p>
              <div className="flex flex-wrap gap-4 text-sm items-center">
                {sentimentHealth?.lastLoadedTime && (
                  <span className="text-gray-500">
                    {t('models.list.createdAt')}: {new Date(sentimentHealth.lastLoadedTime).toLocaleString()}
                  </span>
                )}
                <Button
                  type="primary"
                  size="middle"
                  icon={<ExperimentOutlined />}
                  className="bg-[#00e396] text-black border-none hover:bg-[#00c985]"
                  onClick={() => {
                    setSentimentSampleIndex(0);
                    if (SENTIMENT_TEST_SAMPLES.length > 0) {
                      setSentimentTestText(SENTIMENT_TEST_SAMPLES[0].text);
                    } else {
                      setSentimentTestText('');
                    }
                    setSentimentTestResult(null);
                    setSentimentTestModalVisible(true);
                  }}
                >
                  {t('models.sentiment.testButton')}
                </Button>
              </div>
            </motion.div>
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
              {sentimentModels.map((model, index) => (
          <motion.div
            key={model.id}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: index * 0.1 }}
            className="relative glass rounded-2xl p-8 border border-white/10 overflow-hidden"
          >
             {/* Background Glow */}
             <div className={`absolute top-0 right-0 w-64 h-64 bg-[${model.color}]/5 blur-[80px] rounded-full pointer-events-none`} />

            <div className="flex justify-between items-start mb-6">
              <div className="flex items-center gap-4">
                <div className={`w-16 h-16 rounded-2xl flex items-center justify-center text-3xl bg-[${model.color}]/20 text-[${model.color}] shadow-lg shadow-[${model.color}]/20`}>
                  {model.icon}
                </div>
                <div>
                  <h2 className="text-2xl font-bold text-white">{model.title}</h2>
                  <div className="flex items-center gap-2 mt-1">
                    <Tag color={model.status === 'online' ? 'success' : 'processing'} className="border-none px-2 py-0.5">
                      {model.status === 'online' ? <CheckCircleOutlined /> : <SyncOutlined spin />} 
                      <span className="ml-1 uppercase">{model.status}</span>
                    </Tag>
                  </div>
                </div>
              </div>
            </div>

            <p className="text-gray-400 mb-8 h-12 text-lg leading-relaxed">{model.desc}</p>

            <div className="grid grid-cols-3 gap-4 mb-8 bg-white/5 p-4 rounded-xl border border-white/5">
              {model.stats.map((stat, i) => (
                <div key={i} className="text-center">
                  <div className="text-xs text-gray-500 uppercase mb-1">{stat.label}</div>
                  <div className="text-xl font-bold font-mono" style={{ color: stat.color }}>{stat.value}</div>
                </div>
              ))}
            </div>

            <div className="flex gap-3">
              <Button type="primary" icon={<ExperimentOutlined />} className="flex-1 bg-white/10 border-white/10 hover:bg-white/20 hover:border-white/30 h-10">
                {t('models.actions.backtest')}
              </Button>
              <Button icon={<HistoryOutlined />} className="flex-1 bg-transparent border-white/10 text-gray-400 hover:text-white hover:border-white h-10">
                {t('models.actions.result')}
              </Button>
              <Button icon={<ReloadOutlined />} className="bg-transparent border-white/10 text-gray-400 hover:text-[#00e396] hover:border-[#00e396] h-10" />
            </div>
          </motion.div>
              ))}
            </div>
          </div>
        ),
      },
    ],
    [
      t,
      lstmTotal,
      lstmList,
      lstmLoading,
      lstmError,
      refreshing,
      keyword,
      sortBy,
      sortOrder,
      sentimentLoading,
      sentimentHealth,
      fetchLstmList,
      handleSearch,
      handleTableChange,
      lstmColumns,
      current,
      pageSize,
      sentimentModels,
    ]
  );

  return (
    <div className="space-y-8">
      <div className="flex justify-between items-center mb-8">
        <div>
          <h1 className="text-3xl font-bold mb-2">{t('models.title')}</h1>
          <p className="text-gray-400">{t('models.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-3 text-xs text-gray-500">
            <span>{t('models.summary.sentimentStatus')}</span>
            <Tag
              color={sentimentHealth?.status === 'UP' ? 'processing' : 'default'}
              className="border-none flex items-center gap-1.5"
            >
              {sentimentHealth?.status === 'UP' ? (
                <SyncOutlined spin />
              ) : (
                <CloseCircleOutlined />
              )}
              <span className="ml-1 uppercase">
                {sentimentHealth?.status === 'UP' ? 'ONLINE' : 'OFFLINE'}
              </span>
            </Tag>
          </div>
          <Button
            type="default"
            size="small"
            icon={<ReloadOutlined spin={sentimentReloading} />}
            loading={sentimentReloading}
            className="border-white/20 text-gray-300 hover:text-[#00e396] hover:border-[#00e396]"
            onClick={handleReloadSentiment}
          >
            {t('models.sentiment.reloadButton')}
          </Button>
        </div>
      </div>
      <Tabs items={tabItems} className="models-tabs" />
      <Modal
        title={t('models.resultModal.title')}
        open={resultModalVisible}
        onCancel={() => setResultModalVisible(false)}
        footer={null}
        destroyOnClose
        width={520}
      >
        {resultModalLoading ? (
          <div className="py-8 flex justify-center">
            <Spin />
          </div>
        ) : resultModalData && (resultModalData.profitAmount != null || resultModalData.score != null) ? (
          <>
            {resultModalData.modelName && (
              <p className="text-gray-400 text-sm mb-3">
                {t('models.list.code')}: {resultModalData.modelName}
              </p>
            )}
            <Table
              size="small"
              columns={resultTableColumns}
              dataSource={resultTableData}
              pagination={false}
              rowKey="key"
            />
          </>
        ) : (
          <p className="text-gray-400 py-4">{t('models.resultModal.noData')}</p>
        )}
      </Modal>

      <Modal
        title={t('models.sentiment.testModalTitle')}
        open={sentimentTestModalVisible}
        onCancel={() => {
          setSentimentTestModalVisible(false);
          setSentimentTestText('');
          setSentimentTestResult(null);
        }}
        footer={null}
        destroyOnClose
        width={720}
      >
        <div className="space-y-4 flex flex-col items-center">
          <div className="w-full max-w-[640px]">
            <label className="block text-gray-400 text-sm mb-1">{t('models.sentiment.testModalPlaceholder')}</label>
            <TextArea
              value={sentimentTestText}
              onChange={(e) => setSentimentTestText(e.target.value)}
              placeholder={t('models.sentiment.testModalPlaceholder')}
              rows={6}
              className="bg-white/5 border-white/10 text-base px-4 py-3"
            />
          </div>
          <div className="flex gap-3">
            <Button
              type="default"
              icon={<ReloadOutlined />}
              disabled={SENTIMENT_TEST_SAMPLES.length === 0}
              className="border-white/20 text-gray-300 hover:text-[#00e396] hover:border-[#00e396]"
              onClick={() => {
                if (SENTIMENT_TEST_SAMPLES.length === 0) return;
                const nextIndex = (sentimentSampleIndex + 1) % SENTIMENT_TEST_SAMPLES.length;
                setSentimentSampleIndex(nextIndex);
                setSentimentTestText(SENTIMENT_TEST_SAMPLES[nextIndex].text);
              }}
            >
              换一段
            </Button>
            <Button
              type="primary"
              icon={<ExperimentOutlined />}
              loading={sentimentTestLoading}
              disabled={!sentimentTestText.trim()}
              className="bg-[#00e396] text-black border-none hover:bg-[#00c985]"
              onClick={async () => {
                const text = sentimentTestText.trim();
                if (!text) return;
                setSentimentTestLoading(true);
                setSentimentTestResult(null);
                try {
                  const res = await analyzeSentiment(text);
                  setSentimentTestResult(res);
                  if (res.success === false && res.message) message.error(res.message);
                } catch {
                  message.error(t('models.lstm.listError'));
                  setSentimentTestResult({ success: false, message: t('models.lstm.listError') });
                } finally {
                  setSentimentTestLoading(false);
                }
              }}
            >
              {t('models.sentiment.testModalSubmit')}
            </Button>
          </div>
          {sentimentTestResult && (
            <div className="rounded-xl border border-white/10 bg-white/5 p-4 space-y-2">
              <div className="text-sm font-medium text-gray-400">{t('models.sentiment.testModalResult')}</div>
              {sentimentTestResult.success === false ? (
                <p className="text-red-400 text-sm">{sentimentTestResult.message ?? t('models.lstm.listError')}</p>
              ) : (
                <>
                  <div className="flex flex-wrap gap-4 text-sm">
                    <span>
                      <span className="text-gray-500">{t('models.sentiment.testModalLabel')}:</span>{' '}
                      <Tag color={sentimentTestResult.label === 'positive' ? 'green' : sentimentTestResult.label === 'negative' ? 'red' : 'default'}>
                        {sentimentTestResult.label === 'positive' ? t('models.sentiment.labelPositive') : sentimentTestResult.label === 'negative' ? t('models.sentiment.labelNegative') : t('models.sentiment.labelNeutral')}
                      </Tag>
                    </span>
                    {sentimentTestResult.score != null && (
                      <span>
                        <span className="text-gray-500">{t('models.sentiment.testModalScore')}:</span>{' '}
                        <span className="font-mono text-[#00e396]">{(sentimentTestResult.score * 100).toFixed(1)}%</span>
                      </span>
                    )}
                    {sentimentTestResult.confidence != null && (
                      <span>
                        <span className="text-gray-500">{t('models.sentiment.testModalConfidence')}:</span>{' '}
                        <span className="font-mono">{(sentimentTestResult.confidence * 100).toFixed(1)}%</span>
                      </span>
                    )}
                  </div>
                  <p className="text-gray-300 text-sm pt-2 border-t border-white/5">
                    {t('models.sentiment.testModalExplanation')}{' '}
                    {sentimentTestResult.label === 'positive' ? t('models.sentiment.labelPositive') : sentimentTestResult.label === 'negative' ? t('models.sentiment.labelNegative') : t('models.sentiment.labelNeutral')}
                    {sentimentTestResult.confidence != null && `，${t('models.sentiment.testModalConfidence')} ${(sentimentTestResult.confidence * 100).toFixed(1)}%`}
                    {sentimentTestResult.modelLoaded === false && `（${t('models.sentiment.testModalFallback')}）`}
                    。
                  </p>
                </>
              )}
            </div>
          )}
        </div>
      </Modal>
    </div>
  );
};

export default Strategies;
