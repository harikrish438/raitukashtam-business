package com.raitukashtam.mycommunity.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.DeviceToken;
import com.raitukashtam.mycommunity.entity.Notification;
import com.raitukashtam.mycommunity.entity.NotificationType;
import com.raitukashtam.mycommunity.repository.DeviceTokenRepository;
import com.raitukashtam.mycommunity.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Persists a Notification row for every recipient (so the in-app history
 * is never lossy, even with no device token or Firebase unconfigured) and
 * sends a push to every device a person has registered. Both public
 * methods run @Async so a triggering request (posting an announcement,
 * recording a payment, ...) never blocks on notification delivery. When
 * Firebase isn't configured (see FirebaseConfig), logs what it would have
 * sent instead of failing.
 */
@Service
@Slf4j
public class NotificationService {
    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private Optional<FirebaseMessaging> firebaseMessaging;

    @Async
    public void notifyIdentity(String identityId, String title, String body,
                                Long communityId, String communityName, NotificationType type, Long referenceId) {
        if (identityId == null) {
            return;
        }
        persist(identityId, title, body, communityId, communityName, type, referenceId);
        sendToIdentity(identityId, title, body, communityId, communityName, type, referenceId);
    }

    /** Skips members with no identityId yet (INVITED, never logged in) -- there's no device to reach. */
    @Async
    public void notifyMembers(List<CommunityMember> members, String title, String body,
                               Long communityId, String communityName, NotificationType type, Long referenceId) {
        for (CommunityMember member : members) {
            if (member.getIdentityId() != null) {
                persist(member.getIdentityId(), title, body, communityId, communityName, type, referenceId);
                sendToIdentity(member.getIdentityId(), title, body, communityId, communityName, type, referenceId);
            }
        }
    }

    private void persist(String identityId, String title, String body,
                          Long communityId, String communityName, NotificationType type, Long referenceId) {
        Notification notification = new Notification();
        notification.setRecipientIdentityId(identityId);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setCommunityId(communityId);
        notification.setCommunityName(communityName);
        notification.setType(type);
        notification.setReferenceId(referenceId);
        notificationRepository.save(notification);
    }

    private void sendToIdentity(String identityId, String title, String body,
                                 Long communityId, String communityName, NotificationType type, Long referenceId) {
        for (DeviceToken deviceToken : deviceTokenRepository.findByIdentityId(identityId)) {
            send(deviceToken, title, body, communityId, communityName, type, referenceId);
        }
    }

    private void send(DeviceToken deviceToken, String title, String body,
                       Long communityId, String communityName, NotificationType type, Long referenceId) {
        if (firebaseMessaging.isEmpty()) {
            log.info("[push notification NOT sent -- Firebase not configured] to identity {}: \"{}\" / \"{}\"",
                    deviceToken.getIdentityId(), title, body);
            return;
        }
        Message message = Message.builder()
                .setToken(deviceToken.getFcmToken())
                .setNotification(com.google.firebase.messaging.Notification.builder().setTitle(title).setBody(body).build())
                .putData("type", type.name())
                .putData("referenceId", referenceId == null ? "" : referenceId.toString())
                .putData("communityId", communityId.toString())
                .putData("communityName", communityName)
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
