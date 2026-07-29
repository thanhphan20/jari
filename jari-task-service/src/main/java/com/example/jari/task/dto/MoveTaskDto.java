package com.example.jari.task.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveTaskDto {
    @NotNull
    private Long taskId;
    
    @NotNull
    private String targetStatus;
    
    private Integer targetIndex; // For future implementation of reordering within column
}
