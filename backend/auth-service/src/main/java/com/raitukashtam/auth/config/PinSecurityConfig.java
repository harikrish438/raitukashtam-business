package com.raitukashtam.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "security.pin")
public class PinSecurityConfig {
    private int maxAttempts = 5;
    private Duration lockoutDuration = Duration.ofMinutes(15);
}
