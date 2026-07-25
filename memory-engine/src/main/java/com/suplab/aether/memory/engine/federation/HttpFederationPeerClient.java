package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.domain.FederatedMemory;
import com.suplab.aether.memory.domain.FederationQuery;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.ports.FederationPeerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP {@link FederationPeerClient} — fans a federation query out to configured peer Memory instances.
 *
 * <p>Each peer is reached at {@code {baseUrl}/api/v1/federation/query} with the same bounded request
 * body a local caller would send; the peer applies its own audit, rate limiting, and redaction, so
 * this client never sees more than a peer chooses to project. Per-peer failures (timeout, 4xx/5xx,
 * unreachable) are logged and skipped — one bad peer never fails the fan-out. With an empty peer list
 * the client is a no-op, so Memory runs standalone.</p>
 */
public class HttpFederationPeerClient implements FederationPeerClient {

    private static final Logger log = LoggerFactory.getLogger(HttpFederationPeerClient.class);
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new ParameterizedTypeReference<>() {};

    private final List<String> peerBaseUrls;
    private final RestClient restClient;

    public HttpFederationPeerClient(List<String> peerBaseUrls, RestClient restClient) {
        this.peerBaseUrls = peerBaseUrls == null ? List.of() : List.copyOf(peerBaseUrls);
        this.restClient = restClient;
    }

    @Override
    public List<FederatedMemory> queryPeers(FederationQuery query) {
        if (peerBaseUrls.isEmpty()) return List.of();

        var body = new HashMap<String, Object>();
        body.put("originTenantId", query.originTenantId());
        body.put("queryText", query.queryText());
        if (query.type() != null) body.put("type", query.type().name());
        body.put("limit", query.limit());

        var aggregated = new ArrayList<FederatedMemory>();
        for (String base : peerBaseUrls) {
            var url = base.replaceAll("/+$", "") + "/api/v1/federation/query";
            try {
                var rows = restClient.post().uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(LIST_OF_MAPS);
                if (rows != null) {
                    for (var row : rows) {
                        var fm = toFederatedMemory(row);
                        if (fm != null) aggregated.add(fm);
                    }
                }
                log.debug("Federation peer {} returned {} projections", base, rows != null ? rows.size() : 0);
            } catch (RuntimeException e) {
                log.warn("Federation peer {} unreachable or failed — skipping: {}", base, e.getMessage());
            }
        }
        return aggregated;
    }

    private static FederatedMemory toFederatedMemory(Map<String, Object> row) {
        try {
            var typeRaw = str(row.get("type"));
            var type = typeRaw != null ? MemoryType.valueOf(typeRaw) : MemoryType.SEMANTIC;
            var summary = str(row.get("summary"));
            double strength = row.get("strength") instanceof Number n ? n.doubleValue() : 0.0;
            var provenance = str(row.get("provenance"));
            return new FederatedMemory(type, summary, Math.max(0, Math.min(1, strength)), provenance);
        } catch (RuntimeException e) {
            return null; // ignore malformed peer rows
        }
    }

    private static String str(Object v) {
        return v != null ? v.toString() : null;
    }
}
