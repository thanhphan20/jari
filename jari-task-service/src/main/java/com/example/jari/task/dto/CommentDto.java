package com.example.jari.task.dto;

import com.example.jari.common.dto.BaseDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CommentDto extends BaseDto {
    private Long taskId;
    private Long authorId;

    @NotBlank(message = "Comment body cannot be empty")
    @Size(max = 2000, message = "Comment body must not exceed 2000 characters")
    private String body;
}
