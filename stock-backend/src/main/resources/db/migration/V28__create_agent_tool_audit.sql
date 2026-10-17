-- AI_GENERATE_START -
CREATE TABLE IF NOT EXISTS agent_tool_call (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trace_id VARCHAR(64) NULL,
    session_id VARCHAR(64) NULL,
    task_id VARCHAR(64) NULL,
    tool_name VARCHAR(128) NOT NULL,
    tool_version VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(64) NULL,
    parameter_summary VARCHAR(2000) NULL,
    result_summary VARCHAR(2000) NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_agent_tool_call_task_time (task_id, started_at),
    KEY idx_agent_tool_call_tool_time (tool_name, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- AI_GENERATE_END -
