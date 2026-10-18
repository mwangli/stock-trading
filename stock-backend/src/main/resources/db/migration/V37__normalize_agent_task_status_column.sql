-- AI_GENERATE_START -
-- 历史数据库可能在 V27 创建前已存在旧的 ENUM status，IF NOT EXISTS 不会修正列定义。
ALTER TABLE agent_task
    MODIFY COLUMN status VARCHAR(32) NOT NULL;
-- AI_GENERATE_END -
