package com.notifysystem.enums;

/**
 * Notification type determines visual styling on the frontend.
 * Maps to icon/color in the UI toast and list items.
 */
public enum NotificationType {
    INFO,      // Blue — general information
    SUCCESS,   // Green — positive outcome
    WARNING,   // Yellow — attention required
    ALERT,     // Orange — action needed
    ERROR      // Red — something failed
}
