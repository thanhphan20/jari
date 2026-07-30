package com.example.jari.task.controller;

import com.example.jari.common.dto.ResponseDto;
import com.example.jari.task.dto.KanbanBoardDto;
import com.example.jari.task.dto.MoveTaskDto;
import com.example.jari.task.service.KanbanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tasks/kanban")
@RequiredArgsConstructor
public class KanbanController {

    private final KanbanService kanbanService;

    @GetMapping("/{projectId}")
    public ResponseEntity<ResponseDto<KanbanBoardDto>> getBoard(@PathVariable Long projectId) {
        KanbanBoardDto board = kanbanService.getBoardByProjectId(projectId);
        ResponseDto<KanbanBoardDto> response = ResponseDto.<KanbanBoardDto>builder()
                .success(true)
                .message("Kanban board retrieved successfully")
                .data(board)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/move")
    public ResponseEntity<ResponseDto<Void>> moveTask(@Valid @RequestBody MoveTaskDto moveTaskDto) {
        kanbanService.moveTask(moveTaskDto);
        ResponseDto<Void> response = ResponseDto.<Void>builder()
                .success(true)
                .message("Task moved successfully")
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }
}
