package com.raitukashtam.mycommunity.entity;

public enum MemberStatus {
    /** Added by an admin but has never logged in under this mobile number yet. */
    INVITED,
    /** Linked to a real auth-service identity and able to act in this community. */
    ACTIVE
}
