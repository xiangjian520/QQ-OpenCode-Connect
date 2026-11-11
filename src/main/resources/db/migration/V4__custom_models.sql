CREATE TABLE IF NOT EXISTS opencode_custom_models (
    provider_id VARCHAR(96) NOT NULL,
    model_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    variants VARCHAR(255) NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (provider_id, model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
