package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findByCommunity_IdOrderByCreatedAtDesc(Long communityId);

    List<Announcement> findTop10ByCommunity_IdOrderByCreatedAtDesc(Long communityId);

    Optional<Announcement> findByIdAndCommunity_Id(Long id, Long communityId);
}
