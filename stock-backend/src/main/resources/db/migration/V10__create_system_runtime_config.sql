-- AI_GENERATE_START -
CREATE TABLE IF NOT EXISTS system_runtime_config (
    id BIGINT NOT NULL,
    scheduler_enabled BOOLEAN NOT NULL,
    data_collection_enabled BOOLEAN NOT NULL,
    news_collection_enabled BOOLEAN NOT NULL,
    startup_data_sync_enabled BOOLEAN NOT NULL,
    startup_history_sync_enabled BOOLEAN NOT NULL,
    startup_news_sync_enabled BOOLEAN NOT NULL,
    model_training_enabled BOOLEAN NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);
-- AI_GENERATE_END -
