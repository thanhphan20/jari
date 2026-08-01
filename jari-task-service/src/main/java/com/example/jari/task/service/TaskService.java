package com.example.jari.task.service;

import com.example.jari.common.exception.ResourceNotFoundException;
import com.example.jari.task.dto.TaskDto;
import com.example.jari.task.entity.Task;
import com.example.jari.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    @Transactional
    public TaskDto createTask(TaskDto taskDto) {
        Task task = Task.builder()
                .key(taskDto.getKey())
                .summary(taskDto.getSummary())
                .description(taskDto.getDescription())
                .order(taskDto.getOrder())
                .priority(taskDto.getPriority())
                .type(taskDto.getType())
                .status(taskDto.getStatus() != null ? taskDto.getStatus() : "TODO")
                .projectId(taskDto.getProjectId())
                .reporterId(taskDto.getReporterId())
                .assigneeId(taskDto.getAssigneeId())
                .build();

        Task savedTask = taskRepository.save(task);
        return mapToDto(savedTask);
    }

    @Transactional(readOnly = true)
    public TaskDto getTaskById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
        return mapToDto(task);
    }

    @Transactional(readOnly = true)
    public TaskDto getTaskByKey(String key) {
        Task task = taskRepository.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with key: " + key));
        return mapToDto(task);
    }

    @Transactional(readOnly = true)
    public List<TaskDto> getAllTasks() {
        return taskRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TaskDto> getTasksByProjectId(Long projectId) {
        return taskRepository.findByProjectId(projectId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TaskDto> getTasksByAssigneeId(Long assigneeId) {
        return taskRepository.findByAssigneeId(assigneeId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public TaskDto updateTask(Long id, TaskDto taskDto) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));

        task.setSummary(taskDto.getSummary());
        task.setDescription(taskDto.getDescription());
        task.setOrder(taskDto.getOrder());
        task.setPriority(taskDto.getPriority());
        task.setType(taskDto.getType());
        task.setStatus(taskDto.getStatus());
        task.setAssigneeId(taskDto.getAssigneeId());

        Task updatedTask = taskRepository.save(task);
        return mapToDto(updatedTask);
    }

    @Transactional
    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
        taskRepository.delete(task);
    }

    // Package-private rather than private: KanbanService maps the same entity for
    // its board payload and used to carry a byte-identical copy of this method.
    TaskDto mapToDto(Task task) {
        return TaskDto.builder()
                .id(task.getId())
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
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
