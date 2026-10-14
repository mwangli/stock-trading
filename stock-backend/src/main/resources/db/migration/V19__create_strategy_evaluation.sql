CREATE TABLE IF NOT EXISTS strategy_evaluation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    evaluation_run_id VARCHAR(64) NOT NULL,
    strategy_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    window_start DATE NULL,
    window_end DATE NULL,
    validation_start DATE NULL,
    validation_end DATE NULL,
    sample_count INT NOT NULL,
    batch_count INT NOT NULL,
    net_return DOUBLE NULL,
    max_drawdown DOUBLE NULL,
    win_rate DOUBLE NULL,
    profit_loss_ratio DOUBLE NULL,
    stability_score DOUBLE NULL,
    out_of_sample BIT(1) NOT NULL,
    data_complete BIT(1) NOT NULL,
    sample_sufficient BIT(1) NOT NULL,
    conclusion VARCHAR(500) NOT NULL,
    calculated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_strategy_evaluation_run_mode (evaluation_run_id, strategy_mode),
    KEY idx_strategy_evaluation_time (calculated_at)
);

INSERT INTO job_config (
    job_name, cron_expression, status, description, bean_name, method_name, create_time, update_time
) VALUES (
    'strategy-evaluation', '0 30 18 ? * MON-FRI', 0,
    '基于历史候选与实际收益生成三种策略模式样本外评估，默认关闭',
    'strategyEvaluationJob', 'runEvaluation', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
)
ON DUPLICATE KEY UPDATE
    cron_expression = VALUES(cron_expression),
    description = VALUES(description),
    bean_name = VALUES(bean_name),
    method_name = VALUES(method_name),
    update_time = CURRENT_TIMESTAMP(6);
