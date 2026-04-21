package com.notifysystem.config;

import com.notifysystem.enums.NotificationPriority;
import com.notifysystem.enums.NotificationType;
import com.notifysystem.model.Notification;
import com.notifysystem.model.User;
import com.notifysystem.repository.NotificationRepository;
import com.notifysystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Seeds initial data on application startup.
 * Creates default admin and demo accounts with sample notifications.
 * Safe to run multiple times (checks before inserting).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedAdmin();
        seedDemoUser();
        log.info("=======================================================");
        log.info("  NotifySystem started successfully");
        log.info("  URL      : http://localhost:8080");
        log.info("  H2 Console: http://localhost:8080/h2-console");
        log.info("  Admin    : admin / admin123 (ROLE_ADMIN)");
        log.info("  Demo     : demo  / demo123  (ROLE_USER)");
        log.info("=======================================================");
    }

    private void seedAdmin() {
        if (userRepository.existsByUsername("admin")) return;

        User admin = User.builder()
            .username("admin")
            .email("admin@notifysystem.com")
            .password(passwordEncoder.encode("admin123"))
            .role("ROLE_ADMIN")
            .enabled(true)
            .createdAt(LocalDateTime.now())
            .build();
        userRepository.save(admin);
        log.info("Admin user seeded.");
    }

    private void seedDemoUser() {
        if (userRepository.existsByUsername("demo")) return;

        User demo = User.builder()
            .username("demo")
            .email("demo@notifysystem.com")
            .password(passwordEncoder.encode("demo123"))
            .role("ROLE_USER")
            .enabled(true)
            .createdAt(LocalDateTime.now())
            .build();
        userRepository.save(demo);

        // Seed sample notifications for demo user
        notificationRepository.save(Notification.builder()
            .title("Welcome to NotifySystem!")
            .message("Your real-time notification dashboard is ready. Notifications will appear here instantly — no page refresh needed.")
            .type(NotificationType.SUCCESS)
            .priority(NotificationPriority.NORMAL)
            .read(false)
            .user(demo)
            .createdAt(LocalDateTime.now().minusMinutes(10))
            .build());

        notificationRepository.save(Notification.builder()
            .title("Scheduled Maintenance Tonight")
            .message("System maintenance is scheduled for 2:00 AM – 3:00 AM UTC. Brief service interruptions may occur.")
            .type(NotificationType.WARNING)
            .priority(NotificationPriority.HIGH)
            .read(false)
            .user(demo)
            .createdAt(LocalDateTime.now().minusMinutes(5))
            .build());

        notificationRepository.save(Notification.builder()
            .title("New Feature: Priority Levels")
            .message("Notifications now support CRITICAL, HIGH, NORMAL, and LOW priority levels for better triage.")
            .type(NotificationType.INFO)
            .priority(NotificationPriority.NORMAL)
            .read(true)
            .readAt(LocalDateTime.now().minusMinutes(2))
            .user(demo)
            .createdAt(LocalDateTime.now().minusMinutes(30))
            .build());

        log.info("Demo user and notifications seeded.");
    }
}
