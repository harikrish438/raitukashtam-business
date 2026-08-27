package com.raitukashtam.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RecaptchaConfig {
    
    @Bean
    @ConfigurationProperties(prefix = "google.recaptcha")
    public RecaptchaProperties recaptchaProperties() {
        return new RecaptchaProperties();
    }
    
    @Setter
    @Getter
    public static class RecaptchaProperties {
        // Getters and setters
        private String siteKey;
        private String secretKey;
        private String verifyUrl;

    }
}
