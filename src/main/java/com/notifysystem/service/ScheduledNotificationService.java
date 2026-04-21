package com.notifysystem.service;

import com.notifysystem.enums.NotificationPriority;
import com.notifysystem.enums.NotificationType;
import com.notifysystem.model.User;
import com.notifysystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Scheduled Notification Service
 *
 * Demonstrates event-driven and time-based notification triggers.
 * Uses Spring's @Scheduled with both cron expressions and fixedRate.
 *
 * Production considerations:
 *  - Use a distributed scheduler (Quartz, ShedLock) in multi-instance deployments
 *  - Store scheduled jobs in DB for auditability and retry
 *  - ShedLock annotation recommended to prevent duplicate fires across pods
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledNotificationService {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final NotificationService notificationService;
    private final UserRepository      userRepository;

    /**
     * Daily morning digest — fires at 09:00 every day.
     * In production, filter by user preferences (timezone, mute settings).
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void sendDailyDigest() {
        log.info("Sending daily digest notifications...");
        String time = LocalDateTime.now().format(FORMATTER);
        List<User> users = userRepository.findAll();
        for (User user : users) {
            notificationService.deliverToUser(user,
                "Good Morning — Daily Digest",
                "Here is your system summary for " + time + ". All services are operating normally.",
                NotificationType.INFO,
                NotificationPriority.LOW
            );
        }
        log.info("Daily digest sent to {} users.", users.size());
    }

    /**
     * Security reminder — fires every Monday at 10:00.
     * Reminds users to review their account security.
     */
    @Scheduled(cron = "0 0 10 ? * MON")
    public void sendWeeklySecurityReminder() {
        log.info("Sending weekly security reminders...");
        List<User> users = userRepository.findAll();
        for (User user : users) {
            notificationService.deliverToUser(user,
                "Weekly Security Reminder",
                "Review your account activity and ensure your password is up to date.",
                NotificationType.WARNING,
                NotificationPriority.NORMAL
            );
        }
    }

    /**
     * System heartbeat — internal health log every 5 minutes.
     * Does NOT send user-facing notifications; used for operational monitoring.
     */
    @Scheduled(fixedRate = 300_000)
    public void systemHeartbeat() {
        log.debug("Heartbeat — system operational at {}",
            LocalDateTime.now().format(FORMATTER));
    }
}
