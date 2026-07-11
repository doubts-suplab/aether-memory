package com.suplab.aether.memory.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aether Memory — shared team and organisational memory platform.
 *
 * <p>Runs on port 8083 (Grid proxy=8080, Grid api=8081, Core=8082, Memory=8083). Provides
 * team-scoped shared memory, per-tenant governance policies, and a privacy-preserving
 * federation query API for cross-instance memory sharing.</p>
 *
 * <p>{@code scanBasePackages} covers all sub-packages of {@code com.suplab.aether.memory} so
 * beans from {@code memory-engine} (embedding service, memory store, policy store, federation
 * and lifecycle services) are discovered via the config class in {@code memory-api}.</p>
 */
@SpringBootApplication(scanBasePackages = "com.suplab.aether.memory")
public class AetherMemoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(AetherMemoryApplication.class, args);
    }
}
