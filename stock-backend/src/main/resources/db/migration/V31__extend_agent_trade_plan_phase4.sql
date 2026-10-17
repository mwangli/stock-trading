-- AI_GENERATE_START -
ALTER TABLE agent_trade_plan
    ADD COLUMN prediction_batch_id VARCHAR(128) NULL AFTER candidate_batch_id,
    ADD COLUMN target_trade_date DATE NULL AFTER updated_at,
    ADD COLUMN strategy_version VARCHAR(64) NULL AFTER target_trade_date,
    ADD COLUMN price_constraint TEXT NULL AFTER strategy_version,
    ADD COLUMN reason TEXT NULL AFTER price_constraint,
    ADD COLUMN risk_warnings TEXT NULL AFTER reason,
    ADD COLUMN uncertainty TEXT NULL AFTER risk_warnings;
-- AI_GENERATE_END -
