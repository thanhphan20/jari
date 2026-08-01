package com.example.jari.task.repository;

import com.example.jari.task.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    Optional<Task> findByKey(String key);
    List<Task> findByProjectId(Long projectId);
    List<Task> findByAssigneeId(Long assigneeId);
}
