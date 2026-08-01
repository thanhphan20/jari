package com.example.jari.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    // HS256 needs >= 256 bits of key material, and the constructor base64-decodes
    // whatever it is given, so the secret has to be base64 of >= 32 bytes.
    private static final String SECRET = Base64.getEncoder()
            .encodeToString("test-signing-secret-at-least-32-bytes".getBytes());

    private final JwtUtils jwtUtils = new JwtUtils(SECRET);

    @Test
    void generatedTokenCarriesSubjectAndUserId() {
        String token = jwtUtils.generateToken(42L, "ada", List.of("USER"));

        assertThat(jwtUtils.extractUsername(token)).isEqualTo("ada");
        assertThat(jwtUtils.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void validateTokenAcceptsTheMatchingUsername() {
        String token = jwtUtils.generateToken(1L, "ada", List.of("USER"));

        assertThat(jwtUtils.validateToken(token, "ada")).isTrue();
    }

    @Test
    void validateTokenRejectsADifferentUsername() {
        String token = jwtUtils.generateToken(1L, "ada", List.of("USER"));

        assertThat(jwtUtils.validateToken(token, "grace")).isFalse();
    }

    @Test
    void extractUserIdIsNullWhenTheClaimIsAbsent() {
        // Tokens minted anywhere other than generateToken need not carry userId;
        // the gateway turns a null into a header it can detect rather than a crash.
        String noUserIdClaim = io.jsonwebtoken.Jwts.builder()
                .setSubject("ada")
                .setExpiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        io.jsonwebtoken.io.Decoders.BASE64.decode(SECRET)),
                        io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();

        assertThat(jwtUtils.extractUserId(noUserIdClaim)).isNull();
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        String otherSecret = Base64.getEncoder()
                .encodeToString("a-completely-different-secret-32b".getBytes());
        String foreignToken = new JwtUtils(otherSecret).generateToken(1L, "ada", List.of("USER"));

        assertThatThrownBy(() -> jwtUtils.extractUsername(foreignToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void aTamperedTokenIsRejected() {
        String token = jwtUtils.generateToken(1L, "ada", List.of("USER"));
        // Flip the last character of the signature segment.
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> jwtUtils.extractUsername(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void anExpiredTokenFailsValidationRatherThanPassing() {
        // parseClaimsJws throws on an expired token, so validateToken cannot simply
        // return false - the caller must treat the throw as a rejection. The gateway
        // does (AuthenticationFilter wraps it in try/catch). Pinning that here so a
        // future refactor cannot quietly turn an expired token into a valid one.
        assertThatThrownBy(() -> jwtUtils.validateToken(expiredToken(), "ada"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void aBlankSecretFailsFastAtConstruction() {
        assertThatThrownBy(() -> new JwtUtils("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jari.security.jwt.secret");
    }

    private static String expiredToken() {
        return io.jsonwebtoken.Jwts.builder()
                .setSubject("ada")
                .setIssuedAt(new java.util.Date(System.currentTimeMillis() - 120_000))
                .setExpiration(new java.util.Date(System.currentTimeMillis() - 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        io.jsonwebtoken.io.Decoders.BASE64.decode(SECRET)),
                        io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();
    }
}
