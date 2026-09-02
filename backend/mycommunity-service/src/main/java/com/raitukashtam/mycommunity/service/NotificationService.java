package com.raitukashtam.mycommunity.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.DeviceToken;
import com.raitukashtam.mycommunity.repository.DeviceTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Sends a push notification to every device a person has registered.
 * Both public methods run @Async so a triggering request (posting an
 * announcement, recording a payment, ...) never blocks on notification
 * delivery. When Firebase isn't configured (see FirebaseConfig), logs
 * what it would have sent instead of failing -- lets every trigger point
 * in this phase be built and exercised before a real Firebase project
 * exists, and starts actually sending the moment one is configured.
 */
@Service
@Slf4j
public class NotificationService {
    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private Optional<FirebaseMessaging> firebaseMessaging;

    @Async
    public void notifyIdentity(String identityId, String title, String body) {
        if (identityId == null) {
            return;
        }
        sendToIdentity(identityId, title, body);
    }

    /** Skips members with no identityId yet (INVITED, never logged in) -- there's no device to reach. */
    @Async
    public void notifyMembers(List<CommunityMember> members, String title, String body) {
        for (CommunityMember member : members) {
            if (member.getIdentityId() != null) {
                sendToIdentity(member.getIdentityId(), title, body);
            }
        }
    }

    private void sendToIdentity(String identityId, String title, String body) {
        for (DeviceToken deviceToken : deviceTokenRepository.findByIdentityId(identityId)) {
            send(deviceToken, title, body);
        }
    }

    private void send(DeviceToken deviceToken, String title, String body) {
        if (firebaseMessaging.isEmpty()) {
            log.info("[push notification NOT sent -- Firebase not configured] to identity {}: \"{}\" / \"{}\"",
                    deviceToken.getIdentityId(), title, body);
            return;
        }
        Message message = Message.builder()
                .setToken(deviceToken.getFcmToken())
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .build();
        try {
            firebaseMessaging.get().send(message);
        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                log.info("Removing stale device token id {} -- FCM reports it's no longer registered", deviceToken.getId());
                deviceTokenRepository.delete(deviceToken);
            } else {
                log.error("Failed to send push notification to device token id {}", deviceToken.getId(), e);
            }
        }
    }
}
