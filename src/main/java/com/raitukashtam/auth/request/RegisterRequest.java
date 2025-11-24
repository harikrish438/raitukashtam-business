package com.raitukashtam.auth.request;

import com.raitukashtam.auth.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

import java.util.UUID;

@Getter
public class RegisterRequest {
    @NotBlank(message = "Email is required")
    @Email(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private String email;
    @NotBlank(message = "Password is required")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[_@])[A-Za-z\\d_@]{8,18}$",
            message = "Password must be 8-18 chars, include at least one uppercase, one digit, and one special character (_ or @)."
    )
    private String password;
    @NotBlank(message = "Firstname is required")
    @Pattern(
            regexp = "^.{25}$",
            message = "Field must be exactly 25 characters long"
    )
    private String firstName;
    @NotBlank(message = "Firstname is required")
    @Pattern(
            regexp = "^.{25}$",
            message = "Field must be exactly 25 characters long"
    )
    private String lastName;
    private UserRole role;
    private String tenantCode;
    @NotBlank(message = "Mobile number is required")
    @Pattern(
            regexp = "^[0-9]{10}$",
            message = "Mobile number must be exactly 10 digits"
    )
    private String mobileNumber;
    private String modifiedBy;
}
