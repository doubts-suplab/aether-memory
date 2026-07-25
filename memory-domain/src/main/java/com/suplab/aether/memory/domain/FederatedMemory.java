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
     * Projects a {@link SharedMemory} into a federated result, truncating content to
     * {@link #MAX_SUMMARY_LENGTH} characters and reducing provenance to the supplied label.
     *
     * @param memory     the source memory (must be {@link MemoryVisibility#FEDERATED})
     * @param provenance the coarse origin label to expose (never the raw {@code teamId})
     */
    public static FederatedMemory from(SharedMemory memory, String provenance) {
        return from(memory, provenance, MAX_SUMMARY_LENGTH);
    }

    /**
     * Projects a {@link SharedMemory} into a federated result at a caller-supplied <em>redaction
     * depth</em> — the maximum characters of content the owning tenant permits to leak (its
     * {@link MemoryPolicy#federationSummaryChars()}). The depth is clamped to
     * {@link #MAX_SUMMARY_LENGTH}; a depth of 0 exposes no content at all.
     *
     * @param memory     the source memory (must be {@link MemoryVisibility#FEDERATED})
     * @param provenance the coarse origin label to expose (never the raw {@code teamId})
     * @param maxChars   the owning tenant's redaction depth
     */
    public static FederatedMemory from(SharedMemory memory, String provenance, int maxChars) {
        int depth = Math.max(0, Math.min(maxChars, MAX_SUMMARY_LENGTH));
        var content = memory.content();
        String summary;
        if (depth == 0) {
            summary = "";
        } else if (content.length() <= depth) {
            summary = content;
        } else {
            summary = content.substring(0, Math.max(0, depth - 1)) + "…";
        }
        return new FederatedMemory(memory.type(), summary, memory.strength(), provenance);
    }
}
