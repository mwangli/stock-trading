CREATE TABLE IF NOT EXISTS automatic_trade_execution_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(96) NOT NULL,
    execution_type VARCHAR(16) NOT NULL,
    trade_date DATE NOT NULL,
    candidate_batch_id VARCHAR(64) NULL,
    status VARCHAR(16) NOT NULL,
    processed_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    message VARCHAR(500) NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auto_trade_batch_id (batch_id),
    UNIQUE KEY uk_auto_trade_idempotency (idempotency_key),
    KEY idx_auto_trade_date_type (trade_date, execution_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
