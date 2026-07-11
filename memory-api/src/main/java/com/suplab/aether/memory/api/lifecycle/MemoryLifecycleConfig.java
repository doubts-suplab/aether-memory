package com.suplab.aether.memory.api.lifecycle;

import com.suplab.aether.memory.ports.MemoryLifecyclePort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables and wires the scheduled memory lifecycle.
 *
 * <p>Active by default; set {@code aether.memory.lifecycle.decay-enabled=false} to opt out (for
 * example in environments where a separate batch job owns decay). {@code @EnableScheduling} is
 * scoped to this config so the scheduler only activates when the lifecycle is enabled.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "aether.memory.lifecycle.decay-enabled", havingValue = "true", matchIfMissing = true)
public class MemoryLifecycleConfig {

    @Bean
    public MemoryLifecycleScheduler memoryLifecycleScheduler(MemoryLifecyclePort lifecyclePort,
                                                             MeterRegistry meterRegistry) {
        return new MemoryLifecycleScheduler(lifecyclePort, meterRegistry);
    }
}
