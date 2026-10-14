-- AI_GENERATE_START
INSERT INTO job_config (
    job_name, cron_expression, status, description, bean_name, method_name, create_time, update_time
) VALUES (
    'strategy-auto-switch', '0 10 16 ? * MON-FRI', 0,
    '根据已落库市场参考序列和规则评估自动切换策略模式，默认关闭',
    'strategyAutoSwitchJob', 'runAutoSwitch', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
)
ON DUPLICATE KEY UPDATE
    cron_expression = VALUES(cron_expression),
    description = VALUES(description),
    bean_name = VALUES(bean_name),
    method_name = VALUES(method_name),
    update_time = CURRENT_TIMESTAMP(6);
-- AI_GENERATE_END
