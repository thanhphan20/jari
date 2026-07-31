package com.example.jari.task.controller;

import com.example.jari.common.dto.ResponseDto;
import com.example.jari.security.IdentityHeaders;
import com.example.jari.task.dto.CommentDto;
import com.example.jari.task.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tasks/{taskId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<ResponseDto<List<CommentDto>>> getComments(@PathVariable Long taskId) {
        List<CommentDto> comments = commentService.getCommentsByTaskId(taskId);
        ResponseDto<List<CommentDto>> response = ResponseDto.<List<CommentDto>>builder()
                .success(true)
                .message("Comments retrieved successfully")
                .data(comments)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ResponseDto<CommentDto>> addComment(
            @PathVariable Long taskId,
            @RequestHeader(IdentityHeaders.USER_ID) Long authorId,
            @Valid @RequestBody CommentDto commentDto) {
        CommentDto created = commentService.addComment(taskId, authorId, commentDto);
        ResponseDto<CommentDto> response = ResponseDto.<CommentDto>builder()
                .success(true)
                .message("Comment added successfully")
                .data(created)
                .status(HttpStatus.CREATED.value())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<ResponseDto<Void>> deleteComment(@PathVariable Long taskId, @PathVariable Long commentId) {
        commentService.deleteComment(commentId);
        ResponseDto<Void> response = ResponseDto.<Void>builder()
                .success(true)
                .message("Comment deleted successfully")
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }
}
