package com.raitukashtam.mycommunity.entity;

/** Strictly linear lifecycle -- OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED, one step at a time, no skipping or reopening in this phase. */
public enum ComplaintStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}
