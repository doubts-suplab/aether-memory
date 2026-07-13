package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.domain.FederatedMemory;
import com.suplab.aether.memory.domain.FederationQuery;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.ports.FederationPeerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * {@link FederationPeerClient} that calls a remote Aether Memory instance's
 * {@code POST /api/v1/federation/query?localOnly=true} endpoint over HTTP.
 *
 * <p>The {@code localOnly=true} parameter tells the peer to answer from its own corpus without
 * fanning out again, so federation never recurses. The client is resilient by design: any error
 * (timeout, connection refused, malformed body) is logged and turned into an empty result so one
 * bad peer never fails the whole federated query.</p>
 */
public class HttpFederationPeerClient implements FederationPeerClient {

    private static final Logger log = LoggerFactory.getLogger(HttpFederationPeerClient.class);

    private final RestClient restClient;

    public HttpFederationPeerClient() {
        this(RestClient.builder()
                .requestFactory(timeoutFactory())
                .build());
    }

    /** Package-visible constructor for tests to inject a preconfigured client. */
    HttpFederationPeerClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<FederatedMemory> queryPeer(String peerBaseUrl, FederationQuery query) {
        try {
            var body = restClient.post()
                    .uri(peerBaseUrl + "/api/v1/federation/query?localOnly=true")
                    .body(Map.of(
                            "originTenantId", query.originTenantId(),
                            "queryText", query.queryText(),
                            "type", query.type() != null ? query.type().name() : "",
                            "limit", query.limit()))
                    .retrieve()
                    .body(List.class);
            if (body == null) {
                return List.of();
            }
            return ((List<Map<String, Object>>) body).stream()
                    .map(HttpFederationPeerClient::toFederatedMemory)
                    .toList();
        } catch (Exception e) {
            log.warn("Federation peer query failed peer={}: {}", peerBaseUrl, e.getMessage());
            return List.of();
        }
    }

    private static FederatedMemory toFederatedMemory(Map<String, Object> map) {
        var type = MemoryType.valueOf(map.get("type").toString());
        var summary = map.getOrDefault("summary", "").toString();
        var strength = map.get("strength") instanceof Number n ? n.doubleValue() : 0.0;
        var provenance = map.getOrDefault("provenance", "FEDERATED").toString();
        return new FederatedMemory(type, summary, strength, provenance);
    }

    private static org.springframework.http.client.ClientHttpRequestFactory timeoutFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        return factory;
    }
}
