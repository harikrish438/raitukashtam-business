package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBill_Id(Long billId);

    boolean existsByBill_Id(Long billId);

    List<Payment> findByCommunity_IdOrderByPaidAtDesc(Long communityId);

    List<Payment> findByBill_Member_IdOrderByPaidAtDesc(Long memberId);
}
