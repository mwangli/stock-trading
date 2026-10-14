CREATE TABLE IF NOT EXISTS strategy_runtime_state (
    state_key VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    current_mode VARCHAR(32) NOT NULL DEFAULT 'BALANCED',
    disabled_indicators_json TEXT NOT NULL,
    last_switch_time DATETIME(3) NULL,
    last_switch_reason VARCHAR(500) NULL,
    config_version VARCHAR(64) NOT NULL DEFAULT '1',
    auto_switch_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_auto_switch_time DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (state_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO strategy_runtime_state (
    state_key, enabled, current_mode, disabled_indicators_json,
    last_switch_reason, config_version, auto_switch_enabled, updated_at
) VALUES (
    'strategy:state', TRUE, 'BALANCED', '[]',
    '初始化', '1', FALSE, CURRENT_TIMESTAMP(3)
)
ON DUPLICATE KEY UPDATE state_key = VALUES(state_key);
