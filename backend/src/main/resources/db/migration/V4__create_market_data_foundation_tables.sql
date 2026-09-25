-- AI_GENERATE_START -
CREATE TABLE IF NOT EXISTS historical_stock_universe (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stock_code VARCHAR(16) NOT NULL,
    stock_name VARCHAR(64) NULL,
    market VARCHAR(16) NOT NULL,
    board_code VARCHAR(32) NULL,
    industry_code VARCHAR(64) NULL,
    listed_date DATE NULL,
    delisted_date DATE NULL,
    st_status VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    source VARCHAR(64) NOT NULL,
    source_version VARCHAR(128) NOT NULL,
    captured_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_universe_code_effective_source (stock_code, effective_from, source),
    KEY idx_universe_effective_range (effective_from, effective_to),
    KEY idx_universe_market_board (market, board_code, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS trade_calendar (
    id BIGINT NOT NULL AUTO_INCREMENT,
    market VARCHAR(16) NOT NULL,
    trade_date DATE NOT NULL,
    trading_day BOOLEAN NOT NULL,
    previous_trading_day DATE NULL,
    next_trading_day DATE NULL,
    source VARCHAR(64) NOT NULL,
    source_version VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_calendar_market_date (market, trade_date),
    KEY idx_trade_calendar_date_trading (trade_date, trading_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- AI_GENERATE_END -
