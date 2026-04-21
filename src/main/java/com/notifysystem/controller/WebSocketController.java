package com.notifysystem.controller;

import com.notifysystem.dto.SendNotificationRequest;
import com.notifysystem.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket Message Controller
 *
 * Handles inbound STOMP messages from connected clients.
 * All handlers are prefixed with /app (configured in WebSocketConfig).
 *
 * Available destinations:
 *  /app/notify  — Admin sends a notification via WebSocket
 *  /app/ping    — Client heartbeat / connection check
 *
 * Note: @MessageMapping does not return a value for broadcast —
 *       responses are pushed via SimpMessagingTemplate in NotificationService.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final NotificationService notificationService;

    /**
     * Handles admin notification send over WebSocket.
     * Only principals with ROLE_ADMIN may send.
     */
    @MessageMapping("/notify")
    public void handleNotification(
        @Payload SendNotificationRequest request,
        Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required");
        }

        // Check admin role from granted authorities
        boolean isAdmin = ((org.springframework.security.core.Authentication) principal)
            .getAuthorities()
            .stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            log.warn("Non-admin WebSocket send attempt: user={}", principal.getName());
            throw new AccessDeniedException("Only admins can send notifications");
        }

        log.info("WebSocket notify from admin={}: title={}", principal.getName(), request.getTitle());
        notificationService.sendNotification(request);
    }

    /**
     * Heartbeat ping — used by clients to check connection health.
     * Can be extended to track active sessions.
     */
    @MessageMapping("/ping")
    public void handlePing(SimpMessageHeaderAccessor headerAccessor) {
        String user = headerAccessor.getUser() != null
            ? headerAccessor.getUser().getName()
            : "anonymous";
        log.debug("WS ping from: {}", user);
    }
}
