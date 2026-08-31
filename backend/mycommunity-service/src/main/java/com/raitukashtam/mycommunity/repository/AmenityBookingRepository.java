package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.AmenityBooking;
import com.raitukashtam.mycommunity.entity.AmenityBookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AmenityBookingRepository extends JpaRepository<AmenityBooking, Long> {

    List<AmenityBooking> findByCommunity_IdOrderByBookingDateDescCreatedAtDesc(Long communityId);

    List<AmenityBooking> findByMember_IdOrderByBookingDateDescCreatedAtDesc(Long memberId);

    Optional<AmenityBooking> findByIdAndCommunity_Id(Long id, Long communityId);

    boolean existsByAmenity_IdAndBookingDateAndSlotAndStatusIn(
            Long amenityId, LocalDate bookingDate, String slot, Collection<AmenityBookingStatus> statuses);
}
