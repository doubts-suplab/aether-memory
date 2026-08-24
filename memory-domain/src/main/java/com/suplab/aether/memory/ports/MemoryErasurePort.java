package com.suplab.aether.memory.ports;

import com.suplab.aether.memory.domain.MemoryErasureResult;
import com.suplab.aether.memory.domain.MemoryScope;

/**
 * Erases a team's shared memories on a right-to-erasure request (GDPR Art. 17) — Memory's
 * data-subject seam.
 *
 * <p>Removes a team's memories from both the active store and the archive in one governed operation,
 * scoped to the tenant + team, and reports the counts removed from each. Erasure is idempotent — a
 * team with nothing stored is a no-op that returns zero counts.</p>
 *
 * <p>Unlike the lifecycle's decay/archive (which move, never delete beyond retention), erasure
 * genuinely deletes on request and must report what it removed, so the deletion is auditable.</p>
 */
public interface MemoryErasurePort {

    /**
     * Erases every active and archived memory for a team.
     *
     * @param scope the owning tenant + team (isolation boundary)
     * @return the counts removed from the active store and the archive
     */
    MemoryErasureResult eraseTeam(MemoryScope scope);
}
