package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.DeviceToken;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.DeviceTokenRepository;
import com.raitukashtam.mycommunity.request.RegisterDeviceRequest;
import com.raitukashtam.mycommunity.response.DeviceTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Self-service device-token registration -- identity-scoped, not
 * community-scoped, so this deliberately isn't on CommunityController
 * (unlike every prior phase's data). Registering an already-known
 * (identity, deviceId) pair upserts the fcmToken rather than erroring,
 * since a real app re-registers on every launch/token refresh.
 */
@Service
@Slf4j
public class DeviceTokenService {
    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Transactional
    public DeviceTokenResponse registerDevice(RegisterDeviceRequest request, String callerIdentityId) {
        DeviceToken deviceToken = deviceTokenRepository.findByIdentityIdAndDeviceId(callerIdentityId, request.getDeviceId())
                .orElseGet(DeviceToken::new);
        deviceToken.setIdentityId(callerIdentityId);
        deviceToken.setDeviceId(request.getDeviceId());
        deviceToken.setFcmToken(request.getFcmToken());
        deviceToken.setPlatform(request.getPlatform());
        return toResponse(deviceTokenRepository.save(deviceToken));
    }

    @Transactional(readOnly = true)
    public List<DeviceTokenResponse> listMyDevices(String callerIdentityId) {
        return deviceTokenRepository.findByIdentityId(callerIdentityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void unregisterDevice(String deviceId, String callerIdentityId) {
        DeviceToken deviceToken = deviceTokenRepository.findByIdentityIdAndDeviceId(callerIdentityId, deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not registered: " + deviceId));
        deviceTokenRepository.delete(deviceToken);
    }

    private DeviceTokenResponse toResponse(DeviceToken deviceToken) {
        return new DeviceTokenResponse(
                deviceToken.getId(),
                deviceToken.getDeviceId(),
                deviceToken.getPlatform(),
                deviceToken.getCreatedAt());
    }
}
