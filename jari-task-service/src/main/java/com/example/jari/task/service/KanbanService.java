package com.example.jari.task.service;

import com.example.jari.task.dto.KanbanBoardDto;
import com.example.jari.task.dto.KanbanColumnDto;
import com.example.jari.task.dto.MoveTaskDto;
import com.example.jari.task.dto.TaskDto;
import com.example.jari.task.entity.Task;
import com.example.jari.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KanbanService {

    private final TaskRepository taskRepository;
    private final TaskService taskService; // Reuse for mapping entity to dto if needed, or just use mapper

    // Define standard columns for now
    private static final List<String> STANDARD_COLUMNS = Arrays.asList("TODO", "IN_PROGRESS", "DONE");

    @Transactional(readOnly = true)
    public KanbanBoardDto getBoardByProjectId(Long projectId) {
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        
        // Group tasks by status
        Map<String, List<TaskDto>> tasksByStatus = tasks.stream()
                .map(this::mapToDto)
                .collect(Collectors.groupingBy(task -> task.getStatus() != null ? task.getStatus() : "TODO"));

        List<KanbanColumnDto> columns = new ArrayList<>();
        
        for (String status : STANDARD_COLUMNS) {
            List<TaskDto> columnTasks = tasksByStatus.getOrDefault(status, new ArrayList<>());
            // Sort by order if available, else by ID or created date
            // For now, let's just leave them as is or sort by ID
            
            columns.add(KanbanColumnDto.builder()
                    .id(status)
                    .title(formatTitle(status))
                    .tasks(columnTasks)
                    .build());
        }

        return KanbanBoardDto.builder()
                .columns(columns)
                .build();
    }

    @Transactional
    public void moveTask(MoveTaskDto moveTaskDto) {
        Task task = taskRepository.findById(moveTaskDto.getTaskId())
                .orElseThrow(() -> new RuntimeException("Task not found"));
        
        task.setStatus(moveTaskDto.getTargetStatus());
        // Handle logic for reordering if targetIndex is present
        if (moveTaskDto.getTargetIndex() != null) {
            task.setOrder(moveTaskDto.getTargetIndex());
        }
        
        taskRepository.save(task);
    }

    private String formatTitle(String status) {
        return Arrays.stream(status.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    // Duplicated from TaskService for now to avoid circular dependency or excessive refactoring
    // Ideally should use a shared Mapper
    private TaskDto mapToDto(Task task) {
        return TaskDto.builder()
                .id(task.getId())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .key(task.getKey())
                .summary(task.getSummary())
                .description(task.getDescription())
                .order(task.getOrder())
                .priority(task.getPriority())
                .type(task.getType())
                .status(task.getStatus())
                .projectId(task.getProjectId())
                .reporterId(task.getReporterId())
                .assigneeId(task.getAssigneeId())
                .build();
    }
}
