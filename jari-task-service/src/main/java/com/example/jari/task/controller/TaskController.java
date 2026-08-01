package com.example.jari.task.controller;

import com.example.jari.common.dto.ResponseDto;
import com.example.jari.task.dto.TaskDto;
import com.example.jari.task.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "Create a new task")
    @ApiResponse(responseCode = "201", description = "Task created successfully")
    @PostMapping
    public ResponseEntity<ResponseDto<TaskDto>> createTask(@Valid @RequestBody TaskDto taskDto) {
        return ResponseDto.created(taskService.createTask(taskDto), "Task created successfully");
    }

    @Operation(summary = "Get task by ID")
    @ApiResponse(responseCode = "200", description = "Task retrieved successfully")
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<TaskDto>> getTaskById(@PathVariable Long id) {
        return ResponseDto.ok(taskService.getTaskById(id), "Task retrieved successfully");
    }

    @Operation(summary = "Get task by Key")
    @ApiResponse(responseCode = "200", description = "Task retrieved successfully")
    @GetMapping("/key/{key}")
    public ResponseEntity<ResponseDto<TaskDto>> getTaskByKey(@PathVariable String key) {
        return ResponseDto.ok(taskService.getTaskByKey(key), "Task retrieved successfully");
    }

    @Operation(summary = "Get All Tasks")
    @ApiResponse(responseCode = "200", description = "Tasks retrieved successfully")
    @GetMapping
    public ResponseEntity<ResponseDto<List<TaskDto>>> getAllTasks() {
        return ResponseDto.ok(taskService.getAllTasks(), "Tasks retrieved successfully");
    }

    @Operation(summary = "Get task by project ID")
    @ApiResponse(responseCode = "200", description = "Tasks retrieved successfully")
    @GetMapping("/project/{projectId}")
    public ResponseEntity<ResponseDto<List<TaskDto>>> getTasksByProjectId(@PathVariable Long projectId) {
        return ResponseDto.ok(taskService.getTasksByProjectId(projectId), "Tasks retrieved successfully");
    }

    @Operation(summary = "Get task by assign ID")
    @ApiResponse(responseCode = "200", description = "Task retrieved successfully")
    @GetMapping("/assignee/{assigneeId}")
    public ResponseEntity<ResponseDto<List<TaskDto>>> getTasksByAssigneeId(@PathVariable Long assigneeId) {
        return ResponseDto.ok(taskService.getTasksByAssigneeId(assigneeId), "Tasks retrieved successfully");
    }

    @Operation(summary = "Update Task")
    @ApiResponse(responseCode = "200", description = "Task updated successfully")
    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<TaskDto>> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskDto taskDto) {
        return ResponseDto.ok(taskService.updateTask(id, taskDto), "Task updated successfully");
    }

    @Operation(summary = "Delete Task")
    @ApiResponse(responseCode = "200", description = "Task deleted successfully")
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<Void>> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseDto.ok("Task deleted successfully");
    }
}
