package com.raitukashtam.auth.response;

import com.raitukashtam.auth.entity.Tenant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private List<String> roles = List.of();
    private boolean platformAdmin = false;
    private TenantResponse tenant;
    private boolean verified = false; // Default to false
    private String mobileNumber;
    private String identityId;
    private String createdAt;
    private String updatedAt;
    private String createdBy;
    private String modifiedBy;
}
