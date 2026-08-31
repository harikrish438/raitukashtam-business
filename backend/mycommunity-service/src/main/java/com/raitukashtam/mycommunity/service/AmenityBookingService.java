package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Amenity;
import com.raitukashtam.mycommunity.entity.AmenityBooking;
import com.raitukashtam.mycommunity.entity.AmenityBookingStatus;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.exception.ResourceAlreadyExistsException;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.AmenityBookingRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.AmenityBookingRequest;
import com.raitukashtam.mycommunity.response.AmenityBookingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Any ACTIVE member books an amenity for themselves; ADMIN approves/
 * rejects (mirrors CommunityJoinRequestService's PENDING/APPROVED/
 * REJECTED lifecycle). The booker or an ADMIN can cancel. Membership
 * authorization is delegated to CommunityService; amenity lookup to
 * AmenityService.requireAmenity (package-private, same package).
 */
@Service
@Slf4j
public class AmenityBookingService {
    @Autowired
    private AmenityBookingRepository bookingRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private AmenityService amenityService;

    @Autowired
    private CommunityService communityService;

    private static final List<AmenityBookingStatus> BLOCKING_STATUSES =
            List.of(AmenityBookingStatus.PENDING, AmenityBookingStatus.APPROVED);

    @Transactional
    public AmenityBookingResponse createBooking(Long communityId, Long amenityId, AmenityBookingRequest request, String callerIdentityId) {
        CommunityMember member = communityService.requireActiveMember(communityId, callerIdentityId);
        Amenity amenity = amenityService.requireAmenity(communityId, amenityId);

        if (!amenity.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Amenity is not currently available for booking");
        }
        if (bookingRepository.existsByAmenity_IdAndBookingDateAndSlotAndStatusIn(
                amenityId, request.getBookingDate(), request.getSlot(), BLOCKING_STATUSES)) {
            throw new ResourceAlreadyExistsException("This amenity is already booked for that date and slot");
        }

        Community community = communityRepository.getReferenceById(communityId);
        AmenityBooking booking = new AmenityBooking();
        booking.setCommunity(community);
        booking.setAmenity(amenity);
        booking.setMember(member);
        booking.setBookingDate(request.getBookingDate());
        booking.setSlot(request.getSlot().trim());
        booking.setStatus(AmenityBookingStatus.PENDING);
        AmenityBooking saved = bookingRepository.save(booking);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AmenityBookingResponse> listBookings(Long communityId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        return bookingRepository.findByCommunity_IdOrderByBookingDateDescCreatedAtDesc(communityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AmenityBookingResponse> listMyBookings(Long communityId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        return bookingRepository.findByMember_IdOrderByBookingDateDescCreatedAtDesc(caller.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AmenityBookingResponse getBooking(Long communityId, Long bookingId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        AmenityBooking booking = requireBooking(communityId, bookingId);
        requireOwnerOrAdmin(booking, caller);
        return toResponse(booking);
    }

    @Transactional
    public AmenityBookingResponse approveBooking(Long communityId, Long bookingId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        AmenityBooking booking = requirePendingBooking(communityId, bookingId);
        booking.setStatus(AmenityBookingStatus.APPROVED);
        return toResponse(bookingRepository.save(booking));
    }

    @Transactional
    public AmenityBookingResponse rejectBooking(Long communityId, Long bookingId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        AmenityBooking booking = requirePendingBooking(communityId, bookingId);
        booking.setStatus(AmenityBookingStatus.REJECTED);
        return toResponse(bookingRepository.save(booking));
    }

    @Transactional
    public AmenityBookingResponse cancelBooking(Long communityId, Long bookingId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        AmenityBooking booking = requireBooking(communityId, bookingId);
        requireOwnerOrAdmin(booking, caller);
        if (booking.getStatus() != AmenityBookingStatus.PENDING && booking.getStatus() != AmenityBookingStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is not in a cancellable status");
        }
        booking.setStatus(AmenityBookingStatus.CANCELLED);
        return toResponse(bookingRepository.save(booking));
    }

    private void requireOwnerOrAdmin(AmenityBooking booking, CommunityMember caller) {
        if (!booking.getMember().getId().equals(caller.getId()) && caller.getRole() != CommunityRole.ADMIN) {
            throw new AccessDeniedException("Not authorized to act on this booking");
        }
    }

    private AmenityBooking requirePendingBooking(Long communityId, Long bookingId) {
        AmenityBooking booking = requireBooking(communityId, bookingId);
        if (booking.getStatus() != AmenityBookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is not pending");
        }
        return booking;
    }

    private AmenityBooking requireBooking(Long communityId, Long bookingId) {
        return bookingRepository.findByIdAndCommunity_Id(bookingId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
    }

    private AmenityBookingResponse toResponse(AmenityBooking booking) {
        return new AmenityBookingResponse(
                booking.getId(),
                booking.getCommunity().getId(),
                booking.getAmenity().getId(),
                booking.getAmenity().getName(),
                booking.getMember().getId(),
                booking.getMember().getName(),
                booking.getBookingDate(),
                booking.getSlot(),
                booking.getStatus(),
                booking.getCreatedAt());
    }
}
