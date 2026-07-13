-- V006 — Add per-tenant federation redaction depth to memory_policies
-- Governs how much of a tenant's FEDERATED content is exposed in a federated summary. Lower =
-- more redaction. Bounded by the platform maximum (280, mirroring FederatedMemory.MAX_SUMMARY_LENGTH).
-- Lock risk: MEDIUM (ALTER TABLE ADD COLUMN with a default; brief AccessExclusiveLock)
-- Rollback: ALTER TABLE memory_policies DROP COLUMN IF EXISTS federation_max_summary_length;

ALTER TABLE memory_policies
    ADD COLUMN IF NOT EXISTS federation_max_summary_length INTEGER NOT NULL DEFAULT 280
        CHECK (federation_max_summary_length BETWEEN 1 AND 280);
