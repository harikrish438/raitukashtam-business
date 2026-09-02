package com.raitukashtam.mycommunity.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.DevicePlatform;
import com.raitukashtam.mycommunity.entity.DeviceToken;
import com.raitukashtam.mycommunity.repository.DeviceTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;
    @Mock
    private FirebaseMessaging firebaseMessaging;

    private static final String IDENTITY_ID = "11111111-1111-1111-1111-111111111111";

    private NotificationService buildService(boolean firebaseConfigured) {
        NotificationService service = new NotificationService();
        setField(service, "deviceTokenRepository", deviceTokenRepository);
        setField(service, "firebaseMessaging", firebaseConfigured ? Optional.of(firebaseMessaging) : Optional.empty());
        return service;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private DeviceToken deviceToken(Long id) {
        DeviceToken token = new DeviceToken();
        token.setId(id);
        token.setIdentityId(IDENTITY_ID);
        token.setDeviceId("device-" + id);
        token.setFcmToken("fcm-token-" + id);
        token.setPlatform(DevicePlatform.ANDROID);
        return token;
    }

    @Test
    void notifyIdentity_doesNothing_whenIdentityIdIsNull() {
        NotificationService service = buildService(true);

        service.notifyIdentity(null, "Title", "Body");

        verifyNoInteractions(deviceTokenRepository);
    }

    @Test
    void notifyIdentity_logsOnly_whenFirebaseNotConfigured() throws FirebaseMessagingException {
        NotificationService service = buildService(false);
        when(deviceTokenRepository.findByIdentityId(IDENTITY_ID)).thenReturn(List.of(deviceToken(1L)));

        service.notifyIdentity(IDENTITY_ID, "Title", "Body");

        verifyNoInteractions(firebaseMessaging);
        verify(deviceTokenRepository, never()).delete(any());
    }

    @Test
    void notifyIdentity_sendsToEachRegisteredDevice_whenFirebaseConfigured() throws FirebaseMessagingException {
        NotificationService service = buildService(true);
        DeviceToken token1 = deviceToken(1L);
        DeviceToken token2 = deviceToken(2L);
        when(deviceTokenRepository.findByIdentityId(IDENTITY_ID)).thenReturn(List.of(token1, token2));
        when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id");

        service.notifyIdentity(IDENTITY_ID, "Title", "Body");

        verify(firebaseMessaging, times(2)).send(any(Message.class));
        verify(deviceTokenRepository, never()).delete(any());
    }

    @Test
    void notifyIdentity_removesStaleToken_whenFcmReportsUnregistered() throws FirebaseMessagingException {
        NotificationService service = buildService(true);
        DeviceToken token = deviceToken(1L);
        when(deviceTokenRepository.findByIdentityId(IDENTITY_ID)).thenReturn(List.of(token));
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

        service.notifyIdentity(IDENTITY_ID, "Title", "Body");

        verify(deviceTokenRepository).delete(token);
    }

    @Test
    void notifyIdentity_keepsToken_whenFcmReportsOtherError() throws FirebaseMessagingException {
        NotificationService service = buildService(true);
        DeviceToken token = deviceToken(1L);
        when(deviceTokenRepository.findByIdentityId(IDENTITY_ID)).thenReturn(List.of(token));
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INTERNAL);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

        service.notifyIdentity(IDENTITY_ID, "Title", "Body");

        verify(deviceTokenRepository, never()).delete(any());
    }

    @Test
    void notifyMembers_skipsMembersWithNoIdentityId() {
        NotificationService service = buildService(true);
        CommunityMember withIdentity = new CommunityMember();
        withIdentity.setIdentityId(IDENTITY_ID);
        CommunityMember withoutIdentity = new CommunityMember();
        withoutIdentity.setIdentityId(null);
        when(deviceTokenRepository.findByIdentityId(IDENTITY_ID)).thenReturn(List.of());

        service.notifyMembers(List.of(withIdentity, withoutIdentity), "Title", "Body");

        verify(deviceTokenRepository, times(1)).findByIdentityId(any());
        verify(deviceTokenRepository).findByIdentityId(IDENTITY_ID);
    }
}
