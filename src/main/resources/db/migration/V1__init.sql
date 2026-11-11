CREATE TABLE IF NOT EXISTS conversations (
    conversation_key VARCHAR(160) PRIMARY KEY,
    platform VARCHAR(32) NOT NULL,
    chat_type VARCHAR(16) NOT NULL,
    chat_id VARCHAR(64) NOT NULL,
    display_name VARCHAR(255),
    opencode_session_id VARCHAR(128),
    model_provider VARCHAR(96),
    model_id VARCHAR(255),
    model_variant VARCHAR(64),
    agent_profile VARCHAR(64) NOT NULL DEFAULT 'qq-chat',
    code_mode_until BIGINT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    INDEX idx_conversations_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_key VARCHAR(160) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    platform_message_id VARCHAR(128),
    opencode_message_id VARCHAR(128),
    sender_id VARCHAR(64),
    content LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE KEY uk_messages_platform_id (platform_message_id),
    INDEX idx_messages_conversation_time (conversation_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_key VARCHAR(160) NOT NULL,
    inbound_message_id VARCHAR(128) NOT NULL,
    text LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    error TEXT,
    created_at BIGINT NOT NULL,
    started_at BIGINT,
    completed_at BIGINT,
    UNIQUE KEY uk_jobs_inbound (inbound_message_id),
    INDEX idx_jobs_status_created (status, created_at),
    INDEX idx_jobs_conversation_status (conversation_key, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS settings (
    setting_key VARCHAR(128) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id VARCHAR(64),
    action VARCHAR(96) NOT NULL,
    target VARCHAR(255),
    detail TEXT,
    created_at BIGINT NOT NULL,
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
