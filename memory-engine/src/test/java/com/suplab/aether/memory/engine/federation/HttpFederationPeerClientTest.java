package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.domain.FederationQuery;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpFederationPeerClientTest {

    private static final FederationQuery QUERY = new FederationQuery("tenant-1", null, "insight", 10);

    @Test
    void noPeersConfigured_returnsEmptyWithoutCallingNetwork() {
        var client = new HttpFederationPeerClient(List.of(), RestClient.create());
        assertThat(client.queryPeers(QUERY)).isEmpty();
    }

    @Test
    void nullPeerList_isTreatedAsNoPeers() {
        var client = new HttpFederationPeerClient(null, RestClient.create());
        assertThat(client.queryPeers(QUERY)).isEmpty();
    }

    @Test
    void unreachablePeer_isSkippedNotThrown() {
        // An unroutable address must be tolerated — the fan-out returns empty, never throws.
        var client = new HttpFederationPeerClient(
                List.of("http://127.0.0.1:1"), RestClient.create());
        assertThat(client.queryPeers(QUERY)).isEmpty();
    }
}
