package com.example.jari.notification.service;

import com.example.jari.common.exception.ResourceNotFoundException;
import com.example.jari.notification.dto.NotificationDto;
import com.example.jari.notification.entity.Notification;
import com.example.jari.notification.repository.NotificationRepository;
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
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createDefaultsTypeToInfoAndReadToFalse() {
        // A notification created as already-read would never reach the user.
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createNotification(
                NotificationDto.builder().userId(1L).title("t").message("m").build());

        Notification saved = savedNotification();
        assertThat(saved.getType()).isEqualTo("INFO");
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    void createKeepsAnExplicitType() {
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createNotification(
                NotificationDto.builder().userId(1L).title("t").type("WARNING").build());

        assertThat(savedNotification().getType()).isEqualTo("WARNING");
    }

    @Test
    void createIgnoresAClientSuppliedReadFlag() {
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createNotification(
                NotificationDto.builder().userId(1L).title("t").read(true).build());

        assertThat(savedNotification().isRead()).isFalse();
    }

    @Test
    void getByIdThrowsWhenAbsent() {
        when(notificationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getNotificationById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void listsAUsersNotifications() {
        when(notificationRepository.findByUserId(1L))
                .thenReturn(List.of(notification(1L, false), notification(2L, true)));

        assertThat(notificationService.getNotificationsByUserId(1L))
                .extracting(NotificationDto::getId).containsExactly(1L, 2L);
    }

    @Test
    void listsOnlyUnreadWhenAsked() {
        when(notificationRepository.findByUserIdAndReadFalse(1L))
                .thenReturn(List.of(notification(1L, false)));

        assertThat(notificationService.getUnreadNotificationsByUserId(1L)).hasSize(1);
    }

    @Test
    void countsUnreadViaTheRepositoryRatherThanLoadingRows() {
        when(notificationRepository.countByUserIdAndReadFalse(1L)).thenReturn(3L);

        assertThat(notificationService.getUnreadCountByUserId(1L)).isEqualTo(3L);
        verify(notificationRepository, never()).findByUserId(any());
    }

    @Test
    void markAsReadFlipsTheFlag() {
        Notification existing = notification(1L, false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.markAsRead(1L);

        assertThat(existing.isRead()).isTrue();
    }

    @Test
    void markAsReadThrowsWhenAbsent() {
        when(notificationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markAllAsReadFlipsEveryUnreadRowInOneSave() {
        Notification a = notification(1L, false);
        Notification b = notification(2L, false);
        when(notificationRepository.findByUserIdAndReadFalse(1L)).thenReturn(List.of(a, b));

        notificationService.markAllAsRead(1L);

        assertThat(a.isRead()).isTrue();
        assertThat(b.isRead()).isTrue();
        verify(notificationRepository).saveAll(List.of(a, b));
    }

    @Test
    void markAllAsReadOnAUserWithNothingUnreadIsANoop() {
        when(notificationRepository.findByUserIdAndReadFalse(1L)).thenReturn(List.of());

        notificationService.markAllAsRead(1L);

        verify(notificationRepository).saveAll(List.of());
    }

    @Test
    void deleteRemovesAnExistingNotification() {
        Notification existing = notification(1L, false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(existing));

        notificationService.deleteNotification(1L);

        verify(notificationRepository).delete(existing);
    }

    @Test
    void deleteThrowsRatherThanSilentlySucceedingOnAMissingNotification() {
        when(notificationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.deleteNotification(404L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(notificationRepository, never()).delete(any());
    }

    private Notification savedNotification() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Notification notification(Long id, boolean read) {
        return Notification.builder().id(id).userId(1L).title("t").message("m").type("INFO").read(read).build();
    }
}
