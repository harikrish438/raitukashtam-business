package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBill_Id(Long billId);

    boolean existsByBill_Id(Long billId);

    List<Payment> findByCommunity_IdOrderByPaidAtDesc(Long communityId);

    List<Payment> findTop10ByCommunity_IdOrderByPaidAtDesc(Long communityId);

    List<Payment> findByBill_Member_IdOrderByPaidAtDesc(Long memberId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.community.id = :communityId")
    BigDecimal sumAmountByCommunity_Id(@Param("communityId") Long communityId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.community.id = :communityId AND p.paidAt >= :start AND p.paidAt < :end")
    BigDecimal sumAmountByCommunity_IdAndPaidAtBetween(
            @Param("communityId") Long communityId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
