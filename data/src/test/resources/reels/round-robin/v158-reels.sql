-- Minimal schema-158 fixture for the round-robin upgrade test.
--
-- These are the real pre-158.sqm definitions (last changed by migrations 152/153/156/157), not
-- a current Schema.create: the database is opened at 158 and only 158.sqm is applied. Transport
-- URLs are real values so the migration test can prove that 158 -> 159 rewrites no payload.
CREATE TABLE reels_candidate(
    account_scope_id TEXT NOT NULL,
    source_id INTEGER NOT NULL,
    item_id TEXT NOT NULL,
    title TEXT,
    author TEXT,
    video_url TEXT NOT NULL,
    video_url_hd TEXT,
    poster_url TEXT,
    poster_url_vertical TEXT,
    web_url TEXT,
    duration_sec REAL,
    has_audio INTEGER NOT NULL DEFAULT 0,
    created_at_epoch_sec INTEGER,
    discovered_at INTEGER NOT NULL,
    last_validated_at INTEGER,
    availability TEXT NOT NULL DEFAULT 'available',
    retry_after INTEGER,
    resume_position_ms INTEGER,
    metadata_schema_version INTEGER NOT NULL,
    tags_json TEXT NOT NULL DEFAULT '[]',
    views_count INTEGER,
    likes_count INTEGER,
    is_author_verified INTEGER NOT NULL DEFAULT 0,
    captured_context_key TEXT,
    PRIMARY KEY (account_scope_id, source_id, item_id)
);

CREATE INDEX reels_candidate_scope_index ON reels_candidate(account_scope_id, source_id, discovered_at DESC);

CREATE TABLE reels_consumption(
    account_scope_id TEXT NOT NULL,
    source_id INTEGER NOT NULL,
    item_id TEXT NOT NULL,
    watched_qualified_at INTEGER,
    skipped_forward_at INTEGER,
    last_action_at INTEGER NOT NULL,
    last_surface TEXT,
    policy_version INTEGER NOT NULL,
    history_generation INTEGER NOT NULL,
    PRIMARY KEY (account_scope_id, source_id, item_id)
);

CREATE INDEX reels_consumption_scope_index ON reels_consumption(account_scope_id, source_id);

CREATE TABLE reels_hidden(
    source_id INTEGER NOT NULL,
    kind TEXT NOT NULL,
    hidden_value TEXT NOT NULL,
    label TEXT,
    hidden_at INTEGER NOT NULL,
    PRIMARY KEY (source_id, kind, hidden_value)
);

