package com.raitukashtam.auth.config;

import com.raitukashtam.auth.entity.BaseEntity;
import com.raitukashtam.auth.entity.Tenant;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.response.TenantResponse;
import com.raitukashtam.auth.response.UserResponse;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfiguration {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        // Configure User to UserResponse mapping
        TypeMap<User, UserResponse> userMap = modelMapper.createTypeMap(User.class, UserResponse.class);
        userMap.addMappings(mapper -> {
            mapper.map(BaseEntity::getCreatedAt, UserResponse::setCreatedAt);
            mapper.map(BaseEntity::getUpdatedAt, UserResponse::setUpdatedAt);
            mapper.map(BaseEntity::getModifiedBy, UserResponse::setModifiedBy);
            mapper.map(BaseEntity::getCreatedBy, UserResponse::setCreatedBy);
        });

        // Configure Tenant to TenantResponse mapping
        TypeMap<Tenant, TenantResponse> tenantMap = modelMapper.createTypeMap(Tenant.class, TenantResponse.class);
        tenantMap.addMappings(mapper -> {
            mapper.map(BaseEntity::getCreatedAt, TenantResponse::setCreatedAt);
            mapper.map(BaseEntity::getUpdatedAt, TenantResponse::setUpdatedAt);
            mapper.map(BaseEntity::getModifiedBy, TenantResponse::setModifiedBy);
            mapper.map(BaseEntity::getCreatedBy, TenantResponse::setModifiedBy);
            // Map other fields as needed
        });
        // Add any custom mappings if needed
        return modelMapper;
    }
}
