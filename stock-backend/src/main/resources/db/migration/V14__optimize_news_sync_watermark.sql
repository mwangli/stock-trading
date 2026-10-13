ALTER TABLE stock_info
    ADD COLUMN news_last_attempt_at DATETIME(6) NULL,
    ADD COLUMN news_last_success_at DATETIME(6) NULL,
    ADD COLUMN news_last_publish_time DATETIME(6) NULL,
    ADD COLUMN news_sync_status VARCHAR(32) NOT NULL DEFAULT 'NEVER_SYNCED',
    ADD COLUMN news_sync_batch_id VARCHAR(64) NULL,
    ADD COLUMN news_sync_error_code VARCHAR(128) NULL,
    ADD COLUMN news_consecutive_no_change_count INT NOT NULL DEFAULT 0,
    ADD COLUMN news_next_sync_at DATETIME(6) NULL;

CREATE INDEX idx_stock_info_news_sync_due
    ON stock_info (news_sync_status, news_next_sync_at);

UPDATE job_config
SET cron_expression = '0 0 5 ? * MON-FRI',
    description = '每日增量新闻采集：每交易日 5:00 同步股票最新新闻和公告',
    update_time = CURRENT_TIMESTAMP(6)
WHERE job_name = 'newsDailySync';
