package com.example.jari.task.service;

import com.example.jari.common.exception.ResourceNotFoundException;
import com.example.jari.task.dto.KanbanBoardDto;
import com.example.jari.task.dto.KanbanColumnDto;
import com.example.jari.task.dto.MoveTaskDto;
import com.example.jari.task.entity.Task;
import com.example.jari.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * moveTask reindexes the whole target column so no two tasks can share an order
 * value. That arithmetic is the most intricate logic in the backend and has no
 * visible failure until a board renders in the wrong sequence, so it is pinned
 * in detail here.
 */
@ExtendWith(MockitoExtension.class)
class KanbanServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private KanbanService kanbanService;

    @BeforeEach
    void mapDtosThroughTheRealMapper() {
        // KanbanService delegates mapping to TaskService; a lenient stub keeps these
        // tests about board arithmetic rather than about field copying.
        org.mockito.Mockito.lenient().when(taskService.mapToDto(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    Task t = inv.getArgument(0);
                    return com.example.jari.task.dto.TaskDto.builder()
                            .id(t.getId())
                            .status(t.getStatus())
                            .order(t.getOrder())
                            .summary(t.getSummary())
                            .build();
                });
    }

    // ---- getBoardByProjectId -------------------------------------------------

    @Test
    void boardAlwaysHasTheThreeStandardColumnsEvenWhenEmpty() {
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of());

        KanbanBoardDto board = kanbanService.getBoardByProjectId(1L);

        assertThat(board.getColumns()).extracting(KanbanColumnDto::getId)
                .containsExactly("TODO", "IN_PROGRESS", "DONE");
        assertThat(board.getColumns()).allSatisfy(c -> assertThat(c.getTasks()).isEmpty());
    }

    @Test
    void columnTitlesAreHumanised() {
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of());

        KanbanBoardDto board = kanbanService.getBoardByProjectId(1L);

        assertThat(board.getColumns()).extracting(KanbanColumnDto::getTitle)
                .containsExactly("Todo", "In Progress", "Done");
    }

    @Test
    void tasksAreGroupedIntoTheirStatusColumn() {
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                task(1L, "TODO", 0), task(2L, "DONE", 0), task(3L, "TODO", 1)));

        KanbanBoardDto board = kanbanService.getBoardByProjectId(1L);

        assertThat(columnById(board, "TODO").getTasks()).extracting("id").containsExactly(1L, 3L);
        assertThat(columnById(board, "IN_PROGRESS").getTasks()).isEmpty();
        assertThat(columnById(board, "DONE").getTasks()).extracting("id").containsExactly(2L);
    }

    @Test
    void tasksAreSortedByOrderThenId() {
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                task(3L, "TODO", 2), task(1L, "TODO", 1), task(2L, "TODO", 1)));

        KanbanBoardDto board = kanbanService.getBoardByProjectId(1L);

        // order 1 before order 2; the two order-1 tasks tie-break on id.
        assertThat(columnById(board, "TODO").getTasks()).extracting("id").containsExactly(1L, 2L, 3L);
    }

    @Test
    void aNullOrderSortsLastRatherThanCrashing() {
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                task(1L, "TODO", null), task(2L, "TODO", 0)));

        KanbanBoardDto board = kanbanService.getBoardByProjectId(1L);

        assertThat(columnById(board, "TODO").getTasks()).extracting("id").containsExactly(2L, 1L);
    }

    @Test
    void aTaskWithNoStatusIsTreatedAsTodo() {
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(task(1L, null, 0)));

        KanbanBoardDto board = kanbanService.getBoardByProjectId(1L);

        assertThat(columnById(board, "TODO").getTasks()).extracting("id").containsExactly(1L);
    }

    @Test
    void tasksInAnUnknownStatusAreDroppedNotCrashed() {
        // Nothing should put a task in "ARCHIVED", but a stray row must not take the
        // whole board down with it.
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                task(1L, "ARCHIVED", 0), task(2L, "TODO", 0)));

        KanbanBoardDto board = kanbanService.getBoardByProjectId(1L);

        assertThat(board.getColumns()).flatExtracting(KanbanColumnDto::getTasks)
                .extracting("id").containsExactly(2L);
    }

    // ---- moveTask ------------------------------------------------------------

    @Test
    void movingToAnotherColumnSetsTheNewStatus() {
        Task moved = task(1L, "TODO", 0);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(moved));
        when(taskRepository.findByProjectId(anyLong())).thenReturn(new ArrayList<>(List.of(moved)));

        kanbanService.moveTask(move(1L, "IN_PROGRESS", 0));

        assertThat(moved.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void reindexingLeavesTheTargetColumnWithContiguousOrdersFromZero() {
        Task moved = task(1L, "TODO", 5);
        Task a = task(2L, "DONE", 0);
        Task b = task(3L, "DONE", 1);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(moved));
        when(taskRepository.findByProjectId(anyLong()))
                .thenReturn(new ArrayList<>(List.of(moved, a, b)));

        kanbanService.moveTask(move(1L, "DONE", 1));

        // Inserted between a and b, then the whole column renumbered 0,1,2.
        assertThat(savedTasks()).extracting(Task::getId, Task::getOrder)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2L, 0),
                        org.assertj.core.groups.Tuple.tuple(1L, 1),
                        org.assertj.core.groups.Tuple.tuple(3L, 2));
    }

    @Test
    void aNullTargetIndexAppendsToTheEnd() {
        Task moved = task(1L, "TODO", 0);
        Task existing = task(2L, "DONE", 0);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(moved));
        when(taskRepository.findByProjectId(anyLong()))
                .thenReturn(new ArrayList<>(List.of(moved, existing)));

        kanbanService.moveTask(move(1L, "DONE", null));

        assertThat(savedTasks()).extracting(Task::getId).containsExactly(2L, 1L);
    }

    @Test
    void anOversizedTargetIndexIsClampedInsteadOfThrowing() {
        Task moved = task(1L, "TODO", 0);
        Task existing = task(2L, "DONE", 0);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(moved));
        when(taskRepository.findByProjectId(anyLong()))
                .thenReturn(new ArrayList<>(List.of(moved, existing)));

        kanbanService.moveTask(move(1L, "DONE", 99));

        assertThat(savedTasks()).extracting(Task::getId).containsExactly(2L, 1L);
    }

    @Test
    void aNegativeTargetIndexIsClampedToTheFront() {
        Task moved = task(1L, "TODO", 0);
        Task existing = task(2L, "DONE", 0);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(moved));
        when(taskRepository.findByProjectId(anyLong()))
                .thenReturn(new ArrayList<>(List.of(moved, existing)));

        kanbanService.moveTask(move(1L, "DONE", -5));

        assertThat(savedTasks()).extracting(Task::getId).containsExactly(1L, 2L);
    }

    @Test
    void reorderingWithinTheSameColumnDoesNotDuplicateTheTask() {
        Task a = task(1L, "TODO", 0);
        Task b = task(2L, "TODO", 1);
        Task c = task(3L, "TODO", 2);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(a));
        when(taskRepository.findByProjectId(anyLong()))
                .thenReturn(new ArrayList<>(List.of(a, b, c)));

        kanbanService.moveTask(move(1L, "TODO", 1));

        // A appears once, at its new slot - the moved task is filtered out of the
        // column before being reinserted.
        assertThat(savedTasks()).extracting(Task::getId).containsExactly(2L, 1L, 3L);
    }

    @Test
    void tasksInOtherColumnsAreNotRenumbered() {
        Task moved = task(1L, "TODO", 0);
        Task untouched = task(2L, "IN_PROGRESS", 7);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(moved));
        when(taskRepository.findByProjectId(anyLong()))
                .thenReturn(new ArrayList<>(List.of(moved, untouched)));

        kanbanService.moveTask(move(1L, "DONE", 0));

        assertThat(savedTasks()).extracting(Task::getId).containsExactly(1L);
        assertThat(untouched.getOrder()).isEqualTo(7);
    }

    @Test
    void rejectsAStatusThatIsNotABoardColumn() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task(1L, "TODO", 0)));

        assertThatThrownBy(() -> kanbanService.moveTask(move(1L, "ARCHIVED", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ARCHIVED");

        verify(taskRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAMoveOfATaskThatDoesNotExist() {
        when(taskRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kanbanService.moveTask(move(404L, "TODO", 0)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    // ---- helpers -------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Task> savedTasks() {
        ArgumentCaptor<List<Task>> captor = ArgumentCaptor.forClass(List.class);
        verify(taskRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static KanbanColumnDto columnById(KanbanBoardDto board, String id) {
        return board.getColumns().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no column " + id));
    }

    private static Task task(Long id, String status, Integer order) {
        return Task.builder().id(id).status(status).order(order).projectId(1L).summary("t" + id).build();
    }

    private static MoveTaskDto move(Long taskId, String targetStatus, Integer targetIndex) {
        return MoveTaskDto.builder()
                .taskId(taskId)
                .targetStatus(targetStatus)
                .targetIndex(targetIndex)
                .build();
    }
}
