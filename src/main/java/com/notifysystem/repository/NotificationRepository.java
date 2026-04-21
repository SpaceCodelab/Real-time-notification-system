package com.notifysystem.repository;

import com.notifysystem.model.Notification;
import com.notifysystem.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Paginated notification feed for a user, most recent first.
     * Uses the composite index (user_id, created_at DESC).
     */
    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    /**
     * Unread badge count — uses index (user_id, is_read).
     */
    long countByUserAndReadFalse(User user);

    /**
     * Total notification count per user (used by data initializer).
     */
    long countByUser(User user);

    /**
     * Bulk mark-all-as-read using a single UPDATE statement for efficiency.
     * Sets readAt to current timestamp atomically.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP " +
           "WHERE n.user = :user AND n.read = false")
    int markAllAsRead(@Param("user") User user);
}
