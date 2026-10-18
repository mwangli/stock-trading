-- AI_GENERATE_START -
UPDATE job_config
SET cron_expression = '0 30 20 ? * *',
    description = '每日收盘行情同步后回填所有待评估预测批次，周末补偿未完成批次',
    update_time = CURRENT_TIMESTAMP(6)
WHERE job_name = 'modelPredictionEvaluation';
-- AI_GENERATE_END -
