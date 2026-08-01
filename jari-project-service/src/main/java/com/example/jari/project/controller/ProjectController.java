package com.example.jari.project.controller;

import com.example.jari.common.dto.ResponseDto;
import com.example.jari.project.dto.ProjectDto;
import com.example.jari.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ResponseDto<ProjectDto>> createProject(@Valid @RequestBody ProjectDto projectDto) {
        return ResponseDto.created(projectService.createProject(projectDto), "Project created successfully");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<ProjectDto>> getProjectById(@PathVariable Long id) {
        return ResponseDto.ok(projectService.getProjectById(id), "Project retrieved successfully");
    }

    @GetMapping("/key/{key}")
    public ResponseEntity<ResponseDto<ProjectDto>> getProjectByKey(@PathVariable String key) {
        return ResponseDto.ok(projectService.getProjectByKey(key), "Project retrieved successfully");
    }

    @GetMapping
    public ResponseEntity<ResponseDto<List<ProjectDto>>> getAllProjects() {
        return ResponseDto.ok(projectService.getAllProjects(), "Projects retrieved successfully");
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<ProjectDto>> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectDto projectDto) {
        return ResponseDto.ok(projectService.updateProject(id, projectDto), "Project updated successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<Void>> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseDto.ok("Project deleted successfully");
    }
}
