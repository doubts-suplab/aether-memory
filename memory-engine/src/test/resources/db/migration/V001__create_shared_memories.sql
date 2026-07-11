-- V001 — Create shared_memories table
-- Team- and tenant-scoped shared memory (the AetherMemory analogue of Core's personal_memories).
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE shared_memories;

CREATE TABLE IF NOT EXISTS shared_memories (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         TEXT         NOT NULL,
    team_id           TEXT         NOT NULL,
    memory_type       TEXT         NOT NULL
                                   CHECK (memory_type IN ('EPISODIC', 'SEMANTIC', 'PROCEDURAL', 'EMOTIONAL')),
    content           TEXT         NOT NULL,
    visibility        TEXT         NOT NULL DEFAULT 'PRIVATE'
                                   CHECK (visibility IN ('PRIVATE', 'TENANT', 'FEDERATED')),
    strength          DOUBLE PRECISION NOT NULL DEFAULT 1.0
                                   CHECK (strength BETWEEN 0 AND 1),
    access_count      INTEGER      NOT NULL DEFAULT 0,
    contributor_count INTEGER      NOT NULL DEFAULT 1
                                   CHECK (contributor_count >= 1),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_accessed_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Primary team-scoped access path.
CREATE INDEX IF NOT EXISTS idx_shared_memories_scope
    ON shared_memories (tenant_id, team_id);

-- Type-filtered retrieval within a team.
CREATE INDEX IF NOT EXISTS idx_shared_memories_scope_type
    ON shared_memories (tenant_id, team_id, memory_type);

-- Federation fan-out: only FEDERATED rows are ever scanned cross-tenant.
CREATE INDEX IF NOT EXISTS idx_shared_memories_federatable
    ON shared_memories (memory_type)
    WHERE visibility = 'FEDERATED';
