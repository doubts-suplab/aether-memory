package com.suplab.aether.memory.api.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FederationAuthenticatorTest {

    @Test
    void authNotRequired_alwaysAuthorizes() {
        var auth = new FederationAuthenticator(false, "");
        assertThat(auth.required()).isFalse();
        assertThat(auth.isAuthorized(null)).isTrue();
        assertThat(auth.isAuthorized("anything")).isTrue();
    }

    @Test
    void authRequired_acceptsOnlyMatchingBearerToken() {
        var auth = new FederationAuthenticator(true, "s3cret");
        assertThat(auth.required()).isTrue();
        assertThat(auth.isAuthorized("Bearer s3cret")).isTrue();
        assertThat(auth.isAuthorized("Bearer wrong")).isFalse();
        assertThat(auth.isAuthorized("s3cret")).isFalse();   // missing scheme
        assertThat(auth.isAuthorized(null)).isFalse();
    }

    @Test
    void authRequiredWithoutToken_failsClosed() {
        assertThatThrownBy(() -> new FederationAuthenticator(true, "  "))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("auth-token");
    }
}
