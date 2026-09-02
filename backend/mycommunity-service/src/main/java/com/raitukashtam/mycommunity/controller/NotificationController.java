package com.raitukashtam.mycommunity.controller;

import com.raitukashtam.mycommunity.request.RegisterDeviceRequest;
import com.raitukashtam.mycommunity.response.DeviceTokenResponse;
import com.raitukashtam.mycommunity.service.DeviceTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Device-token registration for push notifications (Phase 14) --
 * deliberately its own controller rather than folded into
 * CommunityController, since a device token is identity-scoped (one
 * person's device works across every community they belong to), not
 * community-scoped like everything CommunityController owns.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Slf4j
public class NotificationController {
    @Autowired
    private DeviceTokenService deviceTokenService;

    @PostMapping("/devices")
    public ResponseEntity<DeviceTokenResponse> registerDevice(@RequestBody @Validated RegisterDeviceRequest request,
                                                                @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside registerDevice method of NotificationController");
        return new ResponseEntity<>(deviceTokenService.registerDevice(request, jwt.getSubject()), HttpStatus.CREATED);
    }

    @GetMapping("/devices")
    public List<DeviceTokenResponse> listMyDevices(@AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listMyDevices method of NotificationController");
        return deviceTokenService.listMyDevices(jwt.getSubject());
    }

    @DeleteMapping("/devices/{deviceId}")
    public ResponseEntity<Void> unregisterDevice(@PathVariable("deviceId") String deviceId,
                                                  @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside unregisterDevice method of NotificationController");
        deviceTokenService.unregisterDevice(deviceId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
