package com.example.jari.task.controller;

import com.example.jari.common.dto.ResponseDto;
import com.example.jari.task.dto.KanbanBoardDto;
import com.example.jari.task.dto.MoveTaskDto;
import com.example.jari.task.service.KanbanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tasks/kanban")
@RequiredArgsConstructor
public class KanbanController {

    private final KanbanService kanbanService;

    @GetMapping("/{projectId}")
    public ResponseEntity<ResponseDto<KanbanBoardDto>> getBoard(@PathVariable Long projectId) {
        return ResponseDto.ok(kanbanService.getBoardByProjectId(projectId), "Kanban board retrieved successfully");
    }

    @PostMapping("/move")
    public ResponseEntity<ResponseDto<Void>> moveTask(@Valid @RequestBody MoveTaskDto moveTaskDto) {
        kanbanService.moveTask(moveTaskDto);
        return ResponseDto.ok("Task moved successfully");
    }
}
