package com.suplab.aether.memory.api.config;

import com.suplab.aether.memory.engine.embedding.SharedEmbeddingService;
import com.suplab.aether.memory.engine.federation.DefaultMemoryFederationService;
import com.suplab.aether.memory.engine.lifecycle.PolicyAwareMemoryLifecycleService;
import com.suplab.aether.memory.engine.policy.JdbcMemoryPolicyStore;
import com.suplab.aether.memory.engine.store.PGVectorSharedMemoryStore;
import com.suplab.aether.memory.ports.MemoryFederationPort;
import com.suplab.aether.memory.ports.MemoryLifecyclePort;
import com.suplab.aether.memory.ports.MemoryPolicyStore;
import com.suplab.aether.memory.ports.SharedMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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
     * Creates the privacy-preserving federation service. The embedding service is optional so
     * federation remains available (degraded to zero-vector matching) when Ollama is disabled.
     */
    @Bean
    public MemoryFederationPort memoryFederationPort(SharedMemoryStore memoryStore,
                                                     Optional<SharedEmbeddingService> embeddingService) {
        return new DefaultMemoryFederationService(memoryStore, embeddingService);
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
