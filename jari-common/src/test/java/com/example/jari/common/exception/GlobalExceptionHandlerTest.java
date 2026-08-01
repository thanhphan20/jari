package com.example.jari.common.exception;

import com.example.jari.common.dto.ErrorResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void aMissingResourceBecomes404WithTheExceptionMessage() {
        ResponseEntity<ErrorResponseDto> response = handler.handleResourceNotFoundException(
                new ResourceNotFoundException("Task not found with id: 7"), request("/api/tasks/7"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("Task not found with id: 7");
        assertThat(response.getBody().getPath()).isEqualTo("/api/tasks/7");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    void aValidationFailureBecomes400ListingEachFieldMessage() throws Exception {
        ResponseEntity<ErrorResponseDto> response = handler.handleValidationExceptions(
                validationErrorOn("summary", "must not be blank"), request("/api/tasks"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(response.getBody().getErrors()).containsExactly("must not be blank");
    }

    @Test
    void anUnexpectedExceptionBecomes500() {
        ResponseEntity<ErrorResponseDto> response = handler.handleGlobalException(
                new IllegalStateException("boom"), request("/api/projects"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getErrors()).isNull();
    }

    @Test
    void stripsTheUriPrefixSpringAddsToTheRequestDescription() {
        // WebRequest.getDescription returns "uri=/path"; leaving that in makes the
        // path field unusable for anything reading it programmatically.
        ResponseEntity<ErrorResponseDto> response = handler.handleGlobalException(
                new RuntimeException("x"), request("/api/anything"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPath()).doesNotContain("uri=").isEqualTo("/api/anything");
    }

    private static WebRequest request(String path) {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=" + path);
        return request;
    }

    private static MethodArgumentNotValidException validationErrorOn(String field, String message) {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", field, message));
        return new MethodArgumentNotValidException(null, bindingResult);
    }
}
