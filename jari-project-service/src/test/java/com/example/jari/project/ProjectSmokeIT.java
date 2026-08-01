package com.example.jari.project;

import com.example.jari.project.dto.ProjectDto;
import com.example.jari.security.IdentityHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectSmokeIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createdProjectIsPersistedAndRetrievable() {
        ProjectDto request = ProjectDto.builder()
                .key("SMOKE")
                .name("Smoke Test Project")
                .active(true)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set(IdentityHeaders.USER_ID, "1");
        ResponseEntity<String> createResponse =
                restTemplate.postForEntity("/projects", new HttpEntity<>(request, headers), String.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> getResponse = restTemplate.exchange(
                "/projects/key/SMOKE", org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).contains("Smoke Test Project");
    }
}
