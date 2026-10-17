-- AI_GENERATE_START -
CREATE TABLE sys_user_preference (
    user_id BIGINT NOT NULL,
    menu_order TEXT NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_sys_user_preference_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE
);
-- AI_GENERATE_END -
