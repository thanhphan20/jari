package com.example.jari.task;

import com.example.jari.security.IdentityHeaders;
import com.example.jari.task.dto.TaskDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class TaskSmokeIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createdTaskIsPersistedAndRetrievable() {
        TaskDto request = TaskDto.builder()
                .key("SMOKE-1")
                .summary("Smoke test task summary")
                .projectId(1L)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set(IdentityHeaders.USER_ID, "1");
        ResponseEntity<String> createResponse =
                restTemplate.postForEntity("/tasks", new HttpEntity<>(request, headers), String.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> getResponse = restTemplate.exchange(
                "/tasks/key/SMOKE-1", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).contains("Smoke test task summary");
    }
}
