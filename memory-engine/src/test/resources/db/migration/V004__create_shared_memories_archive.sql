-- V004 — Create shared_memories_archive table
-- Faded shared memories (strength below the tenant's archive threshold) are moved here, not
-- deleted: forgetting is graceful, and archived memories keep their embeddings for potential
-- restore or retention-window purge.
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE shared_memories_archive;

CREATE TABLE IF NOT EXISTS shared_memories_archive (
    id                UUID         PRIMARY KEY,
    tenant_id         TEXT         NOT NULL,
    team_id           TEXT         NOT NULL,
    memory_type       TEXT         NOT NULL
                                   CHECK (memory_type IN ('EPISODIC', 'SEMANTIC', 'PROCEDURAL', 'EMOTIONAL')),
    content           TEXT         NOT NULL,
    visibility        TEXT         NOT NULL
                                   CHECK (visibility IN ('PRIVATE', 'TENANT', 'FEDERATED')),
    embedding         vector(384),
    strength          DOUBLE PRECISION NOT NULL,
    access_count      INTEGER      NOT NULL,
    contributor_count INTEGER      NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    last_accessed_at  TIMESTAMPTZ  NOT NULL,
    archived_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shared_memories_archive_scope
    ON shared_memories_archive (tenant_id, team_id, archived_at DESC);
