package com.notifysystem.dto;

import com.notifysystem.enums.NotificationPriority;
import com.notifysystem.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for sending notifications via REST API or WebSocket /app/notify.
 *
 * targetUsername = null  →  broadcast to ALL users
 * targetUsername = "bob" →  send only to user "bob"
 */
@Data
public class SendNotificationRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200)
    private String title;

    @NotBlank(message = "Message is required")
    @Size(max = 2000)
    private String message;

    @NotNull(message = "Type is required")
    private NotificationType type;

    private NotificationPriority priority = NotificationPriority.NORMAL;

    /**
     * Target username. If null or blank, the notification is broadcast to all users.
     */
    private String targetUsername;
}
