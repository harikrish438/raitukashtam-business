package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {
}
