-- AI_GENERATE_START -
ALTER TABLE agent_task
    ADD COLUMN result_type VARCHAR(32) NULL AFTER result_summary,
    ADD COLUMN structured_result TEXT NULL AFTER result_type,
    ADD COLUMN evidence_refs TEXT NULL AFTER evidence_summary,
    ADD COLUMN uncertainty TEXT NULL AFTER evidence_refs;
-- AI_GENERATE_END -