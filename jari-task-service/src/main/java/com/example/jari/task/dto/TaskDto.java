package com.example.jari.task.dto;

import com.example.jari.common.dto.BaseDto;
import jakarta.validation.constraints.NotEmpty;
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
public class TaskDto extends BaseDto {
    private String key;
    
    @NotEmpty(message = "Summary cannot be empty")
    @Size(min = 5, max = 100, message = "Summary length must be between 5 and 100 characters")
    private String summary;
    
    @Size(max = 1000, message = "Description length must not exceed 1000 characters")
    private String description;
    
    private Integer order;
    private Integer priority; // 1=Lowest, 2=Low, 3=Medium, 4=High, 5=Highest
    private Integer type; // 1=Task, 2=Bug, 3=Story, 4=Epic
    private String status;
    private Long projectId;
    private Long reporterId;
    private Long assigneeId;
}
