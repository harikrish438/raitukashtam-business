package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.model.UserRole;
import com.raitukashtam.auth.request.RegisterRequest;
import com.raitukashtam.auth.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest request) {
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
}
