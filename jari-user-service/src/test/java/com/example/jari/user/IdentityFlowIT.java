package com.example.jari.user;

import com.example.jari.security.IdentityHeaders;
import com.example.jari.security.JwtUtils;
import com.example.jari.user.dto.AuthRequest;
import com.example.jari.user.dto.RegisterRequest;
import com.example.jari.user.entity.User;
import com.example.jari.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The identity flow - built last phase, verified only by hand until now (see
 * proposal.md "Why"). Covers registration, authentication, invalid credentials,
 * and self-lookup with/without an authenticated identity.
 */
class IdentityFlowIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    private String register(String username, String email, String password) {
        RegisterRequest request = new RegisterRequest(username, email, password);
        restTemplate.postForEntity("/auth/register", request, String.class);
        return username;
    }

    private String authenticate(String username, String password) {
        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/token", new AuthRequest(username, password), String.class);
        return response.getBody();
    }

    @Test
    void registrationPersistsAUserWithAHashedPassword() {
        register("flow-register", "flow-register@example.com", "correct-password");

        User persisted = userRepository.findByUsername("flow-register").orElseThrow();
        assertThat(persisted.getPassword()).isNotEqualTo("correct-password");
    }

    @Test
    void authenticationIssuesATokenCarryingThePersistedUserId() {
        register("flow-auth", "flow-auth@example.com", "correct-password");
        User persisted = userRepository.findByUsername("flow-auth").orElseThrow();

        String token = authenticate("flow-auth", "correct-password");

        assertThat(jwtUtils.extractUserId(token)).isEqualTo(persisted.getId());
    }

    @Test
    void invalidCredentialsAreRejected() {
        register("flow-invalid", "flow-invalid@example.com", "correct-password");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/token", new AuthRequest("flow-invalid", "wrong-password"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void selfLookupResolvesTheAuthenticatedCaller() {
        register("flow-self", "flow-self@example.com", "correct-password");
        User persisted = userRepository.findByUsername("flow-self").orElseThrow();

        HttpHeaders headers = new HttpHeaders();
        headers.set(IdentityHeaders.USER_ID, String.valueOf(persisted.getId()));
        ResponseEntity<String> response = restTemplate.exchange(
                "/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("flow-self");
    }

    @Test
    void selfLookupWithoutIdentityIsRejected() {
        ResponseEntity<String> response = restTemplate.getForEntity("/users/me", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
