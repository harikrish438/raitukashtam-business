package com.raitukashtam.mycommunity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
    private String role;
    private Tenant tenant;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String modifiedBy;
}
