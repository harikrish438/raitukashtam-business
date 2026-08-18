package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.model.UserRole;
import com.raitukashtam.auth.request.RegisterRequest;
import com.raitukashtam.auth.response.UserResponse;
import com.raitukashtam.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerUser(
                request.getEmail(),
                request.getPassword(),
                request.getRole() != null ? request.getRole() : UserRole.CONSUMER,
                request.getTenantCode(),
                request.getFirstName(),
                request.getLastName(),
                request.getMobileNumber()
        );
        return ResponseEntity.ok("User registered successfully");
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable("id") Long userId) {
        UserResponse userResponse = userService.getUserById(userId);
        return ResponseEntity.ok(userResponse);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }
}
