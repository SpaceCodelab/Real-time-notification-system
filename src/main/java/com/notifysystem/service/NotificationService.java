package com.notifysystem.service;

import com.notifysystem.dto.NotificationDTO;
import com.notifysystem.dto.SendNotificationRequest;
import com.notifysystem.enums.NotificationPriority;
import com.notifysystem.enums.NotificationType;
import com.notifysystem.exception.ResourceNotFoundException;
import com.notifysystem.model.Notification;
import com.notifysystem.model.User;
import com.notifysystem.repository.NotificationRepository;
import com.notifysystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Notification Service — core business logic.
 *
 * Dual-delivery model:
 *  1. Persist to DB (always)  → guarantees durability and history
 *  2. Push via WebSocket      → delivers real-time if user is connected
 *
 * If the user is not connected when a notification is sent,
 * it will be fetched from DB the next time they load the dashboard.
 * This ensures zero message loss.
 *
 * WebSocket destinations:
 *  - User-specific : /user/{username}/queue/notifications
 *  - Broadcast     : /topic/broadcast
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository         userRepository;
    private final SimpMessagingTemplate  messagingTemplate;

    // ─── Send ──────────────────────────────────────────────────────────────────

    /**
     * Sends a notification based on the request.
     *  - targetUsername null/blank → broadcast to all users
     *  - targetUsername set        → deliver to a specific user
     */
    @Transactional
    public NotificationDTO sendNotification(SendNotificationRequest req) {
        if (req.getTargetUsername() != null && !req.getTargetUsername().isBlank()) {
            User target = userRepository.findByUsername(req.getTargetUsername())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Target user not found: " + req.getTargetUsername()));
            return deliverToUser(target, req.getTitle(), req.getMessage(),
                req.getType(), req.getPriority());
        } else {
            return broadcast(req.getTitle(), req.getMessage(), req.getType(), req.getPriority());
        }
    }

    /**
     * Broadcasts a notification to every user in the system.
     * Also pushes to /topic/broadcast for subscribers.
     */
    @Transactional
    public NotificationDTO broadcast(String title, String message,
                                     NotificationType type, NotificationPriority priority) {
        List<User> allUsers = userRepository.findAll();
        NotificationDTO last = null;

        for (User user : allUsers) {
            last = deliverToUser(user, title, message, type, priority);
        }

        // Also publish to the broadcast topic (non-persistent WS event)
        if (last != null) {
            messagingTemplate.convertAndSend("/topic/broadcast", last);
        }

        log.info("Broadcast sent to {} users: title={}", allUsers.size(), title);
        return last;
    }

    /**
     * Core delivery method: persist + push via WebSocket.
     */
    @Transactional
    public NotificationDTO deliverToUser(User user, String title, String message,
                                         NotificationType type, NotificationPriority priority) {
        Notification notification = Notification.builder()
            .title(title)
            .message(message)
            .type(type)
            .priority(priority != null ? priority : NotificationPriority.NORMAL)
            .read(false)
            .user(user)
            .createdAt(LocalDateTime.now())
            .build();

        notification = notificationRepository.save(notification);
        NotificationDTO dto = NotificationDTO.from(notification);

        // Push to user's private queue — no-op if user is not connected
        messagingTemplate.convertAndSendToUser(
            user.getUsername(),
            "/queue/notifications",
            dto
        );

        log.debug("Notification delivered: user={} title={} priority={}",
            user.getUsername(), title, priority);
        return dto;
    }

    /**
     * Sends welcome notification on registration.
     * Fire-and-forget (errors do not fail registration).
     */
    public void sendWelcomeNotification(User user) {
        try {
            deliverToUser(user,
                "Welcome to NotifySystem! 🎉",
                "Your account is ready. You'll receive real-time notifications here — no page refresh needed.",
                NotificationType.SUCCESS,
                NotificationPriority.NORMAL
            );
        } catch (Exception e) {
            log.error("Failed to send welcome notification to {}: {}", user.getUsername(), e.getMessage());
        }
    }

    // ─── Read ──────────────────────────────────────────────────────────────────

    /**
     * Paginated notification feed for a user, newest first.
     */
    @Transactional(readOnly = true)
    public Page<NotificationDTO> getNotifications(String username, Pageable pageable) {
        User user = findUser(username);
        return notificationRepository
            .findByUserOrderByCreatedAtDesc(user, pageable)
            .map(NotificationDTO::from);
    }

    /**
     * Count of unread notifications for badge display.
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(String username) {
        User user = findUser(username);
        return notificationRepository.countByUserAndReadFalse(user);
    }

    // ─── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public NotificationDTO markAsRead(Long id, String username) {
        Notification n = findOwned(id, username);
        n.setRead(true);
        n.setReadAt(LocalDateTime.now());
        return NotificationDTO.from(notificationRepository.save(n));
    }

    @Transactional
    public NotificationDTO markAsUnread(Long id, String username) {
        Notification n = findOwned(id, username);
        n.setRead(false);
        n.setReadAt(null);
        return NotificationDTO.from(notificationRepository.save(n));
    }

    @Transactional
    public int markAllAsRead(String username) {
        User user = findUser(username);
        int updated = notificationRepository.markAllAsRead(user);
        log.info("Marked all as read: user={} count={}", username, updated);
        return updated;
    }

    // ─── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteNotification(Long id, String username) {
        Notification n = findOwned(id, username);
        notificationRepository.delete(n);
        log.debug("Notification deleted: id={} user={}", id, username);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private User findUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private Notification findOwned(Long id, String username) {
        Notification n = notificationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        if (!n.getUser().getUsername().equals(username)) {
            throw new SecurityException("Access denied to notification: " + id);
        }
        return n;
    }
}
