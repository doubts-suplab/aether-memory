package com.suplab.aether.memory.api.lifecycle;

import com.suplab.aether.memory.ports.MemoryLifecyclePort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs the policy-aware memory decay + archive lifecycle on a schedule and records metrics.
 *
 * <p>Delegates the work to {@link MemoryLifecyclePort} (set-based SQL) and publishes:</p>
 * <ul>
 *   <li>{@code aether.memory.shared.decayed} — counter, memories decayed (accumulates)</li>
 *   <li>{@code aether.memory.shared.archived} — counter, memories archived (accumulates)</li>
 *   <li>{@code aether.memory.shared.total} — gauge, active memories after the last run</li>
 * </ul>
 */
public class MemoryLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(MemoryLifecycleScheduler.class);

    private final MemoryLifecyclePort lifecyclePort;
    private final Counter decayedCounter;
    private final Counter archivedCounter;
    private final AtomicLong totalActive = new AtomicLong(0);

    public MemoryLifecycleScheduler(MemoryLifecyclePort lifecyclePort, MeterRegistry meterRegistry) {
        this.lifecyclePort = lifecyclePort;
        this.decayedCounter = Counter.builder("aether.memory.shared.decayed")
                .description("Total shared memories decayed across lifecycle runs")
                .register(meterRegistry);
        this.archivedCounter = Counter.builder("aether.memory.shared.archived")
                .description("Total shared memories archived across lifecycle runs")
                .register(meterRegistry);
        meterRegistry.gauge("aether.memory.shared.total", totalActive);
    }

    /**
     * Executes one lifecycle run and updates metrics. Cron is configurable via
     * {@code aether.memory.lifecycle.decay-cron} (default 03:00 daily).
     */
    @Scheduled(cron = "${aether.memory.lifecycle.decay-cron:0 0 3 * * *}")
    public void runScheduledLifecycle() {
        var result = lifecyclePort.runLifecycle();
        decayedCounter.increment(result.decayedCount());
        archivedCounter.increment(result.archivedCount());
        totalActive.set(result.totalRemaining());
        log.info("Scheduled shared-memory lifecycle run: decayed={} archived={} totalRemaining={}",
                result.decayedCount(), result.archivedCount(), result.totalRemaining());
    }
}
