package com.example.jari.task.controller;

import com.example.jari.common.dto.ResponseDto;
import com.example.jari.security.IdentityHeaders;
import com.example.jari.task.dto.CommentDto;
import com.example.jari.task.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
        return ResponseDto.ok(commentService.getCommentsByTaskId(taskId), "Comments retrieved successfully");
    }

    @PostMapping
    public ResponseEntity<ResponseDto<CommentDto>> addComment(
            @PathVariable Long taskId,
            @RequestHeader(IdentityHeaders.USER_ID) Long authorId,
            @Valid @RequestBody CommentDto commentDto) {
        return ResponseDto.created(commentService.addComment(taskId, authorId, commentDto),
                "Comment added successfully");
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<ResponseDto<Void>> deleteComment(@PathVariable Long taskId, @PathVariable Long commentId) {
        commentService.deleteComment(commentId);
        return ResponseDto.ok("Comment deleted successfully");
    }
}
