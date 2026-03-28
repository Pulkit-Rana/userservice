-- Manual schema cleanup for centralized auditing
-- Run this once on the target MySQL database after deploying code changes.
-- Adjust table names if your schema naming differs.

-- ─── 1. Remove legacy audit columns from domain tables ───────────────────────
ALTER TABLE users
    DROP COLUMN IF EXISTS created_at,
    DROP COLUMN IF EXISTS modified_at,
    DROP COLUMN IF EXISTS created_by,
    DROP COLUMN IF EXISTS modified_by,
    DROP COLUMN IF EXISTS is_deleted;

ALTER TABLE profiles
    DROP COLUMN IF EXISTS created_at,
    DROP COLUMN IF EXISTS modified_at,
    DROP COLUMN IF EXISTS created_by,
    DROP COLUMN IF EXISTS modified_by,
    DROP COLUMN IF EXISTS is_deleted;

-- Audit rows are immutable — no soft-delete needed there either
ALTER TABLE audit_history
    DROP COLUMN IF EXISTS is_deleted;

-- ─── 2. Enrich device_metadata with real-time tracking columns ───────────────
-- Hibernate ddl-auto=update adds NEW columns but won't rename/drop old ones.
-- Run these if the table already existed before this refactor.
ALTER TABLE device_metadata
    CHANGE COLUMN IF EXISTS meta_id id BIGINT NOT NULL AUTO_INCREMENT,
    ADD COLUMN IF NOT EXISTS device_id     VARCHAR(64)  NULL,
    ADD COLUMN IF NOT EXISTS ip_address    VARCHAR(45)  NULL,
    ADD COLUMN IF NOT EXISTS user_agent    VARCHAR(512) NULL,
    ADD COLUMN IF NOT EXISTS os            VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS browser       VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS first_seen_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    DROP COLUMN  IF EXISTS is_deleted;

-- ─── 3. Indexes for device lookup performance ─────────────────────────────────
CREATE INDEX IF NOT EXISTS ix_dm_user_id        ON device_metadata (user_id);
CREATE INDEX IF NOT EXISTS ix_dm_user_device_id ON device_metadata (user_id, device_id);

