CREATE TABLE IF NOT EXISTS job_execution_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(64) NOT NULL,
    job_name VARCHAR(128) NOT NULL,
    active_lock_key VARCHAR(128) NULL,
    trigger_source VARCHAR(32) NOT NULL,
    triggered_by VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    scheduled_time DATETIME(6) NULL,
    queued_time DATETIME(6) NOT NULL,
    started_time DATETIME(6) NULL,
    finished_time DATETIME(6) NULL,
    duration_ms BIGINT NULL,
    error_type VARCHAR(256) NULL,
    error_message TEXT NULL,
    result_summary TEXT NULL,
    host_name VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_execution_run_id (run_id),
    UNIQUE KEY uk_job_execution_active_lock (active_lock_key),
    KEY idx_job_execution_job_time (job_name, created_at),
    KEY idx_job_execution_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @job_config_module_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'job_config' AND column_name = 'module'
);
SET @job_config_add_module = IF(@job_config_module_exists = 0,
    'ALTER TABLE job_config ADD COLUMN module VARCHAR(32) NULL', 'SELECT 1');
PREPARE job_config_module_stmt FROM @job_config_add_module;
EXECUTE job_config_module_stmt;
DEALLOCATE PREPARE job_config_module_stmt;

SET @job_config_sync_mode_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'job_config' AND column_name = 'sync_mode'
);
SET @job_config_add_sync_mode = IF(@job_config_sync_mode_exists = 0,
    'ALTER TABLE job_config ADD COLUMN sync_mode VARCHAR(32) NULL', 'SELECT 1');
PREPARE job_config_sync_mode_stmt FROM @job_config_add_sync_mode;
EXECUTE job_config_sync_mode_stmt;
DEALLOCATE PREPARE job_config_sync_mode_stmt;

UPDATE job_config SET module = 'data', sync_mode = 'FULL'
WHERE job_name IN ('stockListSync', 'historicalDataSync', 'newsFullSync');
UPDATE job_config SET module = 'data', sync_mode = 'INCREMENTAL'
WHERE job_name IN ('dailyStockDataSync', 'newsDailySync');
UPDATE job_config SET module = 'data', sync_mode = 'BACKFILL'
WHERE job_name = 'tradeCalendarMonthlySync';
UPDATE job_config SET module = 'model', sync_mode = 'INCREMENTAL'
WHERE job_name IN ('lstmOfflineTraining', 'dailyModelPrediction', 'modelPredictionEvaluation', 'sentiment-model-evaluation');
UPDATE job_config SET module = 'strategy', sync_mode = 'FULL'
WHERE job_name IN ('stockSelection', 'strategy-auto-switch', 'strategy-evaluation', 'daily-five-year-baseline-backtest');
UPDATE job_config SET module = 'trading', sync_mode = 'INCREMENTAL'
WHERE job_name IN ('realTradingAutoBuy', 'realTradingT1Exit', 'broker-current-facts-sync',
                   'broker-five-year-history-sync', 'daily-trade-performance-snapshot');
