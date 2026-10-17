-- AI_GENERATE_START -
CREATE TABLE IF NOT EXISTS agent_session (
    session_id VARCHAR(64) NOT NULL,
    user_scope VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (session_id),
    KEY idx_agent_session_scope_time (user_scope, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_task (
    task_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    question TEXT NULL,
    result_summary TEXT NULL,
    evidence_summary TEXT NULL,
    error_message TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (task_id),
    KEY idx_agent_task_session_time (session_id, created_at),
    KEY idx_agent_task_status (status),
    CONSTRAINT fk_agent_task_session FOREIGN KEY (session_id) REFERENCES agent_session(session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_memory (
    memory_id VARCHAR(64) NOT NULL,
    user_scope VARCHAR(128) NOT NULL,
    memory_type VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    source_ref VARCHAR(500) NULL,
    confidence DECIMAL(8,6) NOT NULL,
    expires_at DATETIME(6) NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (memory_id),
    KEY idx_agent_memory_scope_status (user_scope, status),
    KEY idx_agent_memory_expire (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO job_config (
    job_name, cron_expression, status, description, bean_name, method_name, create_time, update_time
) VALUES (
    'agent-daily-research-report', '0 0 16 ? * MON-FRI', 0,
    'Agent 每日固定模板投研报告，默认关闭', 'agentApplicationService', 'runScheduledReport',
    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
) ON DUPLICATE KEY UPDATE job_name = VALUES(job_name);
-- AI_GENERATE_END -