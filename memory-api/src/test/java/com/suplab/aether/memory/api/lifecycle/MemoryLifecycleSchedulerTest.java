package com.suplab.aether.memory.api.lifecycle;

import com.suplab.aether.memory.ports.MemoryLifecyclePort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryLifecycleSchedulerTest {

    /** Stub port returning a scripted sequence of results, one per run. */
    private static final class ScriptedLifecyclePort implements MemoryLifecyclePort {
        private final Deque<LifecycleResult> results;

        ScriptedLifecyclePort(LifecycleResult... scripted) {
            this.results = new ArrayDeque<>(java.util.List.of(scripted));
        }

        @Override
        public LifecycleResult runLifecycle() {
            return results.poll();
        }
    }

    @Test
    void run_recordsCountersAndGauge() {
        var registry = new SimpleMeterRegistry();
        var port = new ScriptedLifecyclePort(new MemoryLifecyclePort.LifecycleResult(5, 2, 1, 40));
        var scheduler = new MemoryLifecycleScheduler(port, registry);

        scheduler.runScheduledLifecycle();

        assertThat(registry.get("aether.memory.shared.decayed").counter().count()).isEqualTo(5.0);
        assertThat(registry.get("aether.memory.shared.archived").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("aether.memory.shared.purged").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("aether.memory.shared.total").gauge().value()).isEqualTo(40.0);
    }

    @Test
    void run_countersAccumulateButGaugeReflectsLatest() {
        var registry = new SimpleMeterRegistry();
        var port = new ScriptedLifecyclePort(
                new MemoryLifecyclePort.LifecycleResult(5, 2, 1, 40),
                new MemoryLifecyclePort.LifecycleResult(3, 1, 2, 36));
        var scheduler = new MemoryLifecycleScheduler(port, registry);

        scheduler.runScheduledLifecycle();
        scheduler.runScheduledLifecycle();

        // Counters accumulate across runs...
        assertThat(registry.get("aether.memory.shared.decayed").counter().count()).isEqualTo(8.0);
        assertThat(registry.get("aether.memory.shared.archived").counter().count()).isEqualTo(3.0);
        assertThat(registry.get("aether.memory.shared.purged").counter().count()).isEqualTo(3.0);
        // ...the gauge reflects only the latest run.
        assertThat(registry.get("aether.memory.shared.total").gauge().value()).isEqualTo(36.0);
    }
}
