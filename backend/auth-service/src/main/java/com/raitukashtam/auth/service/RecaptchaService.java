package com.raitukashtam.auth.service;

import com.raitukashtam.auth.config.RecaptchaConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class RecaptchaService {

    private final RestTemplate restTemplate;
    private final RecaptchaConfig.RecaptchaProperties recaptchaProperties;

    @Autowired
    public RecaptchaService(RestTemplate restTemplate, RecaptchaConfig.RecaptchaProperties recaptchaProperties) {
        this.restTemplate = restTemplate;
        this.recaptchaProperties = recaptchaProperties;
    }

    public boolean verifyRecaptcha(String recaptchaResponse) {
        if (recaptchaResponse == null || recaptchaResponse.isEmpty()) {
            return false;
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("secret", recaptchaProperties.getSecretKey());
        params.add("response", recaptchaResponse);

        try {
            Map<String, Object> response = restTemplate.postForObject(
                recaptchaProperties.getVerifyUrl(),
                params,
                Map.class
            );

            return response != null && Boolean.TRUE.equals(response.get("success"));
        } catch (Exception e) {
            return false;
        }
    }
}
