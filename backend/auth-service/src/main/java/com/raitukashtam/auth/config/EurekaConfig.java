package com.raitukashtam.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.netflix.eureka.EurekaClientConfigBean;

@Configuration
public class EurekaConfig {

    @Value("${eureka.client.service-url.default-zone:http://discovery-service:8761/eureka/}")
    private String eurekaUrl;

    @Bean
    public EurekaClientConfigBean eurekaClientConfigBean() {
        EurekaClientConfigBean config = new EurekaClientConfigBean();
        config.setRegisterWithEureka(true);
        config.setFetchRegistry(true);
        config.getServiceUrl().put("defaultZone", eurekaUrl);
        config.setEnabled(true);
        config.setRegistryFetchIntervalSeconds(30);
        return config;
    }
}
