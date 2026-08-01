package com.example.jari.task.service;

import com.example.jari.common.exception.ResourceNotFoundException;
import com.example.jari.task.dto.CommentDto;
import com.example.jari.task.entity.Comment;
import com.example.jari.task.repository.CommentRepository;
import com.example.jari.task.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private CommentService commentService;

    @Test
    void listsCommentsOldestFirst() {
        when(taskRepository.existsById(1L)).thenReturn(true);
        when(commentRepository.findByTaskIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(comment(1L, "first"), comment(2L, "second")));

        assertThat(commentService.getCommentsByTaskId(1L))
                .extracting(CommentDto::getBody).containsExactly("first", "second");
    }

    @Test
    void listingRejectsAnUnknownTask() {
        when(taskRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.getCommentsByTaskId(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void addTakesTheAuthorFromTheCallerNotTheRequestBody() {
        // authorId comes from the gateway-injected identity header, so a client
        // cannot post a comment as somebody else.
        when(taskRepository.existsById(1L)).thenReturn(true);
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        commentService.addComment(1L, 7L, CommentDto.builder().body("hi").authorId(999L).build());

        Comment saved = savedComment();
        assertThat(saved.getAuthorId()).isEqualTo(7L);
        assertThat(saved.getTaskId()).isEqualTo(1L);
        assertThat(saved.getBody()).isEqualTo("hi");
    }

    @Test
    void addRejectsAnUnknownTask() {
        when(taskRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.addComment(404L, 1L, CommentDto.builder().body("x").build()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void deleteRemovesAnExistingComment() {
        when(commentRepository.existsById(3L)).thenReturn(true);

        commentService.deleteComment(3L);

        verify(commentRepository).deleteById(3L);
    }

    @Test
    void deleteThrowsRatherThanSilentlySucceedingOnAMissingComment() {
        when(commentRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.deleteComment(404L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(commentRepository, never()).deleteById(any());
    }

    private Comment savedComment() {
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Comment comment(Long id, String body) {
        return Comment.builder().id(id).taskId(1L).authorId(1L).body(body).build();
    }
}
