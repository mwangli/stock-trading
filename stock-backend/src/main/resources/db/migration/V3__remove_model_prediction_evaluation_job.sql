DELETE FROM job_config
WHERE job_name = 'modelPredictionEvaluation';

UPDATE job_config
SET description = '工作日开盘前扫描并消费预测与评估补缺队列',
    sync_mode = 'BACKFILL',
    update_time = CURRENT_TIMESTAMP(6)
WHERE job_name = 'dailyModelPrediction';
