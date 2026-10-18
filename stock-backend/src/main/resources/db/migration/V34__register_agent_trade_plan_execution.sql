-- AI_GENERATE_START -
INSERT INTO job_config (
    job_name, cron_expression, status, description, bean_name, method_name,
    module, sync_mode, create_time, update_time
) VALUES (
    'agent-trade-plan-execution', '0 0/5 9-15 ? * MON-FRI', 0,
    '交易时段复核并执行已通过风控门禁的 Agent 交易计划，默认关闭',
    'tradePlanExecutionService', 'executePendingPlans',
    'trading', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
)
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    bean_name = VALUES(bean_name),
    method_name = VALUES(method_name),
    module = VALUES(module),
    sync_mode = VALUES(sync_mode),
    update_time = CURRENT_TIMESTAMP(6);
-- AI_GENERATE_END -