CREATE TABLE reels_for_you_state(
    account_scope_id TEXT NOT NULL PRIMARY KEY,
    history_generation INTEGER NOT NULL DEFAULT 0,
    policy_version INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE reels_session(
    account_scope_id TEXT NOT NULL,
    source_id INTEGER NOT NULL,
    context_key TEXT NOT NULL,
    session_id TEXT NOT NULL,
    owner_nonce TEXT,
    history_generation INTEGER NOT NULL,
    checkpoint_sequence INTEGER NOT NULL DEFAULT 0,
    active_item_id TEXT,
    active_position_ms INTEGER NOT NULL DEFAULT 0,
    active_completed INTEGER NOT NULL DEFAULT 0,
    active_title TEXT,
    active_author TEXT,
    active_duration_sec REAL,
    active_has_audio INTEGER NOT NULL DEFAULT 0,
    active_tags_json TEXT NOT NULL DEFAULT '[]',
    active_web_url TEXT,
    active_created_at_epoch_sec INTEGER,
    active_views_count INTEGER,
    active_likes_count INTEGER,
    active_is_author_verified INTEGER NOT NULL DEFAULT 0,
    active_metadata_schema_version INTEGER NOT NULL DEFAULT 2,
    updated_at_ms INTEGER NOT NULL,
    PRIMARY KEY (account_scope_id, source_id)
);

CREATE TABLE reels_session_queue(
    account_scope_id TEXT NOT NULL,
    source_id INTEGER NOT NULL,
    session_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    ordinal INTEGER NOT NULL,
    PRIMARY KEY (account_scope_id, source_id, session_id, item_id),
    UNIQUE (account_scope_id, source_id, session_id, ordinal),
    FOREIGN KEY (account_scope_id, source_id)
        REFERENCES reels_session(account_scope_id, source_id) ON DELETE CASCADE
);

INSERT INTO reels_for_you_state(account_scope_id, history_generation, policy_version)
VALUES ('acc:tg:1', 3, 1);

INSERT INTO reels_candidate(
    account_scope_id, source_id, item_id, title, author, video_url, video_url_hd,
    poster_url, poster_url_vertical, web_url, duration_sec, has_audio,
    created_at_epoch_sec, discovered_at, last_validated_at, availability,
    retry_after, resume_position_ms, metadata_schema_version, tags_json,
    views_count, likes_count, is_author_verified, captured_context_key
) VALUES (
    'acc:tg:1', 8529104719284712, 'tg_a1', 'A1 title', '@chat-a',
    'https://cdn.example/a1.mp4', 'https://cdn.example/a1.hd.mp4',
    'https://cdn.example/a1.jpg', 'https://cdn.example/a1v.jpg',
    'https://t.me/chat-a/1', 30.0, 1, 1700000000, 1000, 1100, 'available',
    NULL, 4000, 2, '["tag-a1"]', 10, 2, 1, 'v27_ctx'
), (
    'acc:tg:1', 8529104719284712, 'tg_a2', 'A2 title', '@chat-a',
    'https://cdn.example/a2.mp4', NULL,
    'https://cdn.example/a2.jpg', NULL,
    'https://t.me/chat-a/2', 20.0, 0, 1700000001, 2000, 2100, 'available',
    NULL, NULL, 2, '[]', 20, 3, 0, 'v27_ctx'
), (
    'acc:tg:1', 8529104719284712, 'tg_a3', 'A3 title', '@chat-a',
    'https://cdn.example/a3.mp4', NULL,
    NULL, NULL,
    'https://t.me/chat-a/3', 10.0, 0, 1700000002, 3000, 3100, 'available',
    NULL, NULL, 2, '[]', 30, 4, 0, 'v27_ctx'
), (
    'acc:tg:1', 8529104719284712, 'tg_b1', 'B1 title', '@chat-b',
    'https://cdn.example/b1.mp4', 'https://cdn.example/b1.hd.mp4',
    'https://cdn.example/b1.jpg', NULL,
    'https://t.me/chat-b/1', 40.0, 1, 1700000003, 4000, 4100, 'available',
    NULL, NULL, 2, '[]', 40, 5, 1, 'v27_ctx'
), (
    'acc:tg:1', 8529104719284712, 'tg_x1', 'A1 title', '@chat-a',
    'https://cdn.example/x1.mp4', NULL,
    NULL, NULL,
    'https://t.me/chat-a/x1', 5.0, 0, 1700000004, 5000, 5100, 'available',
    NULL, NULL, 2, '[]', 50, 6, 0, 'v27_ctx'
), (
    'acc:tg:1', 999999, 'other_1', 'Other title', '@other',
    'https://other.example/1.mp4', 'https://other.example/1.hd.mp4',
    'https://other.example/1.jpg', NULL,
    'https://other.example/1', 25.0, 1, 1700000005, 6000, 6100, 'available',
    NULL, 7000, 2, '[]', 60, 7, 1, 'v27_ctx_other'
);

INSERT INTO reels_consumption(
    account_scope_id, source_id, item_id, watched_qualified_at, skipped_forward_at,
    last_action_at, last_surface, policy_version, history_generation
) VALUES
    ('acc:tg:1', 8529104719284712, 'tg_x1', 111, NULL, 111, 'foryou', 1, 3),
    ('acc:tg:1', 999999, 'other_1', NULL, 222, 222, 'foryou', 1, 3);

INSERT INTO reels_hidden(source_id, kind, hidden_value, label, hidden_at)
VALUES (999999, 'item', 'other_hidden', 'Other hidden', 1000);

INSERT INTO reels_session(
    account_scope_id, source_id, context_key, session_id, owner_nonce,
    history_generation, checkpoint_sequence, active_item_id, active_position_ms,
    active_completed, active_title, active_author, active_duration_sec,
    active_has_audio, active_tags_json, active_web_url, active_created_at_epoch_sec,
    active_views_count, active_likes_count, active_is_author_verified,
    active_metadata_schema_version, updated_at_ms
) VALUES (
    'acc:tg:1', 8529104719284712, 'v27_ctx', 'legacy-session', 'legacy-owner',
    3, 7, 'tg_a1', 5000,
    0, 'A1 title', '@chat-a', 30.0,
    1, '["tag-a1"]', 'https://t.me/chat-a/1', 1700000000,
    10, 2, 1,
    2, 1234
);

INSERT INTO reels_session_queue(account_scope_id, source_id, session_id, item_id, ordinal)
VALUES
    ('acc:tg:1', 8529104719284712, 'legacy-session', 'tg_a2', 1),
    ('acc:tg:1', 8529104719284712, 'legacy-session', 'tg_a3', 2),
    ('acc:tg:1', 8529104719284712, 'legacy-session', 'tg_b1', 3);
