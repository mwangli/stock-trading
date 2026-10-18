ALTER TABLE strategy_evaluation
    ADD COLUMN strategy_config_version VARCHAR(1000) NULL AFTER strategy_mode,
    ADD COLUMN model_version VARCHAR(1000) NULL AFTER strategy_config_version,
    ADD COLUMN sentiment_model_version VARCHAR(1000) NULL AFTER model_version;
