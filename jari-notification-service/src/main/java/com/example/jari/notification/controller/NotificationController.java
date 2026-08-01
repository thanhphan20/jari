package com.example.jari.notification.controller;

import com.example.jari.common.dto.ResponseDto;
import com.example.jari.notification.dto.NotificationDto;
import com.example.jari.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<ResponseDto<NotificationDto>> createNotification(@Valid @RequestBody NotificationDto notificationDto) {
        return ResponseDto.created(notificationService.createNotification(notificationDto),
                "Notification created successfully");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<NotificationDto>> getNotificationById(@PathVariable Long id) {
        return ResponseDto.ok(notificationService.getNotificationById(id), "Notification retrieved successfully");
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ResponseDto<List<NotificationDto>>> getNotificationsByUserId(@PathVariable Long userId) {
        return ResponseDto.ok(notificationService.getNotificationsByUserId(userId),
                "Notifications retrieved successfully");
    }

    @GetMapping("/user/{userId}/unread")
    public ResponseEntity<ResponseDto<List<NotificationDto>>> getUnreadNotificationsByUserId(@PathVariable Long userId) {
        return ResponseDto.ok(notificationService.getUnreadNotificationsByUserId(userId),
                "Unread notifications retrieved successfully");
    }

    @GetMapping("/user/{userId}/unread/count")
    public ResponseEntity<ResponseDto<Long>> getUnreadCountByUserId(@PathVariable Long userId) {
        return ResponseDto.ok(notificationService.getUnreadCountByUserId(userId),
                "Unread count retrieved successfully");
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ResponseDto<NotificationDto>> markAsRead(@PathVariable Long id) {
        return ResponseDto.ok(notificationService.markAsRead(id), "Notification marked as read");
    }

    @PutMapping("/user/{userId}/read-all")
    public ResponseEntity<ResponseDto<Void>> markAllAsRead(@PathVariable Long userId) {
        notificationService.markAllAsRead(userId);
        return ResponseDto.ok("All notifications marked as read");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<Void>> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseDto.ok("Notification deleted successfully");
    }
}
