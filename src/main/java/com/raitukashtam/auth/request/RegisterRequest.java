package com.raitukashtam.auth.request;

import com.raitukashtam.auth.model.UserRole;
import lombok.Getter;

import java.util.UUID;

@Getter
public class RegisterRequest {
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private UserRole role;
    private String tenantCode;
    private String mobileNumber;
    private String modifiedBy;
}
