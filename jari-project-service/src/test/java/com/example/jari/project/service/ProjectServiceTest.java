package com.example.jari.project.service;

import com.example.jari.common.exception.ResourceNotFoundException;
import com.example.jari.project.dto.ProjectDto;
import com.example.jari.project.entity.Project;
import com.example.jari.project.repository.ProjectRepository;
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
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void createAlwaysMarksTheProjectActive() {
        // active is a primitive boolean, so an unset request field would otherwise
        // create a project that is invisible from the moment it exists.
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        projectService.createProject(ProjectDto.builder().key("JARI").name("Jari").build());

        assertThat(savedProject().isActive()).isTrue();
    }

    @Test
    void createCopiesEveryClientSuppliedField() {
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        projectService.createProject(ProjectDto.builder()
                .key("JARI").name("Jari").description("d").avatarUrl("u").leadUserId(4L).build());

        Project saved = savedProject();
        assertThat(saved.getKey()).isEqualTo("JARI");
        assertThat(saved.getName()).isEqualTo("Jari");
        assertThat(saved.getDescription()).isEqualTo("d");
        assertThat(saved.getAvatarUrl()).isEqualTo("u");
        assertThat(saved.getLeadUserId()).isEqualTo(4L);
    }

    @Test
    void getByIdReturnsTheMappedProject() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L, "JARI")));

        assertThat(projectService.getProjectById(1L).getKey()).isEqualTo("JARI");
    }

    @Test
    void getByIdThrowsWhenAbsent() {
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProjectById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getByKeyThrowsWhenAbsent() {
        when(projectRepository.findByKey("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProjectByKey("NOPE"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("NOPE");
    }

    @Test
    void getAllMapsEveryRow() {
        when(projectRepository.findAll())
                .thenReturn(List.of(project(1L, "A"), project(2L, "B")));

        assertThat(projectService.getAllProjects()).extracting(ProjectDto::getKey).containsExactly("A", "B");
    }

    @Test
    void updateOverwritesTheMutableFields() {
        Project existing = project(1L, "JARI");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        projectService.updateProject(1L, ProjectDto.builder()
                .name("Renamed").description("d2").avatarUrl("u2").leadUserId(8L).active(true).build());

        assertThat(existing.getName()).isEqualTo("Renamed");
        assertThat(existing.getDescription()).isEqualTo("d2");
        assertThat(existing.getLeadUserId()).isEqualTo(8L);
    }

    @Test
    void updateDoesNotChangeTheProjectKey() {
        // The key prefixes every issue key in the project; renaming it would orphan them.
        Project existing = project(1L, "JARI");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        projectService.updateProject(1L, ProjectDto.builder().key("OTHER").name("n").active(true).build());

        assertThat(existing.getKey()).isEqualTo("JARI");
    }

    @Test
    void updateCanDeactivateAProject() {
        Project existing = project(1L, "JARI");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        projectService.updateProject(1L, ProjectDto.builder().name("n").active(false).build());

        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void updateThrowsWhenAbsent() {
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.updateProject(404L, ProjectDto.builder().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesAnExistingProject() {
        Project existing = project(1L, "JARI");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));

        projectService.deleteProject(1L);

        verify(projectRepository).delete(existing);
    }

    @Test
    void deleteThrowsRatherThanSilentlySucceedingOnAMissingProject() {
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.deleteProject(404L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(projectRepository, never()).delete(any());
    }

    private Project savedProject() {
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Project project(Long id, String key) {
        return Project.builder().id(id).key(key).name("n").active(true).build();
    }
}
