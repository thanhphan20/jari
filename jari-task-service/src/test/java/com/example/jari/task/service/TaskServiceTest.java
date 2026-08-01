package com.example.jari.task.service;

import com.example.jari.common.exception.ResourceNotFoundException;
import com.example.jari.task.dto.TaskDto;
import com.example.jari.task.entity.Task;
import com.example.jari.task.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    @Test
    void createDefaultsStatusToTodoWhenNotSupplied() {
        // The board has no column for a null status, so a task created without one
        // would be invisible.
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        taskService.createTask(TaskDto.builder().key("J-1").summary("s").projectId(1L).build());

        assertThat(savedTask().getStatus()).isEqualTo("TODO");
    }

    @Test
    void createKeepsAnExplicitStatus() {
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        taskService.createTask(TaskDto.builder().key("J-1").status("DONE").projectId(1L).build());

        assertThat(savedTask().getStatus()).isEqualTo("DONE");
    }

    @Test
    void createCopiesEveryClientSuppliedField() {
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        taskService.createTask(TaskDto.builder()
                .key("J-7").summary("summary").description("desc").order(3)
                .priority(4).type(2).status("TODO")
                .projectId(9L).reporterId(11L).assigneeId(12L)
                .build());

        Task saved = savedTask();
        assertThat(saved.getKey()).isEqualTo("J-7");
        assertThat(saved.getSummary()).isEqualTo("summary");
        assertThat(saved.getDescription()).isEqualTo("desc");
        assertThat(saved.getOrder()).isEqualTo(3);
        assertThat(saved.getPriority()).isEqualTo(4);
        assertThat(saved.getType()).isEqualTo(2);
        assertThat(saved.getProjectId()).isEqualTo(9L);
        assertThat(saved.getReporterId()).isEqualTo(11L);
        assertThat(saved.getAssigneeId()).isEqualTo(12L);
    }

    @Test
    void getByIdReturnsTheMappedTask() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task(1L, "J-1")));

        assertThat(taskService.getTaskById(1L).getKey()).isEqualTo("J-1");
    }

    @Test
    void getByIdThrowsWhenAbsent() {
        when(taskRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getByKeyThrowsWhenAbsent() {
        when(taskRepository.findByKey("NOPE-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskByKey("NOPE-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("NOPE-1");
    }

    @Test
    void listQueriesMapEveryRow() {
        when(taskRepository.findAll()).thenReturn(List.of(task(1L, "J-1"), task(2L, "J-2")));

        assertThat(taskService.getAllTasks()).extracting(TaskDto::getKey).containsExactly("J-1", "J-2");
    }

    @Test
    void projectAndAssigneeQueriesDelegateToTheRepository() {
        when(taskRepository.findByProjectId(5L)).thenReturn(List.of(task(1L, "J-1")));
        when(taskRepository.findByAssigneeId(6L)).thenReturn(List.of(task(2L, "J-2")));

        assertThat(taskService.getTasksByProjectId(5L)).hasSize(1);
        assertThat(taskService.getTasksByAssigneeId(6L)).hasSize(1);
    }

    @Test
    void updateOverwritesTheMutableFields() {
        Task existing = task(1L, "J-1");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        taskService.updateTask(1L, TaskDto.builder()
                .summary("new").description("d").order(2).priority(5).type(3)
                .status("DONE").assigneeId(99L)
                .build());

        assertThat(existing.getSummary()).isEqualTo("new");
        assertThat(existing.getStatus()).isEqualTo("DONE");
        assertThat(existing.getAssigneeId()).isEqualTo(99L);
    }

    @Test
    void updateDoesNotChangeTheKeyOrReporter() {
        // Both are identity of the issue rather than editable state; the service
        // deliberately never copies them from the request body.
        Task existing = task(1L, "J-1");
        existing.setReporterId(3L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        taskService.updateTask(1L, TaskDto.builder().key("HACK-9").reporterId(999L).summary("s").build());

        assertThat(existing.getKey()).isEqualTo("J-1");
        assertThat(existing.getReporterId()).isEqualTo(3L);
    }

    @Test
    void updateThrowsWhenAbsent() {
        when(taskRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.updateTask(404L, TaskDto.builder().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesAnExistingTask() {
        Task existing = task(1L, "J-1");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));

        taskService.deleteTask(1L);

        verify(taskRepository).delete(existing);
    }

    @Test
    void deleteThrowsRatherThanSilentlySucceedingOnAMissingTask() {
        when(taskRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.deleteTask(404L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).delete(any());
    }

    private Task savedTask() {
        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Task task(Long id, String key) {
        return Task.builder().id(id).key(key).summary("s").status("TODO").projectId(1L).build();
    }
}
