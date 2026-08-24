package com.suplab.aether.memory.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A single shared memory in a data-portability export (GDPR Art. 20 / right-to-know).
 *
 * <p>A read-only, non-reinforcing snapshot of one memory — active or archived — for a team. It
 * carries the memory's content and metadata but not its raw embedding vector (an internal search
 * artefact, not portable data). The {@code archived} flag distinguishes an in-recall memory from a
 * faded one.</p>
 *
 * @param id               the memory's identifier
 * @param memoryType       the memory type
 * @param content          the memory content
 * @param visibility       the memory's visibility
 * @param strength         current strength
 * @param accessCount      lifetime access count
 * @param contributorCount distinct-contributor count
 * @param createdAt        when the memory was created
 * @param lastAccessedAt   when the memory was last accessed
 * @param archived         {@code true} if this snapshot came from the archive rather than active recall
 */
public record ExportedMemory(
        UUID id,
        MemoryType memoryType,
        String content,
        MemoryVisibility visibility,
        double strength,
        int accessCount,
        int contributorCount,
        Instant createdAt,
        Instant lastAccessedAt,
        boolean archived
) {
}
