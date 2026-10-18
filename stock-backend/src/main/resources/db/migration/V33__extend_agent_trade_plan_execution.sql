ALTER TABLE agent_trade_plan
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER updated_at,
    ADD COLUMN execution_trace_id VARCHAR(64) NULL AFTER decision_message,
    ADD COLUMN risk_code VARCHAR(64) NULL AFTER execution_trace_id,
    ADD COLUMN order_id VARCHAR(128) NULL AFTER risk_code,
    ADD COLUMN executed_at DATETIME(6) NULL AFTER order_id;
