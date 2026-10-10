-- AI_GENERATE_START -
ALTER TABLE broker_order
    MODIFY COLUMN command_id VARCHAR(64) NULL,
    ADD COLUMN stock_code VARCHAR(16) NULL AFTER account_id,
    ADD COLUMN stock_name VARCHAR(64) NULL AFTER stock_code,
    ADD COLUMN market VARCHAR(16) NULL AFTER stock_name,
    ADD COLUMN side VARCHAR(16) NULL AFTER market,
    ADD COLUMN trade_date DATE NULL AFTER side,
    DROP INDEX uk_broker_order_broker_no,
    ADD UNIQUE KEY uk_broker_order_broker_no (
        broker_code, account_id, trade_date, broker_order_no
    ),
    ADD KEY idx_broker_order_stock_date (stock_code, trade_date);

ALTER TABLE broker_fill
    ADD COLUMN stock_name VARCHAR(64) NULL AFTER stock_code,
    ADD COLUMN market VARCHAR(16) NULL AFTER stock_name;
-- AI_GENERATE_END -
