-- AI_GENERATE_START -
INSERT INTO job_config (
    job_name, cron_expression, status, description, bean_name, method_name, create_time, update_time
) VALUES
    ('dailyModelPrediction', '0 15 6 ? * MON-FRI', 1,
     '工作日开盘前使用最近完整日线生成全市场预测',
     'dailyModelPredictionJob', 'runDailyPrediction', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('modelPredictionEvaluation', '0 30 18 ? * MON-FRI', 1,
     '收盘行情同步后回填所有待评估预测批次',
     'dailyModelPredictionJob', 'refreshPredictionEvaluations', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    cron_expression = VALUES(cron_expression),
    description = VALUES(description),
    bean_name = VALUES(bean_name),
    method_name = VALUES(method_name),
    update_time = CURRENT_TIMESTAMP(6);
-- AI_GENERATE_END -
