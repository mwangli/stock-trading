ALTER TABLE trade_calendar
    ADD COLUMN day_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' AFTER trading_day,
    ADD COLUMN closure_type VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' AFTER day_status,
    ADD COLUMN verification_status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED' AFTER closure_type,
    ADD COLUMN source_fetched_at DATETIME(6) NULL AFTER source_version,
    ADD COLUMN confirmed_at DATETIME(6) NULL AFTER source_fetched_at;

UPDATE trade_calendar
SET day_status = CASE WHEN trading_day = TRUE THEN 'OPEN' ELSE 'CLOSED' END,
    closure_type = CASE WHEN trading_day = TRUE THEN 'NONE' ELSE 'UNKNOWN' END,
    verification_status = 'CONFIRMED',
    confirmed_at = COALESCE(updated_at, created_at)
WHERE source IS NOT NULL;

CREATE INDEX idx_trade_calendar_market_status_date
    ON trade_calendar (market, verification_status, day_status, trade_date);

INSERT INTO job_config (
    job_name, cron_expression, status, description, bean_name, method_name, create_time, update_time
) VALUES (
    'tradeCalendarMonthlySync', '0 10 4 1 * ?', 1,
    '每月回补交易日历缺口、确认公开休市安排并维持未来一年滚动窗口',
    'tradingCalendarMaintenanceService', 'maintainRollingCalendar',
    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
)
ON DUPLICATE KEY UPDATE
    cron_expression = VALUES(cron_expression),
    description = VALUES(description),
    bean_name = VALUES(bean_name),
    method_name = VALUES(method_name),
    update_time = CURRENT_TIMESTAMP(6);
