ALTER TABLE training_dataset_snapshot
    ADD COLUMN test_samples INT NOT NULL DEFAULT 0 AFTER val_samples,
    ADD COLUMN walk_forward_fold_count INT NOT NULL DEFAULT 0 AFTER test_samples,
    ADD COLUMN strict_isolation_fold_count INT NOT NULL DEFAULT 0 AFTER walk_forward_fold_count,
    ADD COLUMN split_policy_version VARCHAR(255) NULL AFTER strict_isolation_fold_count,
    ADD COLUMN data_source_version VARCHAR(255) NULL AFTER split_policy_version,
    ADD COLUMN train_start_date DATE NULL AFTER data_source_version,
    ADD COLUMN train_end_date DATE NULL AFTER train_start_date,
    ADD COLUMN validation_start_date DATE NULL AFTER train_end_date,
    ADD COLUMN validation_end_date DATE NULL AFTER validation_start_date,
    ADD COLUMN test_start_date DATE NULL AFTER validation_end_date,
    ADD COLUMN test_end_date DATE NULL AFTER test_start_date;
