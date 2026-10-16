-- AI_GENERATE_START -
-- 删除已迁移任务在 Quartz 持久化表中的旧触发器和任务定义，避免启动时继续保留幽灵任务。
DELETE FROM QRTZ_CRON_TRIGGERS
WHERE TRIGGER_GROUP = 'stock-trading-jobs'
  AND TRIGGER_NAME IN ('historicalDataSync-trigger', 'broker-five-year-history-sync-trigger');
DELETE FROM QRTZ_TRIGGERS
WHERE TRIGGER_GROUP = 'stock-trading-jobs'
  AND TRIGGER_NAME IN ('historicalDataSync-trigger', 'broker-five-year-history-sync-trigger');
DELETE FROM QRTZ_JOB_DETAILS
WHERE JOB_GROUP = 'stock-trading-jobs'
  AND JOB_NAME IN ('historicalDataSync', 'broker-five-year-history-sync');

DELETE FROM job_config
WHERE job_name = 'historicalDataSync';

UPDATE job_config
SET description = '每日交易日线同步：先采集当前交易日，再回补最近三年缺失交易日',
    update_time = CURRENT_TIMESTAMP(6)
WHERE job_name = 'dailyStockDataSync';

UPDATE job_config
SET job_name = 'broker-history-backfill',
    description = '按供应商窗口回补最近三年券商历史委托和成交',
    method_name = 'syncHistoryBackfill',
    update_time = CURRENT_TIMESTAMP(6)
WHERE job_name = 'broker-five-year-history-sync';

UPDATE job_config SET module = 'data', sync_mode = 'INCREMENTAL'
WHERE job_name = 'dailyStockDataSync';
UPDATE job_config SET module = 'trading', sync_mode = 'BACKFILL'
WHERE job_name = 'broker-history-backfill';
-- AI_GENERATE_END -
