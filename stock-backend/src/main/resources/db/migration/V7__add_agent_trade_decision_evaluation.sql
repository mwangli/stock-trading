ALTER TABLE trade_decision_context
    ADD COLUMN base_trade_date DATE NULL AFTER stock_code,
    ADD COLUMN target_trade_date DATE NULL AFTER base_trade_date,
    ADD COLUMN baseline_eligible BIT(1) NULL AFTER target_trade_date,
    ADD COLUMN baseline_close_price DECIMAL(20, 6) NULL AFTER baseline_eligible,
    ADD KEY idx_trade_decision_context_target_date (target_trade_date, candidate_batch_id);

ALTER TABLE automatic_trade_execution_batch
    ADD KEY idx_auto_trade_candidate_batch (candidate_batch_id, started_at);

CREATE TABLE IF NOT EXISTS trade_decision_evaluation (
    evaluation_id VARCHAR(64) NOT NULL,
    decision_id VARCHAR(64) NOT NULL,
    context_id VARCHAR(64) NOT NULL,
    candidate_batch_id VARCHAR(128) NOT NULL,
    stock_code VARCHAR(32) NOT NULL,
    base_trade_date DATE NULL,
    target_trade_date DATE NULL,
    baseline_eligible BIT(1) NULL,
    agent_recommendation VARCHAR(16) NOT NULL,
    baseline_close_price DECIMAL(20, 6) NULL,
    target_open_price DECIMAL(20, 6) NULL,
    target_high_price DECIMAL(20, 6) NULL,
    target_low_price DECIMAL(20, 6) NULL,
    target_close_price DECIMAL(20, 6) NULL,
    close_return_rate DECIMAL(16, 8) NULL,
    max_favorable_rate DECIMAL(16, 8) NULL,
    max_adverse_rate DECIMAL(16, 8) NULL,
    skip_opportunity_cost_rate DECIMAL(16, 8) NULL,
    risk_avoidance_benefit_rate DECIMAL(16, 8) NULL,
    counterfactual_outcome VARCHAR(32) NOT NULL,
    market_data_status VARCHAR(32) NOT NULL,
    execution_batch_id VARCHAR(64) NULL,
    execution_batch_status VARCHAR(16) NULL,
    execution_processed_count INT NULL,
    execution_success_count INT NULL,
    stock_execution_known BIT(1) NOT NULL,
    evaluated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (evaluation_id),
    UNIQUE KEY uk_trade_decision_evaluation_decision (decision_id),
    KEY idx_trade_decision_evaluation_batch (candidate_batch_id, evaluated_at),
    KEY idx_trade_decision_evaluation_status (market_data_status, target_trade_date),
    CONSTRAINT fk_trade_decision_evaluation_decision
        FOREIGN KEY (decision_id) REFERENCES agent_trade_decision (decision_id),
    CONSTRAINT fk_trade_decision_evaluation_context
        FOREIGN KEY (context_id) REFERENCES trade_decision_context (context_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO job_config (
    job_name, cron_expression, status, description, module, sync_mode, create_time, update_time
) VALUES
    ('agentTradeDecisionShadow', '0 40 6 ? * MON-FRI', 1,
     '选股完成后对当前候选批次生成不影响真实交易的 Agent SHADOW 判断',
     'strategy', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('agentTradeDecisionEvaluation', '0 10 20 ? * MON-FRI', 1,
     '日线同步完成后回写 Agent SHADOW 判断的市场路径与反事实评估',
     'strategy', 'BACKFILL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
