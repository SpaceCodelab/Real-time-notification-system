package com.notifysystem.controller;

import com.notifysystem.dto.ApiResponse;
import com.notifysystem.dto.NotificationDTO;
import com.notifysystem.dto.SendNotificationRequest;
import com.notifysystem.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Notification REST Controller
 *
 * All routes require a valid JWT ("Authorization: Bearer <token>").
 *
 * User endpoints (ROLE_USER + ROLE_ADMIN):
 *  GET    /api/notifications            — paginated feed
 *  GET    /api/notifications/unread-count
 *  PATCH  /api/notifications/{id}/read
 *  PATCH  /api/notifications/{id}/unread
 *  POST   /api/notifications/mark-all-read
 *  DELETE /api/notifications/{id}
 *
 * Admin-only endpoints (ROLE_ADMIN):
 *  POST   /api/notifications/send       — send to user or broadcast
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    // ─── User Endpoints ────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationDTO>>> getNotifications(
        Authentication auth,
        @RequestParam(defaultValue = "0")  int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<NotificationDTO> notifications = notificationService.getNotifications(
            auth.getName(),
            PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending())
        );
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(Authentication auth) {
        long count = notificationService.getUnreadCount(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationDTO>> markAsRead(
        @PathVariable Long id,
        Authentication auth
    ) {
        NotificationDTO dto = notificationService.markAsRead(id, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Marked as read", dto));
    }

    @PatchMapping("/{id}/unread")
    public ResponseEntity<ApiResponse<NotificationDTO>> markAsUnread(
        @PathVariable Long id,
        Authentication auth
    ) {
        NotificationDTO dto = notificationService.markAsUnread(id, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Marked as unread", dto));
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(Authentication auth) {
        int updated = notificationService.markAllAsRead(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(Map.of("updated", updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
        @PathVariable Long id,
        Authentication auth
    ) {
        notificationService.deleteNotification(id, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Notification deleted", null));
    }

    // ─── Admin Endpoints ───────────────────────────────────────────────────────

    /**
     * Send a notification to a specific user or broadcast to all.
     * Restricted to ROLE_ADMIN.
     *
     * Body: { title, message, type, priority, targetUsername? }
     *  - targetUsername omitted → broadcast to ALL users
     */
    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<NotificationDTO>> sendNotification(
        @Valid @RequestBody SendNotificationRequest request
    ) {
        log.info("Admin sending notification: title={} target={}",
            request.getTitle(),
            request.getTargetUsername() != null ? request.getTargetUsername() : "BROADCAST");

        NotificationDTO dto = notificationService.sendNotification(request);
        return ResponseEntity.ok(ApiResponse.success("Notification sent successfully", dto));
    }
}
