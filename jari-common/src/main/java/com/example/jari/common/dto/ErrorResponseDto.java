package com.example.jari.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponseDto {
    private String message;
    private int status;
    private LocalDateTime timestamp;
    private String path;
    private List<String> errors;
}
