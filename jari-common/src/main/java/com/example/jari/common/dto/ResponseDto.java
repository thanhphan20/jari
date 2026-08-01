package com.example.jari.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDto<T> {
    private boolean success;
    private String message;
    private T data;
    private int status;

    /**
     * 200 with a body. Every controller was hand-building this same envelope, which
     * is how {@code success} and {@code status} drifted from the HTTP status in the
     * first place - here they can only ever agree.
     */
    public static <T> ResponseEntity<ResponseDto<T>> ok(T data, String message) {
        return ResponseEntity.ok(envelope(data, message, HttpStatus.OK));
    }

    /** 201 with a body, and a matching 201 on the response itself. */
    public static <T> ResponseEntity<ResponseDto<T>> created(T data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(envelope(data, message, HttpStatus.CREATED));
    }

    /** 200 with no body - deletes and other acknowledgements. */
    public static ResponseEntity<ResponseDto<Void>> ok(String message) {
        return ResponseEntity.ok(envelope(null, message, HttpStatus.OK));
    }

    private static <T> ResponseDto<T> envelope(T data, String message, HttpStatus status) {
        return ResponseDto.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .status(status.value())
                .build();
    }
}
