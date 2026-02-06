package com.raitukashtam.auth.response;

import com.raitukashtam.auth.entity.Tenant;
import com.raitukashtam.auth.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private UserRole role = UserRole.CONSUMER; // Default role
    private TenantResponse tenant;
    private boolean verified = false; // Default to false
    private String mobileNumber;
    private String createdAt;
    private String updatedAt;
    private String createdBy;
    private String modifiedBy;
}
