package com.notifysystem.enums;

/**
 * Priority controls urgency and display order in the notification feed.
 * CRITICAL notifications will also trigger persistent toast popups.
 */
public enum NotificationPriority {
    LOW,      // Informational, can be dismissed silently
    NORMAL,   // Standard notification
    HIGH,     // Needs prompt attention
    CRITICAL  // Immediate action required; triggers persistent alert
}
