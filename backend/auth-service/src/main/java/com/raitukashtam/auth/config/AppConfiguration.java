package com.raitukashtam.auth.config;

import com.raitukashtam.auth.entity.BaseEntity;
import com.raitukashtam.auth.entity.Product;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.response.ProductResponse;
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

        // Configure Product to ProductResponse mapping
        TypeMap<Product, ProductResponse> productMap = modelMapper.createTypeMap(Product.class, ProductResponse.class);
        productMap.addMappings(mapper -> {
            mapper.map(BaseEntity::getCreatedAt, ProductResponse::setCreatedAt);
            mapper.map(BaseEntity::getUpdatedAt, ProductResponse::setUpdatedAt);
            mapper.map(BaseEntity::getModifiedBy, ProductResponse::setModifiedBy);
            mapper.map(BaseEntity::getCreatedBy, ProductResponse::setCreatedBy);
        });
        // Add any custom mappings if needed
        return modelMapper;
    }
}
