package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.client.AuthServiceClient;
import com.raitukashtam.mycommunity.client.AuthUserProfile;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityJoinRequest;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.JoinRequestStatus;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.exception.ResourceAlreadyExistsException;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityJoinRequestRepository;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.JoinRequestRequest;
import com.raitukashtam.mycommunity.response.CommunityMemberResponse;
import com.raitukashtam.mycommunity.response.JoinRequestResponse;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityJoinRequestServiceTest {

    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;
    @Mock
    private CommunityJoinRequestRepository joinRequestRepository;
    @Mock
    private AuthServiceClient authServiceClient;
    @InjectMocks
    private CommunityService communityService;

    private CommunityJoinRequestService joinRequestService;

    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";
    private static final String CALLER_TOKEN = "fake-token";

    private CommunityJoinRequestService buildService() {
        CommunityJoinRequestService service = new CommunityJoinRequestService();
        setField(service, "communityRepository", communityRepository);
        setField(service, "communityMemberRepository", communityMemberRepository);
        setField(service, "joinRequestRepository", joinRequestRepository);
        setField(service, "communityService", communityService);
        setField(service, "authServiceClient", authServiceClient);
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

    @Test
    void createJoinRequest_savesPendingRequest_whenCallerNotAlreadyMember() {
        joinRequestService = buildService();
        when(communityRepository.findById(1L)).thenReturn(Optional.of(community(1L)));
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(joinRequestRepository.existsByCommunity_IdAndRequesterIdentityIdAndStatus(1L, CALLER_IDENTITY, JoinRequestStatus.PENDING))
                .thenReturn(false);
        AuthUserProfile profile = new AuthUserProfile();
        profile.setMobileNumber("9876500001");
        when(authServiceClient.getCurrentUserProfile(CALLER_TOKEN)).thenReturn(profile);
        when(joinRequestRepository.save(any(CommunityJoinRequest.class))).thenAnswer(invocation -> {
            CommunityJoinRequest jr = invocation.getArgument(0);
            jr.setId(10L);
            return jr;
        });

        JoinRequestRequest request = new JoinRequestRequest();
        request.setName("New Resident");

        JoinRequestResponse response = joinRequestService.createJoinRequest(1L, request, CALLER_IDENTITY, CALLER_TOKEN);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getStatus()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(response.getRequesterMobileNumber()).isEqualTo("9876500001");
    }

    @Test
    void createJoinRequest_throwsAlreadyExists_whenCallerAlreadyActiveMember() {
        joinRequestService = buildService();
        CommunityMember existingMember = new CommunityMember();
        when(communityRepository.findById(1L)).thenReturn(Optional.of(community(1L)));
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(existingMember));

        JoinRequestRequest request = new JoinRequestRequest();
        request.setName("New Resident");

        assertThatThrownBy(() -> joinRequestService.createJoinRequest(1L, request, CALLER_IDENTITY, CALLER_TOKEN))
                .isInstanceOf(ResourceAlreadyExistsException.class);
        verify(joinRequestRepository, never()).save(any());
    }

    @Test
    void createJoinRequest_throwsNotFound_whenCommunityMissing() {
        joinRequestService = buildService();
        when(communityRepository.findById(1L)).thenReturn(Optional.empty());

        JoinRequestRequest request = new JoinRequestRequest();
        request.setName("New Resident");

        assertThatThrownBy(() -> joinRequestService.createJoinRequest(1L, request, CALLER_IDENTITY, CALLER_TOKEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listPendingJoinRequests_throwsAccessDenied_whenCallerNotAdmin() {
        joinRequestService = buildService();
        CommunityMember resident = new CommunityMember();
        resident.setRole(CommunityRole.RESIDENT);
        resident.setCommunity(community(1L));
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));

        assertThatThrownBy(() -> joinRequestService.listPendingJoinRequests(1L, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void approveJoinRequest_createsActiveResidentMember_andMarksApproved() {
        joinRequestService = buildService();
        CommunityMember admin = new CommunityMember();
        admin.setRole(CommunityRole.ADMIN);
        admin.setCommunity(community(1L));
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));

        CommunityJoinRequest joinRequest = new CommunityJoinRequest();
        joinRequest.setId(10L);
        joinRequest.setCommunity(community(1L));
        joinRequest.setRequesterIdentityId("33333333-3333-3333-3333-333333333333");
        joinRequest.setRequesterMobileNumber("9876500001");
        joinRequest.setRequesterName("New Resident");
        joinRequest.setStatus(JoinRequestStatus.PENDING);
        when(joinRequestRepository.findByIdAndCommunity_Id(10L, 1L)).thenReturn(Optional.of(joinRequest));
        when(communityMemberRepository.existsByCommunity_IdAndMobileNumber(1L, "9876500001")).thenReturn(false);
        when(communityMemberRepository.save(any(CommunityMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommunityMemberResponse response = joinRequestService.approveJoinRequest(1L, 10L, CALLER_IDENTITY);

        assertThat(response.getRole()).isEqualTo(CommunityRole.RESIDENT);
        assertThat(response.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(joinRequest.getStatus()).isEqualTo(JoinRequestStatus.APPROVED);

        ArgumentCaptor<CommunityJoinRequest> captor = ArgumentCaptor.forClass(CommunityJoinRequest.class);
        verify(joinRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(JoinRequestStatus.APPROVED);
    }

    @Test
    void approveJoinRequest_throwsConflict_whenRequestNotPending() {
        joinRequestService = buildService();
        CommunityMember admin = new CommunityMember();
        admin.setRole(CommunityRole.ADMIN);
        admin.setCommunity(community(1L));
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));

        CommunityJoinRequest joinRequest = new CommunityJoinRequest();
        joinRequest.setId(10L);
        joinRequest.setStatus(JoinRequestStatus.REJECTED);
        when(joinRequestRepository.findByIdAndCommunity_Id(10L, 1L)).thenReturn(Optional.of(joinRequest));

        assertThatThrownBy(() -> joinRequestService.approveJoinRequest(1L, 10L, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(communityMemberRepository, never()).save(any());
    }

    @Test
    void rejectJoinRequest_marksRejected() {
        joinRequestService = buildService();
        CommunityMember admin = new CommunityMember();
        admin.setRole(CommunityRole.ADMIN);
        admin.setCommunity(community(1L));
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));

        CommunityJoinRequest joinRequest = new CommunityJoinRequest();
        joinRequest.setId(10L);
        joinRequest.setStatus(JoinRequestStatus.PENDING);
        when(joinRequestRepository.findByIdAndCommunity_Id(10L, 1L)).thenReturn(Optional.of(joinRequest));

        joinRequestService.rejectJoinRequest(1L, 10L, CALLER_IDENTITY);

        assertThat(joinRequest.getStatus()).isEqualTo(JoinRequestStatus.REJECTED);
    }
}
