package com.raitukashtam.auth.service;

import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.exception.AuthenticationException;
import com.raitukashtam.auth.exception.ResourceAlreadyExistsException;
import com.raitukashtam.auth.exception.ResourceNotFoundException;
import com.raitukashtam.auth.model.UserRole;
import com.raitukashtam.auth.repository.RefreshTokenRepository;
import com.raitukashtam.auth.repository.TenantRepository;
import com.raitukashtam.auth.repository.UserRepository;
import com.raitukashtam.auth.response.UserResponse;
import org.hibernate.Hibernate;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    ModelMapper modelMapper;

    @Transactional
    public User registerUser(String email, String password, UserRole role,
                             String tenantCode, String firstName,
                             String lastName, String mobileNumber) {
        // Verify tenant exists
        tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with code: " + tenantCode));

        if (userRepository.existsByEmail(email)) {
            throw new ResourceAlreadyExistsException("Email already in use");
        }

        if (userRepository.existsByMobileNumber(mobileNumber)) {
            throw new ResourceAlreadyExistsException("Mobile number already in use");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setVerified(false);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setMobileNumber(mobileNumber);
        user.setTenant(tenantRepository.findByCode(tenantCode).orElse(null));
        user.setCreatedBy(email);

        return userRepository.save(user);
    }

    // Add this method to fetch user with tenant
    @Transactional(readOnly = true)
    public User getUserWithTenant(Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    // Initialize the proxy to load tenant
                    Hibernate.initialize(user.getTenant());
                    return user;
                })
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    // In your service
    @Transactional(readOnly = true)
    public UserResponse getUserWithTenantInfo(Long userId) {
        User user = userRepository.findWithTenantById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return modelMapper.map(user, UserResponse.class);
    }

    @Transactional(readOnly = true)
    public User authenticate(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationException("Invalid email or password"));

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new AuthenticationException("Invalid email or password");
        }

        return user;
    }

    public User findUserByEmail(String username) {
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
    }

    @Transactional
    public void deleteByUsername(String username) {
        refreshTokenRepository.deleteByUsername(username);
    }
    
    @Transactional
    public void updatePassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid username or password"));
        
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
    
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
                
        // Initialize the tenant proxy to avoid LazyInitializationException
        if (user.getTenant() != null) {
            user.getTenant().getCode();
        }
        
        return modelMapper.map(user, UserResponse.class);
    }
}
