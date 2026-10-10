SET @drop_job_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'job_config'
    ),
    'DELETE FROM job_config WHERE job_name = ''strategy-circuit-breaker-recovery''',
    'SELECT 1'
);
PREPARE drop_job_statement FROM @drop_job_sql;
EXECUTE drop_job_statement;
DEALLOCATE PREPARE drop_job_statement;

SET @drop_consecutive_threshold_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'strategy_config'
          AND column_name = 'consecutive_failure_threshold'
    ),
    'ALTER TABLE strategy_config DROP COLUMN consecutive_failure_threshold',
    'SELECT 1'
);
PREPARE drop_consecutive_threshold_statement FROM @drop_consecutive_threshold_sql;
EXECUTE drop_consecutive_threshold_statement;
DEALLOCATE PREPARE drop_consecutive_threshold_statement;

SET @drop_daily_threshold_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'strategy_config'
          AND column_name = 'daily_failure_threshold'
    ),
    'ALTER TABLE strategy_config DROP COLUMN daily_failure_threshold',
    'SELECT 1'
);
PREPARE drop_daily_threshold_statement FROM @drop_daily_threshold_sql;
EXECUTE drop_daily_threshold_statement;
DEALLOCATE PREPARE drop_daily_threshold_statement;

SET @drop_recovery_minutes_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'strategy_config'
          AND column_name = 'circuit_breaker_recovery_minutes'
    ),
    'ALTER TABLE strategy_config DROP COLUMN circuit_breaker_recovery_minutes',
    'SELECT 1'
);
PREPARE drop_recovery_minutes_statement FROM @drop_recovery_minutes_sql;
EXECUTE drop_recovery_minutes_statement;
DEALLOCATE PREPARE drop_recovery_minutes_statement;
