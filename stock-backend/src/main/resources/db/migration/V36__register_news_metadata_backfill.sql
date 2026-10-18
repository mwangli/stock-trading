-- AI_GENERATE_START
INSERT INTO job_config (
    job_name, cron_expression, status, description, bean_name, method_name,
    module, sync_mode, create_time, update_time
) VALUES (
    'newsMetadataBackfill', '0 30 4 ? * MON-FRI', 1,
    '每日补全历史新闻缺失的事件类型和信息优先级',
    'dataSyncScheduler', 'backfillNewsMetadata',
    'data', 'BACKFILL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
)
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    bean_name = VALUES(bean_name),
    method_name = VALUES(method_name),
    module = VALUES(module),
    sync_mode = VALUES(sync_mode),
    update_time = CURRENT_TIMESTAMP(6);
-- AI_GENERATE_END
