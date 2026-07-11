-- V002 — Add pgvector embedding column to shared_memories
-- Lock risk: MEDIUM (ALTER TABLE on existing table; AccessExclusiveLock during ADD COLUMN)
-- The column is nullable initially so the migration completes without a table scan backfill.
-- Rollback: ALTER TABLE shared_memories DROP COLUMN IF EXISTS embedding;
--           DROP INDEX IF EXISTS idx_shared_memories_embedding;

CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE shared_memories
    ADD COLUMN IF NOT EXISTS embedding vector(384);

-- IVFFlat index for approximate cosine similarity search.
-- lists=100 is sized for ~1M vectors per the pgvector recommendation.
-- Revisit when total embeddings approach that threshold.
CREATE INDEX IF NOT EXISTS idx_shared_memories_embedding
    ON shared_memories USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
