package com.example.jari.common.dto;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These factories replaced the same envelope hand-built at 33 controller sites,
 * where nothing stopped the body's status from disagreeing with the HTTP status.
 * The point of each test below is that the two now cannot diverge.
 */
class ResponseDtoTest {

    @Test
    void okReturns200InBothTheResponseAndTheBody() {
        ResponseEntity<ResponseDto<String>> response = ResponseDto.ok("payload", "retrieved");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(200);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("retrieved");
        assertThat(response.getBody().getData()).isEqualTo("payload");
    }

    @Test
    void createdReturns201InBothTheResponseAndTheBody() {
        ResponseEntity<ResponseDto<String>> response = ResponseDto.created("payload", "created");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(201);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo("payload");
    }

    @Test
    void theNoBodyOkCarriesNullDataAnd200() {
        ResponseEntity<ResponseDto<Void>> response = ResponseDto.ok("deleted");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(200);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("deleted");
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    void carriesCollectionsWithoutFlatteningThem() {
        ResponseEntity<ResponseDto<List<String>>> response =
                ResponseDto.ok(List.of("a", "b"), "listed");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).containsExactly("a", "b");
    }

    @Test
    void allowsNullDataWithoutFailing() {
        // getUserById-style handlers can legitimately produce a null payload; the
        // envelope must not choke on it.
        ResponseEntity<ResponseDto<String>> response = ResponseDto.ok(null, "nothing to report");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().isSuccess()).isTrue();
    }
}
