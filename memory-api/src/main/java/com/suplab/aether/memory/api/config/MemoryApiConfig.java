package com.suplab.aether.memory.api.config;

import com.suplab.aether.memory.engine.embedding.SharedEmbeddingService;
import com.suplab.aether.memory.engine.federation.DefaultMemoryFederationService;
import com.suplab.aether.memory.api.security.FederationAuthenticator;
import com.suplab.aether.memory.api.federation.RedisDistributedRateLimitStore;
import com.suplab.aether.memory.engine.federation.HttpFederationPeerClient;
import com.suplab.aether.memory.engine.federation.InMemoryFederationRateLimiter;
import com.suplab.aether.memory.engine.federation.JdbcFederationAuditStore;
import com.suplab.aether.memory.engine.federation.RedisFederationRateLimiter;
import com.suplab.aether.memory.engine.lifecycle.PolicyAwareMemoryLifecycleService;
import com.suplab.aether.memory.engine.policy.JdbcMemoryPolicyStore;
import com.suplab.aether.memory.engine.store.PGVectorSharedMemoryStore;
import com.suplab.aether.memory.ports.FederationAuditStore;
import com.suplab.aether.memory.ports.FederationPeerClient;
import com.suplab.aether.memory.ports.FederationRateLimiter;
import com.suplab.aether.memory.ports.MemoryFederationPort;
import com.suplab.aether.memory.ports.MemoryLifecyclePort;
import com.suplab.aether.memory.ports.MemoryPolicyStore;
import com.suplab.aether.memory.ports.SharedMemoryStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Spring configuration for Aether Memory API beans.
 *
 * <p>Wires the pgvector shared-memory store, Ollama embedding service, per-tenant policy store,
 * federation service (with its audit log, per-origin rate limiter, and optional peer client), and
 * lifecycle service using constructor injection. All beans are declared here — never via field
 * {@code @Autowired}.</p>
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
     * Creates the append-only federation audit store ({@code federation_audit} table).
     */
    @Bean
    public FederationAuditStore federationAuditStore(NamedParameterJdbcTemplate jdbc) {
        return new JdbcFederationAuditStore(jdbc);
    }

    /**
     * Creates the per-origin federation rate limiter. The backend is selected by
     * {@code aether.memory.federation.rate-limit.backend}: {@code memory} (default — a per-instance
     * fixed window) or {@code redis} (a <em>shared</em> fixed window across every instance, so an
     * origin's budget is enforced fleet-wide). The Redis limiter degrades to a per-node in-memory
     * limiter if Redis is unreachable, so a cache outage weakens throttling rather than removing it or
     * blocking federation. Selecting {@code redis} needs a {@link StringRedisTemplate} (Spring Data
     * Redis, configured via {@code spring.data.redis.*}); if none is present it falls back to memory.
     *
     * @param maxPerWindow  maximum federation queries per origin per window (default 60)
     * @param windowSeconds window length in seconds (default 60)
     * @param backend       {@code memory} or {@code redis}
     * @param redisTemplate the Redis template (optional — absent unless the Redis starter is wired)
     */
    @Bean
    public FederationRateLimiter federationRateLimiter(
            @Value("${aether.memory.federation.rate-limit.max-per-window:60}") int maxPerWindow,
            @Value("${aether.memory.federation.rate-limit.window-seconds:60}") int windowSeconds,
            @Value("${aether.memory.federation.rate-limit.backend:memory}") String backend,
            ObjectProvider<StringRedisTemplate> redisTemplate) {
        var local = new InMemoryFederationRateLimiter(maxPerWindow, windowSeconds);
        if ("redis".equalsIgnoreCase(backend)) {
            var template = redisTemplate.getIfAvailable();
            if (template != null) {
                var store = new RedisDistributedRateLimitStore(template);
                return new RedisFederationRateLimiter(store, maxPerWindow, windowSeconds, local);
            }
        }
        return local;
    }

    /**
     * Creates the outbound federation peer client — enabled only when peers are configured.
     *
     * <p>{@code aether.memory.federation.peers} is a comma-separated list of peer base URLs. When
     * empty (default) no bean is created and fan-out queries resolve to local-only, so Memory runs
     * standalone. Peers are the only outbound network dependency.</p>
     *
     * @param peersCsv       comma-separated peer base URLs
     * @param timeoutSeconds per-peer request timeout in seconds (default 10)
     */
    @Bean
    @ConditionalOnProperty(name = "aether.memory.federation.peers")
    public FederationPeerClient federationPeerClient(
            @Value("${aether.memory.federation.peers:}") String peersCsv,
            @Value("${aether.memory.federation.peer-timeout-seconds:10}") long timeoutSeconds,
            @Value("${aether.memory.federation.peer-auth-token:}") String peerAuthToken) {
        List<String> peers = Arrays.stream(peersCsv.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        var restClient = RestClient.builder().requestFactory(requestFactory).build();
        return new HttpFederationPeerClient(peers, restClient, peerAuthToken);
    }

    /**
     * Creates the inbound federation authenticator. When {@code aether.memory.federation.require-auth}
     * is true, {@code /federation/query} requires {@code Authorization: Bearer <auth-token>} — a
     * misconfiguration (require-auth without a token) fails construction. Off by default so Memory runs
     * open standalone; the shared token is sourced from the environment (never hardcoded).
     */
    @Bean
    public FederationAuthenticator federationAuthenticator(
            @Value("${aether.memory.federation.require-auth:false}") boolean requireAuth,
            @Value("${aether.memory.federation.auth-token:}") String authToken) {
        return new FederationAuthenticator(requireAuth, authToken);
    }

    /**
     * Creates the privacy-preserving federation service. The embedding service is optional so
     * federation remains available (degraded to zero-vector matching) when Ollama is disabled; the
     * peer client is optional so the service runs standalone; the policy store drives per-tenant
     * redaction depth and the audit store records every served query.
     */
    @Bean
    public MemoryFederationPort memoryFederationPort(SharedMemoryStore memoryStore,
                                                     Optional<SharedEmbeddingService> embeddingService,
                                                     MemoryPolicyStore policyStore,
                                                     FederationAuditStore auditStore,
                                                     Optional<FederationPeerClient> peerClient) {
        return new DefaultMemoryFederationService(memoryStore, embeddingService, policyStore, auditStore,
                peerClient);
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
            @Value("${aether.memory.lifecycle.archive-threshold:0.1}") double defaultArchiveThreshold) {
        return new PolicyAwareMemoryLifecycleService(
                jdbc, defaultDecayRate, defaultDecayAfterDays, defaultArchiveThreshold);
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
