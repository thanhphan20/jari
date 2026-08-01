package com.example.jari.notification;

import com.example.jari.notification.dto.NotificationDto;
import com.example.jari.security.IdentityHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSmokeIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createdNotificationIsPersistedAndRetrievable() {
        NotificationDto request = NotificationDto.builder()
                .userId(1L)
                .title("Smoke test notification")
                .message("created during the integration test suite")
                .type("INFO")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set(IdentityHeaders.USER_ID, "1");
        ResponseEntity<String> createResponse =
                restTemplate.postForEntity("/notifications", new HttpEntity<>(request, headers), String.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> getResponse = restTemplate.exchange(
                "/notifications/user/1", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).contains("Smoke test notification");
    }
}
