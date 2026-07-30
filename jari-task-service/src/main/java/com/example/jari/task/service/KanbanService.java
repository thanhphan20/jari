package com.example.jari.task.service;

import com.example.jari.common.exception.ResourceNotFoundException;
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
import java.util.Comparator;
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
            columnTasks.sort(Comparator.comparing(TaskDto::getOrder, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(TaskDto::getId));

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
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + moveTaskDto.getTaskId()));

        String targetStatus = moveTaskDto.getTargetStatus();
        if (!STANDARD_COLUMNS.contains(targetStatus)) {
            throw new IllegalArgumentException("Unsupported target status: " + targetStatus);
        }
        task.setStatus(targetStatus);

        // Reindex the whole target column so the moved task's order value can never
        // collide with a sibling's - assigning targetIndex directly (the old
        // behavior) let two tasks end up sharing the same order.
        List<Task> columnTasks = taskRepository.findByProjectId(task.getProjectId()).stream()
                .filter(t -> targetStatus.equals(t.getStatus()) && !t.getId().equals(task.getId()))
                .sorted(Comparator.comparing(Task::getOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Task::getId))
                .collect(Collectors.toCollection(ArrayList::new));

        int insertAt = moveTaskDto.getTargetIndex() != null
                ? Math.max(0, Math.min(moveTaskDto.getTargetIndex(), columnTasks.size()))
                : columnTasks.size();
        columnTasks.add(insertAt, task);

        for (int i = 0; i < columnTasks.size(); i++) {
            columnTasks.get(i).setOrder(i);
        }
        taskRepository.saveAll(columnTasks);
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
