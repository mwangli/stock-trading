ALTER TABLE agent_trade_plan
    ADD COLUMN user_scope VARCHAR(128) NOT NULL DEFAULT 'system:legacy' AFTER plan_id;

ALTER TABLE agent_trade_plan
    DROP INDEX UK_6aqp7g4ruowoovw1rau8balb4,
    ADD UNIQUE KEY uk_agent_trade_plan_user_idempotency (user_scope, idempotency_key),
    ADD KEY idx_agent_trade_plan_user (user_scope);
