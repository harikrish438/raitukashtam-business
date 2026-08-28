package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.exception.ResourceAlreadyExistsException;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.CommunityMemberRequest;
import com.raitukashtam.mycommunity.request.CommunityRequest;
import com.raitukashtam.mycommunity.response.CommunityResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;

    @InjectMocks
    private CommunityService communityService;

    private static final String CALLER_IDENTITY = "11111111-1111-1111-1111-111111111111";

    private CommunityRequest communityRequest() {
        CommunityRequest request = new CommunityRequest();
        request.setName("Green Valley Apartments");
        request.setTotalUnits(100);
        request.setStreet("Street 1");
        request.setArea("Area 1");
        request.setDistrict("District 1");
        request.setState("State 1");
        request.setPincode("500001");
        request.setAdminMobile("9876543210");
        return request;
    }

    private Community communityWithId(Long id) {
        Community community = new Community();
        community.setId(id);
        community.setName("Green Valley Apartments");
        return community;
    }

    @Test
    void createCommunity_savesCommunityAndActiveAdminMember() {
        when(communityRepository.save(any(Community.class))).thenAnswer(invocation -> {
            Community c = invocation.getArgument(0);
            c.setId(1L);
            return c;
        });

        CommunityResponse response = communityService.createCommunity(communityRequest(), CALLER_IDENTITY);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Green Valley Apartments");

        ArgumentCaptor<CommunityMember> memberCaptor = ArgumentCaptor.forClass(CommunityMember.class);
        verify(communityMemberRepository).save(memberCaptor.capture());
        CommunityMember savedAdmin = memberCaptor.getValue();
        assertThat(savedAdmin.getRole()).isEqualTo(CommunityRole.ADMIN);
        assertThat(savedAdmin.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(savedAdmin.getIdentityId()).isEqualTo(CALLER_IDENTITY);
        assertThat(savedAdmin.getMobileNumber()).isEqualTo("9876543210");
    }

    @Test
    void getCommunity_throwsNotFound_whenCommunityMissing() {
        when(communityRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> communityService.getCommunity(1L, CALLER_IDENTITY))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCommunity_throwsAccessDenied_whenCallerNotAnActiveMember() {
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> communityService.getCommunity(1L, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void addMember_throwsAccessDenied_whenCallerIsNotAdmin() {
        CommunityMember owner = new CommunityMember();
        owner.setRole(CommunityRole.OWNER);
        owner.setCommunity(communityWithId(1L));
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(owner));

        CommunityMemberRequest request = new CommunityMemberRequest();
        request.setName("New Owner");
        request.setUnitNumber("A-101");
        request.setMobileNumber("9876500000");

        assertThatThrownBy(() -> communityService.addMember(1L, request, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
        verify(communityMemberRepository, never()).save(any());
    }

    @Test
    void addMember_throwsAlreadyExists_whenMobileNumberAlreadyInCommunity() {
        CommunityMember admin = new CommunityMember();
        admin.setRole(CommunityRole.ADMIN);
        admin.setCommunity(communityWithId(1L));
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        when(communityMemberRepository.existsByCommunity_IdAndMobileNumber(1L, "9876500000")).thenReturn(true);

        CommunityMemberRequest request = new CommunityMemberRequest();
        request.setName("New Owner");
        request.setUnitNumber("A-101");
        request.setMobileNumber("9876500000");

        assertThatThrownBy(() -> communityService.addMember(1L, request, CALLER_IDENTITY))
                .isInstanceOf(ResourceAlreadyExistsException.class);
    }

    @Test
    void removeMember_throwsConflict_whenRemovingLastActiveAdmin() {
        CommunityMember admin = new CommunityMember();
        admin.setRole(CommunityRole.ADMIN);
        admin.setCommunity(communityWithId(1L));
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));

        CommunityMember targetAdmin = new CommunityMember();
        targetAdmin.setId(2L);
        targetAdmin.setRole(CommunityRole.ADMIN);
        when(communityMemberRepository.findByIdAndCommunity_Id(2L, 1L)).thenReturn(Optional.of(targetAdmin));
        when(communityMemberRepository.countByCommunity_IdAndRoleAndStatus(1L, CommunityRole.ADMIN, MemberStatus.ACTIVE))
                .thenReturn(1L);

        assertThatThrownBy(() -> communityService.removeMember(1L, 2L, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(communityMemberRepository, never()).delete(any());
    }

    @Test
    void listMembers_returnsMappedMembers_whenCallerIsActiveMember() {
        CommunityMember admin = new CommunityMember();
        admin.setRole(CommunityRole.ADMIN);
        admin.setCommunity(communityWithId(1L));
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        when(communityMemberRepository.findByCommunity_Id(1L)).thenReturn(List.of(admin));

        List<?> result = communityService.listMembers(1L, CALLER_IDENTITY);

        assertThat(result).hasSize(1);
    }
}
