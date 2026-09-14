package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Notification;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.NotificationRepository;
import com.raitukashtam.mycommunity.response.NotificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read/mark-read access to the notification history NotificationService
 * persists -- kept separate from that fire-and-forget @Async push sender
 * since this is plain request-scoped CRUD, same split as
 * DeviceTokenService/NotificationService.
 */
@Service
@Slf4j
public class NotificationHistoryService {
    @Autowired
    private NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine(String callerIdentityId) {
        return notificationRepository.findByRecipientIdentityIdOrderByCreatedAtDesc(callerIdentityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void markRead(Long id, String callerIdentityId) {
        Notification notification = notificationRepository.findById(id)
                .filter(n -> n.getRecipientIdentityId().equals(callerIdentityId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getCommunityId(),
                notification.getCommunityName(),
                notification.getType(),
                notification.getReferenceId(),
                notification.getTitle(),
                notification.getBody(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
