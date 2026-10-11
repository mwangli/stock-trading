ALTER TABLE trade_performance_snapshot
    ADD COLUMN ranking_json LONGTEXT NULL AFTER worst_return_rate,
    ADD COLUMN daily_returns_json LONGTEXT NULL AFTER ranking_json,
    ADD COLUMN monthly_returns_json LONGTEXT NULL AFTER daily_returns_json;
