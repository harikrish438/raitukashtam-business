package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Bill;
import com.raitukashtam.mycommunity.entity.BillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {

    List<Bill> findByCommunity_IdOrderByPeriodDescCreatedAtDesc(Long communityId);

    List<Bill> findByMember_IdOrderByPeriodDescCreatedAtDesc(Long memberId);

    Optional<Bill> findByIdAndCommunity_Id(Long id, Long communityId);

    boolean existsByCommunity_IdAndPeriod(Long communityId, String period);

    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Bill b WHERE b.community.id = :communityId AND b.status = :status")
    BigDecimal sumAmountByCommunity_IdAndStatus(@Param("communityId") Long communityId, @Param("status") BillStatus status);
}
