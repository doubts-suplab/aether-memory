package com.suplab.aether.memory.domain;

import java.time.Instant;
import java.util.List;

/**
 * A data-portability export of a team's shared memories (GDPR Art. 20 / right-to-know).
 *
 * <p>A read-only snapshot spanning both active recall and the archive, produced without reinforcing
 * anything it reads. The counts summarise the split; {@code memories} carries the individual
 * {@link ExportedMemory} snapshots.</p>
 *
 * @param tenantId      owning tenant
 * @param teamId        the exported team
 * @param activeCount   number of active memories in the export
 * @param archivedCount number of archived memories in the export
 * @param exportedAt    when the export was produced
 * @param memories      the exported memory snapshots (active first, then archived)
 */
public record MemoryExport(
        String tenantId,
        String teamId,
        int activeCount,
        int archivedCount,
        Instant exportedAt,
        List<ExportedMemory> memories
) {
    public MemoryExport {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (teamId == null || teamId.isBlank()) throw new IllegalArgumentException("teamId required");
        memories = memories == null ? List.of() : List.copyOf(memories);
        if (exportedAt == null) exportedAt = Instant.now();
    }
}
