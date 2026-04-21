package com.notifysystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * NotifySystem — Real-Time Notification System
 * Entry point for the Spring Boot application.
 *
 * Features:
 *  - JWT-secured REST APIs
 *  - WebSocket + STOMP for real-time delivery
 *  - Scheduled notification support
 */
@SpringBootApplication
@EnableScheduling
public class NotificationSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationSystemApplication.class, args);
    }
}
