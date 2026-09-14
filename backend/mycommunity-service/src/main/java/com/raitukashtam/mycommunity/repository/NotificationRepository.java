package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdentityIdOrderByCreatedAtDesc(String recipientIdentityId);
}
