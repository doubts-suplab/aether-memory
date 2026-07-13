package com.suplab.aether.memory.api.config;

import com.suplab.aether.memory.engine.embedding.SharedEmbeddingService;
import com.suplab.aether.memory.engine.federation.DefaultMemoryFederationGateway;
import com.suplab.aether.memory.engine.federation.DefaultMemoryFederationService;
import com.suplab.aether.memory.engine.federation.FederationRateLimiter;
import com.suplab.aether.memory.engine.federation.HttpFederationPeerClient;
import com.suplab.aether.memory.engine.federation.JdbcFederationAuditStore;
import com.suplab.aether.memory.engine.governance.JdbcMemoryGovernanceService;
import com.suplab.aether.memory.engine.lifecycle.PolicyAwareMemoryLifecycleService;
import com.suplab.aether.memory.engine.policy.JdbcMemoryPolicyAuditStore;
import com.suplab.aether.memory.engine.policy.JdbcMemoryPolicyStore;
import com.suplab.aether.memory.engine.store.PGVectorSharedMemoryStore;
import com.suplab.aether.memory.ports.FederationAuditStore;
import com.suplab.aether.memory.ports.FederationPeerClient;
import com.suplab.aether.memory.ports.MemoryFederationGateway;
import com.suplab.aether.memory.ports.MemoryFederationPort;
import com.suplab.aether.memory.ports.MemoryGovernancePort;
import com.suplab.aether.memory.ports.MemoryLifecyclePort;
import com.suplab.aether.memory.ports.MemoryPolicyAuditStore;
import com.suplab.aether.memory.ports.MemoryPolicyStore;
import com.suplab.aether.memory.ports.SharedMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Spring configuration for Aether Memory API beans.
 *
 * <p>Wires the pgvector shared-memory store, Ollama embedding service, per-tenant policy store,
 * federation service, and lifecycle service using constructor injection. All beans are declared
 * here — never via field {@code @Autowired}.</p>
 */
@Configuration
public class MemoryApiConfig {

    /**
     * Creates the shared-memory store backed by pgvector.
     */
    @Bean
    public SharedMemoryStore sharedMemoryStore(NamedParameterJdbcTemplate jdbc) {
        return new PGVectorSharedMemoryStore(jdbc);
    }

    /**
     * Creates the per-tenant memory policy store backed by the {@code memory_policies} table.
     */
    @Bean
    public MemoryPolicyStore memoryPolicyStore(NamedParameterJdbcTemplate jdbc) {
        return new JdbcMemoryPolicyStore(jdbc);
    }

    /**
     * Creates the policy-change audit store backed by the {@code policy_change_audit} table.
     */
    @Bean
    public MemoryPolicyAuditStore memoryPolicyAuditStore(NamedParameterJdbcTemplate jdbc) {
        return new JdbcMemoryPolicyAuditStore(jdbc);
    }

    /**
     * Creates the GDPR governance service (erasure across active + archive, export, erasure audit).
     */
    @Bean
    public MemoryGovernancePort memoryGovernancePort(NamedParameterJdbcTemplate jdbc) {
        return new JdbcMemoryGovernanceService(jdbc);
    }

    /**
     * Creates the local privacy-preserving federation service. The embedding service is optional so
     * federation remains available (degraded to zero-vector matching) when Ollama is disabled.
     * Redaction depth per federated result comes from the source tenant's policy.
     */
    @Bean
    public MemoryFederationPort memoryFederationPort(SharedMemoryStore memoryStore,
                                                     MemoryPolicyStore policyStore,
                                                     Optional<SharedEmbeddingService> embeddingService) {
        return new DefaultMemoryFederationService(memoryStore, policyStore, embeddingService);
    }

    /**
     * Creates the federation audit store backed by the {@code federation_audit} table.
     */
    @Bean
    public FederationAuditStore federationAuditStore(NamedParameterJdbcTemplate jdbc) {
        return new JdbcFederationAuditStore(jdbc);
    }

    /**
     * Creates the outbound HTTP client used to query peer Memory instances during fan-out.
     */
    @Bean
    public FederationPeerClient federationPeerClient() {
        return new HttpFederationPeerClient();
    }

    /**
     * Creates the federation gateway that orchestrates local search + peer fan-out + audit.
     *
     * @param peersCsv comma-separated peer base URLs (empty by default — local-only federation)
     */
    @Bean
    public MemoryFederationGateway memoryFederationGateway(
            MemoryFederationPort localFederation,
            FederationPeerClient peerClient,
            FederationAuditStore auditStore,
            @Value("${aether.memory.federation.peers:}") String peersCsv) {
        List<String> peers = peersCsv == null || peersCsv.isBlank()
                ? List.of()
                : Arrays.stream(peersCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new DefaultMemoryFederationGateway(localFederation, peerClient, auditStore, peers);
    }

    /**
     * Creates the per-origin federation rate limiter.
     *
     * @param capacity     max federation queries per origin per window (default 60)
     * @param windowMillis window length in milliseconds (default 60000 = 1 minute)
     */
    @Bean
    public FederationRateLimiter federationRateLimiter(
            @Value("${aether.memory.federation.rate-limit.capacity:60}") int capacity,
            @Value("${aether.memory.federation.rate-limit.window-millis:60000}") long windowMillis) {
        return new FederationRateLimiter(capacity, windowMillis);
    }

    /**
     * Creates the policy-aware decay + archive lifecycle service. Default decay parameters apply
     * to tenants without an explicit policy; per-tenant overrides come from {@code memory_policies}.
     *
     * @param defaultDecayRate        strength lost per idle day (default 0.01)
     * @param defaultDecayAfterDays   grace period in days (default 7)
     * @param defaultArchiveThreshold archive cutoff strength (default 0.1)
     */
    @Bean
    public MemoryLifecyclePort memoryLifecyclePort(
            NamedParameterJdbcTemplate jdbc,
            @Value("${aether.memory.lifecycle.decay-rate:0.01}") double defaultDecayRate,
            @Value("${aether.memory.lifecycle.decay-after-days:7}") int defaultDecayAfterDays,
            @Value("${aether.memory.lifecycle.archive-threshold:0.1}") double defaultArchiveThreshold,
            @Value("${aether.memory.lifecycle.retention-days:90}") int defaultRetentionDays) {
        return new PolicyAwareMemoryLifecycleService(
                jdbc, defaultDecayRate, defaultDecayAfterDays, defaultArchiveThreshold, defaultRetentionDays);
    }

    /**
     * Creates the embedding service that calls Ollama's {@code /api/embeddings} endpoint.
     *
     * <p>Conditional on {@code aether.memory.embedding.enabled=true} (default). Set to
     * {@code false} in environments where Ollama is unavailable — memories will be saved with
     * zero vectors and semantic similarity search will be non-functional, but all other
     * endpoints remain operational.</p>
     */
    @Bean
    @ConditionalOnProperty(name = "aether.memory.embedding.enabled", havingValue = "true", matchIfMissing = true)
    public SharedEmbeddingService sharedEmbeddingService(
            @Value("${aether.memory.ollama.base-url:http://localhost:11434}") String ollamaUrl,
            @Value("${aether.memory.embedding.model:all-minilm}") String model) {
        return new SharedEmbeddingService(ollamaUrl, model);
    }
}
