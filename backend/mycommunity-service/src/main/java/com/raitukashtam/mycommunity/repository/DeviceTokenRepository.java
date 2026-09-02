package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findByIdentityId(String identityId);

    Optional<DeviceToken> findByIdentityIdAndDeviceId(String identityId, String deviceId);
}
