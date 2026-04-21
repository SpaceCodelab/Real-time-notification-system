package com.notifysystem.dto;

import com.notifysystem.enums.NotificationPriority;
import com.notifysystem.enums.NotificationType;
import com.notifysystem.model.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO returned to clients and sent over WebSocket.
 * Never exposes the User entity directly — only username is exposed.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationDTO {

    private Long id;
    private String title;
    private String message;
    private NotificationType type;
    private NotificationPriority priority;
    private boolean read;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private String username;

    /**
     * Factory method: safely maps entity → DTO.
     * Avoids LazyInitializationException by only accessing username.
     */
    public static NotificationDTO from(Notification notification) {
        return NotificationDTO.builder()
            .id(notification.getId())
            .title(notification.getTitle())
            .message(notification.getMessage())
            .type(notification.getType())
            .priority(notification.getPriority())
            .read(notification.isRead())
            .createdAt(notification.getCreatedAt())
            .readAt(notification.getReadAt())
            .username(notification.getUser().getUsername())
            .build();
    }
}
