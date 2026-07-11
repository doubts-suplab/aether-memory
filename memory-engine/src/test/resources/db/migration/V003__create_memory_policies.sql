-- V003 — Create memory_policies table
-- One configurable governance policy per tenant. Tenants with no row fall back to code defaults
-- (MemoryPolicy.defaults), so this table stores only explicit overrides.
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE memory_policies;

CREATE TABLE IF NOT EXISTS memory_policies (
    tenant_id               TEXT         PRIMARY KEY,
    decay_rate              DOUBLE PRECISION NOT NULL DEFAULT 0.01
                                         CHECK (decay_rate BETWEEN 0 AND 1),
    decay_after_days        INTEGER      NOT NULL DEFAULT 7
                                         CHECK (decay_after_days >= 0),
    reinforcement_increment DOUBLE PRECISION NOT NULL DEFAULT 0.1
                                         CHECK (reinforcement_increment BETWEEN 0 AND 1),
    archive_threshold       DOUBLE PRECISION NOT NULL DEFAULT 0.1
                                         CHECK (archive_threshold BETWEEN 0 AND 1),
    retention_days          INTEGER      NOT NULL DEFAULT 90
                                         CHECK (retention_days >= 0),
    federation_enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
