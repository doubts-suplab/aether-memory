package com.suplab.aether.memory.ports;

import com.suplab.aether.memory.domain.MemoryExport;
import com.suplab.aether.memory.domain.MemoryScope;

/**
 * Exports a team's shared memories for data portability (GDPR Art. 20 / right-to-know) — Memory's
 * export seam.
 *
 * <p>Produces a read-only {@link MemoryExport} snapshot spanning both active recall and the archive.
 * The export is <strong>non-reinforcing</strong>: reading memories for export must not raise their
 * strength or access count the way a retrieval does — it is a snapshot, not a use.</p>
 */
public interface MemoryExportPort {

    /**
     * Exports every active and archived memory for a team, without reinforcing any of them.
     *
     * @param scope the owning tenant + team (isolation boundary)
     * @return the export snapshot (active memories first, then archived)
     */
    MemoryExport exportTeam(MemoryScope scope);
}
