-- AI_GENERATE_START -
CREATE TABLE IF NOT EXISTS trade_performance_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    broker_code VARCHAR(32) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    as_of_date DATE NOT NULL,
    cumulative_profit DECIMAL(20, 4) NOT NULL DEFAULT 0,
    cumulative_return_rate DECIMAL(18, 8) NOT NULL DEFAULT 0,
    daily_profit DECIMAL(20, 4) NOT NULL DEFAULT 0,
    daily_return_rate DECIMAL(18, 8) NOT NULL DEFAULT 0,
    monthly_profit DECIMAL(20, 4) NOT NULL DEFAULT 0,
    monthly_return_rate DECIMAL(18, 8) NOT NULL DEFAULT 0,
    total_trade_count BIGINT NOT NULL DEFAULT 0,
    closed_trade_count BIGINT NOT NULL DEFAULT 0,
    winning_trade_count BIGINT NOT NULL DEFAULT 0,
    losing_trade_count BIGINT NOT NULL DEFAULT 0,
    win_rate DECIMAL(18, 8) NOT NULL DEFAULT 0,
    average_return_rate DECIMAL(18, 8) NOT NULL DEFAULT 0,
    best_return_rate DECIMAL(18, 8) NOT NULL DEFAULT 0,
    worst_return_rate DECIMAL(18, 8) NOT NULL DEFAULT 0,
    generated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_performance_account_date (broker_code, account_id, as_of_date),
    KEY idx_trade_performance_latest (broker_code, account_id, as_of_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- AI_GENERATE_END -
