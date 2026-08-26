package com.suplab.aether.memory.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * An append-only audit record of a change to a tenant's {@link MemoryPolicy}.
 *
 * <p>Governance policy shapes retention, decay, and federation — the levers that decide what data
 * survives and what may leave the tenancy boundary. Every change to those levers must be
 * demonstrable after the fact, so each accepted {@code PUT} of a policy appends one of these records:
 * the tenant, an optional actor label, a <strong>length-bounded, human-readable summary</strong> of
 * what changed (before → after per field), and when. It stores no secrets and no memory content.
 * Written once; never updated or deleted.</p>
 *
 * @param id         stable identifier
 * @param tenantId   the tenant whose policy changed
 * @param actor      an optional label for who made the change (nullable → recorded as unattributed)
 * @param summary    a bounded description of the change (truncated on construction)
 * @param occurredAt when the change was applied
 */
public record PolicyChangeEvent(
        UUID id,
        String tenantId,
        String actor,
        String summary,
        Instant occurredAt
) {
    /** Maximum characters of the change summary retained. */
    public static final int MAX_SUMMARY = 500;

    /** Maximum characters of the actor label retained. */
    public static final int MAX_ACTOR = 120;

    public PolicyChangeEvent {
        if (id == null) id = UUID.randomUUID();
        if (tenantId == null || tenantId.isBlank())
            throw new IllegalArgumentException("tenantId required");
        if (actor != null && actor.length() > MAX_ACTOR) actor = actor.substring(0, MAX_ACTOR);
        if (summary == null) summary = "";
        if (summary.length() > MAX_SUMMARY) summary = summary.substring(0, MAX_SUMMARY);
        if (occurredAt == null) occurredAt = Instant.now();
    }

    /**
     * Factory recording a freshly applied policy change: random ID, {@code occurredAt} now, summary
     * truncated to {@link #MAX_SUMMARY}.
     *
     * @param tenantId the tenant whose policy changed
     * @param actor    who made the change (nullable)
     * @param summary  a description of the change (truncated)
     */
    public static PolicyChangeEvent of(String tenantId, String actor, String summary) {
        return new PolicyChangeEvent(UUID.randomUUID(), tenantId, actor, summary, Instant.now());
    }

    /**
     * Builds a bounded, human-readable delta between two policies — one {@code field: old → new}
     * clause per changed field, or {@code "no changes"} when they are identical. Never includes
     * secrets or content; only the governance scalar values.
     *
     * @param before the policy prior to the change (typically the resolved current/default policy)
     * @param after  the policy being applied
     * @return a summary string suitable for {@link #summary}
     */
    public static String describe(MemoryPolicy before, MemoryPolicy after) {
        var sb = new StringBuilder();
        appendIfChanged(sb, "decayRate", before.decayRate(), after.decayRate());
        appendIfChanged(sb, "decayAfterDays", before.decayAfterDays(), after.decayAfterDays());
        appendIfChanged(sb, "reinforcementIncrement",
                before.reinforcementIncrement(), after.reinforcementIncrement());
        appendIfChanged(sb, "archiveThreshold", before.archiveThreshold(), after.archiveThreshold());
        appendIfChanged(sb, "retentionDays", before.retentionDays(), after.retentionDays());
        appendIfChanged(sb, "federationEnabled", before.federationEnabled(), after.federationEnabled());
        appendIfChanged(sb, "federationSummaryChars",
                before.federationSummaryChars(), after.federationSummaryChars());
        return sb.isEmpty() ? "no changes" : sb.toString();
    }

    private static void appendIfChanged(StringBuilder sb, String field, Object before, Object after) {
        if (before.equals(after)) return;
        if (!sb.isEmpty()) sb.append("; ");
        sb.append(field).append(": ").append(before).append(" → ").append(after);
    }
}
