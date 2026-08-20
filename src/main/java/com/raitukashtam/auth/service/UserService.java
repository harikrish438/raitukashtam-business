package com.raitukashtam.auth.service;

import com.raitukashtam.auth.entity.CredentialType;
import com.raitukashtam.auth.entity.Identity;
import com.raitukashtam.auth.entity.IdentityCredential;
import com.raitukashtam.auth.entity.IdentityStatus;
import com.raitukashtam.auth.entity.MembershipStatus;
import com.raitukashtam.auth.entity.Product;
import com.raitukashtam.auth.entity.ProductMembership;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.exception.AccountLockedException;
import com.raitukashtam.auth.exception.AuthenticationException;
import com.raitukashtam.auth.exception.ResourceAlreadyExistsException;
import com.raitukashtam.auth.exception.ResourceNotFoundException;
import com.raitukashtam.auth.repository.IdentityCredentialRepository;
import com.raitukashtam.auth.repository.IdentityRepository;
import com.raitukashtam.auth.repository.ProductMembershipRepository;
import com.raitukashtam.auth.repository.ProductRepository;
import com.raitukashtam.auth.repository.TenantRepository;
import com.raitukashtam.auth.repository.UserRepository;
import com.raitukashtam.auth.response.UserResponse;
import org.hibernate.Hibernate;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
    private ProductRepository productRepository;
    @Autowired
    private ProductMembershipRepository productMembershipRepository;
    @Autowired
    private IdentityRepository identityRepository;
    @Autowired
    private IdentityCredentialRepository identityCredentialRepository;
    @Autowired
    private RoleService roleService;

    @Autowired
    ModelMapper modelMapper;

    @Value("${raitukashtam.default-product-code}")
    private String defaultProductCode;

    @Transactional
    public User registerUser(String email, String password,
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

        Product defaultProduct = productRepository.findByCode(defaultProductCode)
                .orElseThrow(() -> new ResourceNotFoundException("Default product not found with code: " + defaultProductCode));

        // An Identity may already exist (e.g. a prior Google-only sign-in under
        // this email) -- reuse it and add a PASSWORD credential rather than
        // creating a duplicate Identity for the same person.
        Identity identity = identityRepository.findByPrimaryEmail(email)
                .orElseGet(() -> {
                    Identity newIdentity = new Identity();
                    newIdentity.setPrimaryEmail(email);
                    newIdentity.setPrimaryPhone(mobileNumber);
                    newIdentity.setStatus(IdentityStatus.ACTIVE);
                    return identityRepository.save(newIdentity);
                });

        IdentityCredential passwordCredential = new IdentityCredential();
        passwordCredential.setIdentity(identity);
        passwordCredential.setCredentialType(CredentialType.PASSWORD);
        passwordCredential.setPasswordHash(passwordEncoder.encode(password));
        passwordCredential.setVerified(false);
        identityCredentialRepository.save(passwordCredential);

        User user = new User();
        user.setEmail(email);
        user.setVerified(false);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setMobileNumber(mobileNumber);
        user.setTenant(tenantRepository.findByCode(tenantCode).orElse(null));
        user.setIdentity(identity);
        user.setCreatedBy(email);

        User savedUser = userRepository.save(user);

        ProductMembership membership = new ProductMembership();
        membership.setIdentity(identity);
        membership.setProduct(defaultProduct);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setJoinedAt(LocalDateTime.now());
        productMembershipRepository.save(membership);
        roleService.assignDefaultRole(membership);

        return savedUser;
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

        if (user.isLocked()) {
            throw new AccountLockedException("Account is locked. Please contact support.");
        }

        IdentityCredential credential = identityCredentialRepository
                .findByIdentity_IdAndCredentialType(user.getIdentity().getId(), CredentialType.PASSWORD)
                .orElseThrow(() -> new AuthenticationException("Invalid email or password"));

        if (!passwordEncoder.matches(rawPassword, credential.getPasswordHash())) {
            throw new AuthenticationException("Invalid email or password");
        }

        return user;
    }

    public User findUserByEmail(String username) {
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
    }

    public User findByIdentityId(UUID identityId) {
        return userRepository.findByIdentity_Id(identityId)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid or unknown identity"));
    }

    @Transactional
    public void updatePassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid username or password"));

        Identity identity = user.getIdentity();
        IdentityCredential credential = identityCredentialRepository
                .findByIdentity_IdAndCredentialType(identity.getId(), CredentialType.PASSWORD)
                .orElseGet(() -> {
                    IdentityCredential newCredential = new IdentityCredential();
                    newCredential.setIdentity(identity);
                    newCredential.setCredentialType(CredentialType.PASSWORD);
                    return newCredential;
                });
        credential.setPasswordHash(passwordEncoder.encode(newPassword));
        credential.setVerified(true);
        identityCredentialRepository.save(credential);
    }
    
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Initialize the tenant proxy to avoid LazyInitializationException
        if (user.getTenant() != null) {
            user.getTenant().getCode();
        }

        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .peek(user -> {
                    if (user.getTenant() != null) {
                        user.getTenant().getCode();
                    }
                })
                .map(this::toUserResponse)
                .toList();
    }

    private UserResponse toUserResponse(User user) {
        UserResponse response = modelMapper.map(user, UserResponse.class);
        Identity identity = user.getIdentity();
        if (identity != null) {
            response.setIdentityId(identity.getId().toString());
            response.setPlatformAdmin(identity.isPlatformAdmin());
            productMembershipRepository.findByIdentity_IdAndProduct_Code(identity.getId(), defaultProductCode)
                    .ifPresent(membership -> response.setRoles(roleService.getRoleCodes(membership)));
        }
        return response;
    }
}
