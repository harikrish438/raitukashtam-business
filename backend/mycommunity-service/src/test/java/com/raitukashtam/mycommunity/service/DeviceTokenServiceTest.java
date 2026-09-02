package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.DevicePlatform;
import com.raitukashtam.mycommunity.entity.DeviceToken;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.DeviceTokenRepository;
import com.raitukashtam.mycommunity.request.RegisterDeviceRequest;
import com.raitukashtam.mycommunity.response.DeviceTokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @InjectMocks
    private DeviceTokenService service;

    private static final String IDENTITY_ID = "11111111-1111-1111-1111-111111111111";

    private RegisterDeviceRequest registerRequest() {
        RegisterDeviceRequest request = new RegisterDeviceRequest();
        request.setDeviceId("device-1");
        request.setFcmToken("fcm-token-abc");
        request.setPlatform(DevicePlatform.ANDROID);
        return request;
    }

    @Test
    void registerDevice_createsNewToken_whenNoneExists() {
        when(deviceTokenRepository.findByIdentityIdAndDeviceId(IDENTITY_ID, "device-1")).thenReturn(Optional.empty());
        when(deviceTokenRepository.save(any(DeviceToken.class))).thenAnswer(invocation -> {
            DeviceToken token = invocation.getArgument(0);
            token.setId(1L);
            return token;
        });

        DeviceTokenResponse response = service.registerDevice(registerRequest(), IDENTITY_ID);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getDeviceId()).isEqualTo("device-1");
        assertThat(response.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
    }

    @Test
    void registerDevice_upsertsExistingToken_whenDeviceAlreadyRegistered() {
        DeviceToken existing = new DeviceToken();
        existing.setId(1L);
        existing.setIdentityId(IDENTITY_ID);
        existing.setDeviceId("device-1");
        existing.setFcmToken("old-token");
        existing.setPlatform(DevicePlatform.ANDROID);
        when(deviceTokenRepository.findByIdentityIdAndDeviceId(IDENTITY_ID, "device-1")).thenReturn(Optional.of(existing));
        when(deviceTokenRepository.save(any(DeviceToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceTokenResponse response = service.registerDevice(registerRequest(), IDENTITY_ID);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(existing.getFcmToken()).isEqualTo("fcm-token-abc");
    }

    @Test
    void listMyDevices_returnsCallersDevices() {
        DeviceToken token = new DeviceToken();
        token.setId(1L);
        token.setDeviceId("device-1");
        token.setPlatform(DevicePlatform.ANDROID);
        when(deviceTokenRepository.findByIdentityId(IDENTITY_ID)).thenReturn(List.of(token));

        List<DeviceTokenResponse> result = service.listMyDevices(IDENTITY_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDeviceId()).isEqualTo("device-1");
    }

    @Test
    void unregisterDevice_deletes_whenFound() {
        DeviceToken existing = new DeviceToken();
        existing.setId(1L);
        when(deviceTokenRepository.findByIdentityIdAndDeviceId(IDENTITY_ID, "device-1")).thenReturn(Optional.of(existing));

        service.unregisterDevice("device-1", IDENTITY_ID);

        verify(deviceTokenRepository).delete(existing);
    }

    @Test
    void unregisterDevice_throwsNotFound_whenMissing() {
        when(deviceTokenRepository.findByIdentityIdAndDeviceId(IDENTITY_ID, "device-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unregisterDevice("device-1", IDENTITY_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(deviceTokenRepository, never()).delete(any());
    }
}
