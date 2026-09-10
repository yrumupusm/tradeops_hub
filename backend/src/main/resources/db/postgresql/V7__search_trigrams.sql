CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE UNIQUE INDEX uq_app_users_username_lower ON app_users(LOWER(username));
CREATE INDEX idx_bis_names_trgm ON bis_search_names USING GIN(normalized gin_trgm_ops);
CREATE INDEX idx_bis_compact_trgm ON bis_search_names USING GIN(compact gin_trgm_ops);
CREATE INDEX idx_bis_tokens_trgm ON bis_search_names USING GIN(tokenized gin_trgm_ops);
