package com.example.jari.task.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    @Pattern(regexp = "TODO|IN_PROGRESS|DONE", message = "targetStatus must be one of TODO, IN_PROGRESS, DONE")
    private String targetStatus;
    
    private Integer targetIndex; // For future implementation of reordering within column
}
