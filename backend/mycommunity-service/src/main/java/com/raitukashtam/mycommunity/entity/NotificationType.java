package com.raitukashtam.mycommunity.entity;

/** One per notificationService.notify* call site -- drives the Android client's tap-to-navigate mapping. */
public enum NotificationType {
    ANNOUNCEMENT,
    BILL_GENERATED,
    PAYMENT_RECORDED,
    COMPLAINT_RAISED,
    COMPLAINT_ASSIGNED,
    COMPLAINT_STATUS_UPDATED,
    JOIN_REQUEST_SUBMITTED,
    JOIN_REQUEST_APPROVED,
    JOIN_REQUEST_REJECTED,
    BOOKING_APPROVED,
    BOOKING_REJECTED,
    VISITOR_ARRIVED
}
