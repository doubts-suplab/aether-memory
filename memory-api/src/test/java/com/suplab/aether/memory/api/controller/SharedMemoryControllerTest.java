package com.suplab.aether.memory.api.controller;

import com.suplab.aether.memory.domain.MemoryPolicy;
import com.suplab.aether.memory.domain.MemoryScope;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.domain.MemoryVisibility;
import com.suplab.aether.memory.domain.SharedMemory;
import com.suplab.aether.memory.ports.MemoryPolicyStore;
import com.suplab.aether.memory.ports.SharedMemoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SharedMemoryControllerTest {

    private static final String TENANT = "acme";
    private static final String TEAM = "platform";

    /** In-memory store recording saves/contributions and returning a fixed similar corpus. */
    private static final class FakeStore implements SharedMemoryStore {
        final List<SharedMemory> saved = new ArrayList<>();
        SharedMemory contributeReturn;
        double lastIncrement = -1;
        int lastLimit = -1;

        @Override public void save(SharedMemory memory, float[] embedding) { saved.add(memory); }

        @Override
        public List<SharedMemory> findSimilar(MemoryScope scope, float[] q, int limit, double inc) {
            lastLimit = limit;
            lastIncrement = inc;
            return saved;
        }

        @Override
        public List<SharedMemory> findByType(MemoryScope scope, MemoryType type, int limit, double inc) {
            lastIncrement = inc;
            return saved;
        }

        @Override
        public List<SharedMemory> findFederatable(float[] q, MemoryType type, int limit) {
            return List.of();
        }

        @Override
        public Optional<SharedMemory> contribute(UUID memoryId, MemoryScope scope, double increment) {
            lastIncrement = increment;
            return Optional.ofNullable(contributeReturn);
        }

        @Override public void delete(UUID memoryId, MemoryScope scope) { }
        @Override public long countByTeam(MemoryScope scope) { return saved.size(); }
    }

    private static final class FakePolicyStore implements MemoryPolicyStore {
        @Override public MemoryPolicy resolve(String tenantId) { return MemoryPolicy.defaults(tenantId); }
        @Override public void save(MemoryPolicy policy) { }
        @Override public List<MemoryPolicy> findAll() { return List.of(); }
    }

    private SharedMemoryController controller(FakeStore store) {
        return new SharedMemoryController(store, new FakePolicyStore(), Optional.empty());
    }

    private static SharedMemory memory(String content) {
        return SharedMemory.create(MemoryScope.of(TENANT, TEAM), MemoryType.SEMANTIC, content,
                MemoryVisibility.PRIVATE);
    }

    @Test
    void store_persistsAndReturns201() {
        var store = new FakeStore();
        var res = controller(store).store(TENANT, TEAM,
                Map.of("type", "SEMANTIC", "content", "phased rollouts", "visibility", "TENANT"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(store.saved).hasSize(1);
        assertThat(store.saved.get(0).content()).isEqualTo("phased rollouts");
    }

    @Test
    void store_rejectsBlankContent() {
        var res = controller(new FakeStore()).store(TENANT, TEAM, Map.of("content", "  "));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void store_rejectsInvalidType() {
        var res = controller(new FakeStore()).store(TENANT, TEAM,
                Map.of("type", "NONSENSE", "content", "x"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void search_returnsResultsAndUsesPolicyIncrement() {
        var store = new FakeStore();
        store.saved.add(memory("shared insight"));

        var res = controller(store).search(TENANT, TEAM, Map.of("query", "insight", "limit", 5));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) res.getBody()).hasSize(1);
        assertThat(store.lastLimit).isEqualTo(5);
        // increment sourced from tenant policy (defaults = 0.1)
        assertThat(store.lastIncrement).isEqualTo(MemoryPolicy.defaults(TENANT).reinforcementIncrement());
    }

    @Test
    void search_rejectsBlankQuery() {
        var res = controller(new FakeStore()).search(TENANT, TEAM, Map.of("query", "  "));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void search_defaultsLimitWhenAbsent() {
        var store = new FakeStore();
        controller(store).search(TENANT, TEAM, Map.of("query", "x"));
        assertThat(store.lastLimit).isEqualTo(10);
    }

    @Test
    void contribute_returnsUpdatedViewWhenFound() {
        var store = new FakeStore();
        var contributed = memory("insight").contribute(0.1); // contributorCount 2
        store.contributeReturn = contributed;

        var res = controller(store).contribute(TENANT, TEAM, contributed.id());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) res.getBody()).get("contributorCount")).isEqualTo(2);
        assertThat(store.lastIncrement).isEqualTo(MemoryPolicy.defaults(TENANT).reinforcementIncrement());
    }

    @Test
    void contribute_returns404WhenMissing() {
        var store = new FakeStore(); // contributeReturn stays null → empty
        var res = controller(store).contribute(TENANT, TEAM, UUID.randomUUID());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listByType_rejectsInvalidType() {
        var res = controller(new FakeStore()).listByType(TENANT, TEAM, "NONSENSE", 10);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void count_and_delete_work() {
        var store = new FakeStore();
        store.saved.add(memory("a"));

        assertThat(controller(store).count(TENANT, TEAM).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller(store).delete(TENANT, TEAM, UUID.randomUUID()).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }
}
