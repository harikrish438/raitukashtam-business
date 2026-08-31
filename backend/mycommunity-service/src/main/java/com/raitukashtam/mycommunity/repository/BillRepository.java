package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {

    List<Bill> findByCommunity_IdOrderByPeriodDescCreatedAtDesc(Long communityId);

    List<Bill> findByMember_IdOrderByPeriodDescCreatedAtDesc(Long memberId);

    Optional<Bill> findByIdAndCommunity_Id(Long id, Long communityId);

    boolean existsByCommunity_IdAndPeriod(Long communityId, String period);
}
