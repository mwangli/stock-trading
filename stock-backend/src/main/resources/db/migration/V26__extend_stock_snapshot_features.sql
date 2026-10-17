ALTER TABLE stock_info
    ADD COLUMN volume DECIMAL(24,4) NULL,
    ADD COLUMN bid_price DECIMAL(10,3) NULL,
    ADD COLUMN ask_price DECIMAL(10,3) NULL,
    ADD COLUMN security_category VARCHAR(32) NULL,
    ADD COLUMN market_code VARCHAR(10) NULL,
    ADD COLUMN momentum_strength DECIMAL(12,6) NULL,
    ADD COLUMN volume_price_signal DECIMAL(12,6) NULL,
    ADD COLUMN turnover_anomaly DECIMAL(12,6) NULL,
    ADD COLUMN abnormal_volume_flag BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN bid_ask_spread DECIMAL(10,3) NULL,
    ADD COLUMN bid_ask_spread_rate DECIMAL(12,8) NULL;
