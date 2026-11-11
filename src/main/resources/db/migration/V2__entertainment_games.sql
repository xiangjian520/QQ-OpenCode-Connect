CREATE TABLE IF NOT EXISTS entertainment_games (
    conversation_key VARCHAR(160) PRIMARY KEY,
    game_type VARCHAR(32) NOT NULL,
    state_json LONGTEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    INDEX idx_entertainment_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
