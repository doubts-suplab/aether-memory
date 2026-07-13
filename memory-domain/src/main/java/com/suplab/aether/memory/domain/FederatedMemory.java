package com.suplab.aether.memory.domain;

/**
 * A privacy-preserving projection of a {@link SharedMemory}, safe to return across the
 * federation boundary.
 *
 * <p>Federation is deliberately lossy. A federated result never carries the owning
 * {@code teamId}, contributor identities, or the raw memory ID — only a coarse
 * {@code provenance} label, the memory type, a length-bounded {@code summary}, and the
 * consolidated {@code strength}. Callers get useful signal without the source tenant leaking
 * who knows what.</p>
 *
 * @param type       the memory type
 * @param summary    a length-bounded, privacy-preserving excerpt of the content
 * @param strength   the consolidated strength of the source memory (0–1)
 * @param provenance a coarse origin label (e.g. the source tenant id, or {@code "FEDERATED"})
 */
public record FederatedMemory(
        MemoryType type,
        String summary,
        double strength,
        String provenance
) {
    /** Maximum characters of source content exposed in a federated summary. */
    public static final int MAX_SUMMARY_LENGTH = 280;

    public FederatedMemory {
        if (type == null) throw new IllegalArgumentException("type required");
        if (summary == null) summary = "";
        if (strength < 0 || strength > 1) throw new IllegalArgumentException("strength must be 0-1");
        if (provenance == null || provenance.isBlank()) provenance = "FEDERATED";
    }

    /**
     * Projects a {@link SharedMemory} into a federated result at full redaction depth
     * ({@link #MAX_SUMMARY_LENGTH}).
     *
     * @param memory     the source memory (must be {@link MemoryVisibility#FEDERATED})
     * @param provenance the coarse origin label to expose (never the raw {@code teamId})
     */
    public static FederatedMemory from(SharedMemory memory, String provenance) {
        return from(memory, provenance, MAX_SUMMARY_LENGTH);
    }

    /**
     * Projects a {@link SharedMemory} into a federated result, truncating content to the source
     * tenant's configured redaction depth and reducing provenance to the supplied label.
     *
     * @param memory     the source memory (must be {@link MemoryVisibility#FEDERATED})
     * @param provenance the coarse origin label to expose (never the raw {@code teamId})
     * @param maxLength  redaction depth — clamped to {@code [1, MAX_SUMMARY_LENGTH]}
     */
    public static FederatedMemory from(SharedMemory memory, String provenance, int maxLength) {
        int limit = Math.max(1, Math.min(maxLength, MAX_SUMMARY_LENGTH));
        var content = memory.content();
        var summary = content.length() <= limit
                ? content
                : content.substring(0, limit - 1) + "…";
        return new FederatedMemory(memory.type(), summary, memory.strength(), provenance);
    }
}
