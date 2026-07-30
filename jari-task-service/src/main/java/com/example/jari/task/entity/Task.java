package com.example.jari.task.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String key; // e.g., "PROJ-123"

    @Column(nullable = false)
    private String summary;

    private String description;

    @Column(name = "task_order") // "order" is a reserved word in Postgres and cannot be an unquoted column name
    private Integer order;
    private Integer priority; // 1=Lowest, 2=Low, 3=Medium, 4=High, 5=Highest
    private Integer type; // 1=Task, 2=Bug, 3=Story, 4=Epic
    private String status; // TODO, IN_PROGRESS, DONE, etc.
    private Long projectId;
    private Long reporterId;
    private Long assigneeId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
