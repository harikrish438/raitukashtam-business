package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.service.LoginAttemptService;
import com.raitukashtam.auth.service.UserService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Bare login page behind /oauth2/authorize -- see the Phase 4b plan for
 * why a real page (not Spring's zero-config default) is needed: the
 * existing captcha-after-N-attempts protocol needs a widget slot.
 */
@Controller
@Hidden
public class LoginPageController {

    @Autowired
    private UserService userService;
    @Autowired
    private LoginAttemptService loginAttemptService;

    @Value("${google.recaptcha.site-key}")
    private String recaptchaSiteKey;

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                         @RequestParam(required = false) String username,
                         Model model) {
        model.addAttribute("error", error);
        model.addAttribute("username", username);

        boolean captchaRequired = "captcha_required".equals(error);
        if (!captchaRequired && username != null && !username.isBlank()) {
            try {
                User user = userService.findUserByEmail(username);
                captchaRequired = loginAttemptService.isCaptchaRequired(user);
            } catch (Exception ignored) {
                // Unknown username -- no attempt history to check; leave
                // captchaRequired false, same as today's behavior for a
                // nonexistent account.
            }
        }
        model.addAttribute("captchaRequired", captchaRequired);
        model.addAttribute("recaptchaSiteKey", recaptchaSiteKey);
        return "login";
    }
}
