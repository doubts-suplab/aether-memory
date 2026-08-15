package com.suplab.aether.memory.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Verifies the shared bearer token on inbound federation queries.
 *
 * <p>Federation is trust-on-configuration: when {@code aether.memory.federation.require-auth=true}, a
 * peer must present {@code Authorization: Bearer <token>} matching the configured
 * {@code aether.memory.federation.auth-token} or the query is rejected with 401. When auth is not
 * required (the default), Memory runs open so it works standalone and in local dev. Comparison is
 * constant-time to avoid leaking the token through timing.</p>
 *
 * <p><strong>Fail-closed:</strong> if auth is required but no token is configured, construction throws —
 * a misconfigured deployment must not silently accept every request.</p>
 */
public class FederationAuthenticator {

    private final boolean requireAuth;
    private final String expectedHeader;

    public FederationAuthenticator(boolean requireAuth, String expectedToken) {
        this.requireAuth = requireAuth;
        var token = expectedToken == null ? "" : expectedToken.trim();
        if (requireAuth && token.isBlank()) {
            throw new IllegalStateException(
                    "aether.memory.federation.auth-token must be set when require-auth=true");
        }
        this.expectedHeader = "Bearer " + token;
    }

    /** @return {@code true} when inbound federation auth is enforced. */
    public boolean required() {
        return requireAuth;
    }

    /**
     * @param authorizationHeader the inbound {@code Authorization} header (may be {@code null})
     * @return {@code true} when the request may proceed — always when auth is not required, otherwise
     *         only when the header exactly matches {@code Bearer <configured-token>}.
     */
    public boolean isAuthorized(String authorizationHeader) {
        if (!requireAuth) {
            return true;
        }
        if (authorizationHeader == null) {
            return false;
        }
        return MessageDigest.isEqual(
                authorizationHeader.getBytes(StandardCharsets.UTF_8),
                expectedHeader.getBytes(StandardCharsets.UTF_8));
    }
}
