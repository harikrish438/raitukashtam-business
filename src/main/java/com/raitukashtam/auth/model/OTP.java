package com.raitukashtam.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OTP {
    private String mobileNumber;
    private String otp;
    private LocalDateTime expiryTime;
}
