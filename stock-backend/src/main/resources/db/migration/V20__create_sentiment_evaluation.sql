CREATE TABLE IF NOT EXISTS sentiment_evaluation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    evaluation_run_id VARCHAR(64) NOT NULL,
    model_version VARCHAR(128) NOT NULL,
    dataset_version VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    sample_count INT NOT NULL,
    correct_count INT NOT NULL,
    accuracy DOUBLE NOT NULL,
    macro_f1 DOUBLE NOT NULL,
    negative_recall DOUBLE NOT NULL,
    expected_calibration_error DOUBLE NOT NULL,
    p95_latency_ms DOUBLE NOT NULL,
    conclusion VARCHAR(500) NOT NULL,
    evaluated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sentiment_evaluation_run (evaluation_run_id),
    KEY idx_sentiment_evaluation_time (evaluated_at)
);

INSERT INTO job_config (
    job_name, cron_expression, status, description, bean_name, method_name, create_time, update_time
) VALUES (
    'sentiment-model-evaluation', '0 0 3 ? * SUN', 0,
    '使用人工标注股票新闻评估集验证真实情感模型，默认关闭',
    'sentimentEvaluationJob', 'runEvaluation', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
)
ON DUPLICATE KEY UPDATE
    cron_expression = VALUES(cron_expression),
    description = VALUES(description),
    bean_name = VALUES(bean_name),
    method_name = VALUES(method_name),
    update_time = CURRENT_TIMESTAMP(6);
