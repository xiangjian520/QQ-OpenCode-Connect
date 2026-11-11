CREATE TABLE IF NOT EXISTS fun_user_stats (
    platform VARCHAR(32) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    display_name VARCHAR(255),
    points INT NOT NULL DEFAULT 0,
    sign_streak INT NOT NULL DEFAULT 0,
    signed_days INT NOT NULL DEFAULT 0,
    last_sign_day VARCHAR(10),
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (platform, user_id),
    INDEX idx_fun_user_points (points DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS fun_reminders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    platform VARCHAR(32) NOT NULL,
    chat_type VARCHAR(16) NOT NULL,
    chat_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    due_at BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at BIGINT NOT NULL,
    sent_at BIGINT,
    INDEX idx_fun_reminders_due (status, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS fun_polls (
    conversation_key VARCHAR(160) PRIMARY KEY,
    question VARCHAR(255) NOT NULL,
    options_json LONGTEXT NOT NULL,
    votes_json LONGTEXT NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
