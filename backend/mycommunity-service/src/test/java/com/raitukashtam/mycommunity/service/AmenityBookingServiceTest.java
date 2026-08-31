package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Amenity;
import com.raitukashtam.mycommunity.entity.AmenityBooking;
import com.raitukashtam.mycommunity.entity.AmenityBookingStatus;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.exception.ResourceAlreadyExistsException;
import com.raitukashtam.mycommunity.repository.AmenityBookingRepository;
import com.raitukashtam.mycommunity.repository.AmenityRepository;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.AmenityBookingRequest;
import com.raitukashtam.mycommunity.response.AmenityBookingResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AmenityBookingServiceTest {

    @Mock
    private AmenityBookingRepository bookingRepository;
    @Mock
    private AmenityRepository amenityRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;

    private static final Long COMMUNITY_ID = 1L;
    private static final Long AMENITY_ID = 20L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private AmenityBookingService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        AmenityService amenityService = new AmenityService();
        setField(amenityService, "amenityRepository", amenityRepository);
        setField(amenityService, "communityRepository", communityRepository);
        setField(amenityService, "communityService", communityService);

        AmenityBookingService service = new AmenityBookingService();
        setField(service, "bookingRepository", bookingRepository);
        setField(service, "communityRepository", communityRepository);
        setField(service, "amenityService", amenityService);
        setField(service, "communityService", communityService);
        return service;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Community community(Long id) {
        Community community = new Community();
        community.setId(id);
        community.setName("Green Valley Apartments");
        return community;
    }

    private CommunityMember member(Long id, CommunityRole role) {
        CommunityMember member = new CommunityMember();
        member.setId(id);
        member.setName("Member " + id);
        member.setRole(role);
        member.setStatus(MemberStatus.ACTIVE);
        member.setCommunity(community(COMMUNITY_ID));
        return member;
    }

    private void stubActiveMember(CommunityMember m) {
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
    }

    private Amenity amenity(boolean active) {
        Amenity amenity = new Amenity();
        amenity.setId(AMENITY_ID);
        amenity.setCommunity(community(COMMUNITY_ID));
        amenity.setName("Clubhouse");
        amenity.setActive(active);
        return amenity;
    }

    private AmenityBookingRequest bookingRequest() {
        AmenityBookingRequest request = new AmenityBookingRequest();
        request.setBookingDate(LocalDate.now().plusDays(2));
        request.setSlot("18:00-19:00");
        return request;
    }

    @Test
    void createBooking_savesPendingBooking_whenAmenityActiveAndSlotFree() {
        AmenityBookingService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        when(amenityRepository.findByIdAndCommunity_Id(AMENITY_ID, COMMUNITY_ID)).thenReturn(Optional.of(amenity(true)));
        when(bookingRepository.existsByAmenity_IdAndBookingDateAndSlotAndStatusIn(eq(AMENITY_ID), any(), eq("18:00-19:00"), anyCollection()))
                .thenReturn(false);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(bookingRepository.save(any(AmenityBooking.class))).thenAnswer(invocation -> {
            AmenityBooking b = invocation.getArgument(0);
            b.setId(100L);
            return b;
        });

        AmenityBookingResponse response = service.createBooking(COMMUNITY_ID, AMENITY_ID, bookingRequest(), CALLER_IDENTITY);

        assertThat(response.getStatus()).isEqualTo(AmenityBookingStatus.PENDING);
        assertThat(response.getMemberId()).isEqualTo(6L);
        assertThat(response.getAmenityName()).isEqualTo("Clubhouse");
    }

    @Test
    void createBooking_throwsConflict_whenAmenityInactive() {
        AmenityBookingService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        when(amenityRepository.findByIdAndCommunity_Id(AMENITY_ID, COMMUNITY_ID)).thenReturn(Optional.of(amenity(false)));

        assertThatThrownBy(() -> service.createBooking(COMMUNITY_ID, AMENITY_ID, bookingRequest(), CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_throwsAlreadyExists_whenSlotAlreadyTaken() {
        AmenityBookingService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        when(amenityRepository.findByIdAndCommunity_Id(AMENITY_ID, COMMUNITY_ID)).thenReturn(Optional.of(amenity(true)));
        when(bookingRepository.existsByAmenity_IdAndBookingDateAndSlotAndStatusIn(eq(AMENITY_ID), any(), eq("18:00-19:00"), anyCollection()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createBooking(COMMUNITY_ID, AMENITY_ID, bookingRequest(), CALLER_IDENTITY))
                .isInstanceOf(ResourceAlreadyExistsException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void approveBooking_transitionsToApproved_whenCallerIsAdmin() {
        AmenityBookingService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        AmenityBooking booking = new AmenityBooking();
        booking.setId(100L);
        booking.setCommunity(community(COMMUNITY_ID));
        booking.setAmenity(amenity(true));
        booking.setMember(member(6L, CommunityRole.RESIDENT));
        booking.setBookingDate(LocalDate.now().plusDays(2));
        booking.setSlot("18:00-19:00");
        booking.setStatus(AmenityBookingStatus.PENDING);
        when(bookingRepository.findByIdAndCommunity_Id(100L, COMMUNITY_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(AmenityBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AmenityBookingResponse response = service.approveBooking(COMMUNITY_ID, 100L, CALLER_IDENTITY);

        assertThat(response.getStatus()).isEqualTo(AmenityBookingStatus.APPROVED);
    }

    @Test
    void approveBooking_throwsAccessDenied_whenCallerNotAdmin() {
        AmenityBookingService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);

        assertThatThrownBy(() -> service.approveBooking(COMMUNITY_ID, 100L, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void approveBooking_throwsConflict_whenNotPending() {
        AmenityBookingService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        AmenityBooking booking = new AmenityBooking();
        booking.setId(100L);
        booking.setStatus(AmenityBookingStatus.APPROVED);
        when(bookingRepository.findByIdAndCommunity_Id(100L, COMMUNITY_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.approveBooking(COMMUNITY_ID, 100L, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBooking_cancels_whenCallerIsOwner() {
        AmenityBookingService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        AmenityBooking booking = new AmenityBooking();
        booking.setId(100L);
        booking.setCommunity(community(COMMUNITY_ID));
        booking.setAmenity(amenity(true));
        booking.setMember(resident);
        booking.setBookingDate(LocalDate.now().plusDays(2));
        booking.setSlot("18:00-19:00");
        booking.setStatus(AmenityBookingStatus.PENDING);
        when(bookingRepository.findByIdAndCommunity_Id(100L, COMMUNITY_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(AmenityBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AmenityBookingResponse response = service.cancelBooking(COMMUNITY_ID, 100L, CALLER_IDENTITY);

        assertThat(response.getStatus()).isEqualTo(AmenityBookingStatus.CANCELLED);
    }

    @Test
    void cancelBooking_throwsAccessDenied_whenCallerIsNeitherOwnerNorAdmin() {
        AmenityBookingService service = buildService();
        CommunityMember otherResident = member(7L, CommunityRole.RESIDENT);
        stubActiveMember(otherResident);
        AmenityBooking booking = new AmenityBooking();
        booking.setId(100L);
        booking.setCommunity(community(COMMUNITY_ID));
        booking.setAmenity(amenity(true));
        booking.setMember(member(6L, CommunityRole.RESIDENT));
        booking.setStatus(AmenityBookingStatus.PENDING);
        when(bookingRepository.findByIdAndCommunity_Id(100L, COMMUNITY_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancelBooking(COMMUNITY_ID, 100L, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBooking_throwsConflict_whenAlreadyRejected() {
        AmenityBookingService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        AmenityBooking booking = new AmenityBooking();
        booking.setId(100L);
        booking.setCommunity(community(COMMUNITY_ID));
        booking.setAmenity(amenity(true));
        booking.setMember(resident);
        booking.setStatus(AmenityBookingStatus.REJECTED);
        when(bookingRepository.findByIdAndCommunity_Id(100L, COMMUNITY_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancelBooking(COMMUNITY_ID, 100L, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(bookingRepository, never()).save(any());
    }
}
