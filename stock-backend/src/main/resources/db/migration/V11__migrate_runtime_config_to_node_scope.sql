-- AI_GENERATE_START -
DROP TABLE IF EXISTS system_runtime_config_v11;

CREATE TABLE system_runtime_config_v11 (
    node_id VARCHAR(128) NOT NULL,
    scheduler_enabled BOOLEAN NOT NULL,
    data_collection_enabled BOOLEAN NOT NULL,
    news_collection_enabled BOOLEAN NOT NULL,
    startup_data_sync_enabled BOOLEAN NOT NULL,
    startup_history_sync_enabled BOOLEAN NOT NULL,
    startup_news_sync_enabled BOOLEAN NOT NULL,
    model_training_enabled BOOLEAN NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (node_id)
);

INSERT INTO system_runtime_config_v11 (
    node_id,
    scheduler_enabled,
    data_collection_enabled,
    news_collection_enabled,
    startup_data_sync_enabled,
    startup_history_sync_enabled,
    startup_news_sync_enabled,
    model_training_enabled,
    updated_at
)
SELECT
    'stock-backend',
    scheduler_enabled,
    data_collection_enabled,
    news_collection_enabled,
    startup_data_sync_enabled,
    startup_history_sync_enabled,
    startup_news_sync_enabled,
    model_training_enabled,
    updated_at
FROM system_runtime_config
ORDER BY updated_at DESC
LIMIT 1;

DROP TABLE system_runtime_config;
RENAME TABLE system_runtime_config_v11 TO system_runtime_config;
-- AI_GENERATE_END -
