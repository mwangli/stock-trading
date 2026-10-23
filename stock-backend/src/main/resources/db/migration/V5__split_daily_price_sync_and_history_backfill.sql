UPDATE job_config
SET description = '每日仅同步当前已确认交易日的全市场日线增量',
    sync_mode = 'INCREMENTAL',
    update_time = CURRENT_TIMESTAMP(6)
WHERE job_name = 'dailyStockDataSync';

INSERT IGNORE INTO job_config (
    job_name, cron_expression, status, description, module, sync_mode, create_time, update_time
) VALUES (
    'stockPriceHistoryBackfill',
    '0 0 3 ? * SAT',
    1,
    '每周回补最近三年已确认交易日的股票历史日线缺口',
    'data',
    'BACKFILL',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
);
