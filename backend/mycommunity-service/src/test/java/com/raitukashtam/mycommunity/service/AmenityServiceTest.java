package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Amenity;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.AmenityRepository;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.AmenityRequest;
import com.raitukashtam.mycommunity.response.AmenityResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AmenityServiceTest {

    @Mock
    private AmenityRepository amenityRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;

    private static final Long COMMUNITY_ID = 1L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private AmenityService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        AmenityService service = new AmenityService();
        setField(service, "amenityRepository", amenityRepository);
        setField(service, "communityRepository", communityRepository);
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

    @Test
    void createAmenity_savesFreeAmenity_whenCallerIsAdmin() {
        AmenityService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(amenityRepository.save(any(Amenity.class))).thenAnswer(invocation -> {
            Amenity a = invocation.getArgument(0);
            a.setId(10L);
            return a;
        });

        AmenityRequest request = new AmenityRequest();
        request.setName("Clubhouse");
        request.setDescription("Main community hall");

        AmenityResponse response = service.createAmenity(COMMUNITY_ID, request, CALLER_IDENTITY);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.isPaid()).isFalse();
        assertThat(response.getFee()).isNull();
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void createAmenity_savesPaidAmenity_withFee() {
        AmenityService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(amenityRepository.save(any(Amenity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AmenityRequest request = new AmenityRequest();
        request.setName("Tennis Court");
        request.setPaid(true);
        request.setFee(new BigDecimal("300.00"));

        AmenityResponse response = service.createAmenity(COMMUNITY_ID, request, CALLER_IDENTITY);

        assertThat(response.isPaid()).isTrue();
        assertThat(response.getFee()).isEqualByComparingTo("300.00");
    }

    @Test
    void createAmenity_throwsBadRequest_whenPaidWithNoFee() {
        AmenityService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);

        AmenityRequest request = new AmenityRequest();
        request.setName("Tennis Court");
        request.setPaid(true);

        assertThatThrownBy(() -> service.createAmenity(COMMUNITY_ID, request, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(amenityRepository, never()).save(any());
    }

    @Test
    void createAmenity_throwsAccessDenied_whenCallerNotAdmin() {
        AmenityService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);

        AmenityRequest request = new AmenityRequest();
        request.setName("Clubhouse");

        assertThatThrownBy(() -> service.createAmenity(COMMUNITY_ID, request, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listAmenities_returnsAll_forAnyActiveMember() {
        AmenityService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);

        Amenity amenity = new Amenity();
        amenity.setId(10L);
        amenity.setCommunity(community(COMMUNITY_ID));
        amenity.setName("Clubhouse");
        when(amenityRepository.findByCommunity_IdOrderByNameAsc(COMMUNITY_ID)).thenReturn(List.of(amenity));

        List<AmenityResponse> result = service.listAmenities(COMMUNITY_ID, CALLER_IDENTITY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Clubhouse");
    }

    @Test
    void deactivateAmenity_setsInactive_whenCallerIsAdmin() {
        AmenityService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        Amenity amenity = new Amenity();
        amenity.setId(10L);
        amenity.setCommunity(community(COMMUNITY_ID));
        amenity.setName("Clubhouse");
        amenity.setActive(true);
        when(amenityRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(amenity));
        when(amenityRepository.save(any(Amenity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AmenityResponse response = service.deactivateAmenity(COMMUNITY_ID, 10L, CALLER_IDENTITY);

        assertThat(response.isActive()).isFalse();
    }

    @Test
    void deactivateAmenity_throwsConflict_whenAlreadyInactive() {
        AmenityService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        Amenity amenity = new Amenity();
        amenity.setId(10L);
        amenity.setActive(false);
        when(amenityRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(amenity));

        assertThatThrownBy(() -> service.deactivateAmenity(COMMUNITY_ID, 10L, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(amenityRepository, never()).save(any());
    }

    @Test
    void getAmenity_throwsNotFound_whenMissing() {
        AmenityService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        when(amenityRepository.findByIdAndCommunity_Id(99L, COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAmenity(COMMUNITY_ID, 99L, CALLER_IDENTITY))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
