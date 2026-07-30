package com.example.jari.security;

/**
 * Header names the gateway injects after validating a token, and that downstream
 * services trust unconditionally. Sound only because the gateway is the sole ingress -
 * see gateway-identity-propagation spec for the trust model this depends on.
 */
public final class IdentityHeaders {

    public static final String USER_ID = "X-Jari-User-Id";
    public static final String USERNAME = "X-Jari-Username";

    private IdentityHeaders() {
    }
}
