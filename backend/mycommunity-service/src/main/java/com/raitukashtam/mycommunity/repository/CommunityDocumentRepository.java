package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.CommunityDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityDocumentRepository extends JpaRepository<CommunityDocument, Long> {

    List<CommunityDocument> findByCommunity_IdOrderByCreatedAtDesc(Long communityId);

    Optional<CommunityDocument> findByIdAndCommunity_Id(Long id, Long communityId);
}
