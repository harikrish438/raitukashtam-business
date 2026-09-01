package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.client.AuthServiceClient;
import com.raitukashtam.mycommunity.client.AuthUserProfile;
import com.raitukashtam.mycommunity.entity.BillingMode;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.exception.DuplicateCommunityException;
import com.raitukashtam.mycommunity.exception.ResourceAlreadyExistsException;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.BillingSettingsRequest;
import com.raitukashtam.mycommunity.request.CommunityMemberRequest;
import com.raitukashtam.mycommunity.request.CommunityRequest;
import com.raitukashtam.mycommunity.request.MemberProfileUpdateRequest;
import com.raitukashtam.mycommunity.response.CommunityMemberResponse;
import com.raitukashtam.mycommunity.response.CommunityResponse;
import com.raitukashtam.mycommunity.response.MyCommunityResponse;
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
class CommunityServiceTest {

    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;
    @Mock
    private AuthServiceClient authServiceClient;

    @InjectMocks
    private CommunityService communityService;

    private static final String CALLER_IDENTITY = "11111111-1111-1111-1111-111111111111";
    private static final String CALLER_TOKEN = "fake-token";

    private CommunityRequest communityRequest() {
        CommunityRequest request = new CommunityRequest();
        request.setName("Green Valley Apartments");
        request.setTotalUnits(100);
        request.setStreet("Street 1");
        request.setArea("Area 1");
        request.setDistrict("District 1");
        request.setState("State 1");
        request.setPincode("500001");
        return request;
    }

    private Community communityWithId(Long id) {
        Community community = new Community();
        community.setId(id);
        community.setName("Green Valley Apartments");
        return community;
    }

    private AuthUserProfile callerProfile() {
        AuthUserProfile profile = new AuthUserProfile();
        profile.setMobileNumber("9876543210");
        profile.setFirstName("Jane");
        profile.setLastName("Doe");
        return profile;
    }

    @Test
    void createCommunity_savesCommunityAndActiveAdminMember() {
        when(authServiceClient.getCurrentUserProfile(CALLER_TOKEN)).thenReturn(callerProfile());
        when(communityRepository.save(any(Community.class))).thenAnswer(invocation -> {
            Community c = invocation.getArgument(0);
            c.setId(1L);
            return c;
        });

        CommunityResponse response = communityService.createCommunity(communityRequest(), CALLER_IDENTITY, CALLER_TOKEN);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Green Valley Apartments");

        ArgumentCaptor<CommunityMember> memberCaptor = ArgumentCaptor.forClass(CommunityMember.class);
        verify(communityMemberRepository).save(memberCaptor.capture());
        CommunityMember savedAdmin = memberCaptor.getValue();
        assertThat(savedAdmin.getRole()).isEqualTo(CommunityRole.ADMIN);
        assertThat(savedAdmin.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(savedAdmin.getIdentityId()).isEqualTo(CALLER_IDENTITY);
        assertThat(savedAdmin.getMobileNumber()).isEqualTo("9876543210");
        assertThat(savedAdmin.getName()).isEqualTo("Jane Doe");
    }

    @Test
    void createCommunity_throwsDuplicateCommunity_whenMatchingNameAndPincodeExists() {
        Community existing = communityWithId(5L);
        when(communityRepository.findByNameIgnoreCaseAndPincode("Green Valley Apartments", "500001"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> communityService.createCommunity(communityRequest(), CALLER_IDENTITY, CALLER_TOKEN))
                .isInstanceOf(DuplicateCommunityException.class)
                .satisfies(ex -> {
                    DuplicateCommunityException dup = (DuplicateCommunityException) ex;
                    assertThat(dup.getExistingCommunityId()).isEqualTo(5L);
                });
        verify(communityRepository, never()).save(any());
        verify(authServiceClient, never()).getCurrentUserProfile(any());
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
    void listMyCommunities_returnsMappedList() {
        CommunityMember member = new CommunityMember();
        member.setCommunity(communityWithId(1L));
        member.setRole(CommunityRole.RESIDENT);
        member.setStatus(MemberStatus.ACTIVE);
        when(communityMemberRepository.findByIdentityId(CALLER_IDENTITY)).thenReturn(List.of(member));

        List<MyCommunityResponse> result = communityService.listMyCommunities(CALLER_IDENTITY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCommunityId()).isEqualTo(1L);
        assertThat(result.get(0).getRole()).isEqualTo(CommunityRole.RESIDENT);
    }

    @Test
    void activateInvitations_linksInvitedMembersMatchingCallerMobile() {
        when(authServiceClient.getCurrentUserProfile(CALLER_TOKEN)).thenReturn(callerProfile());
        CommunityMember invited = new CommunityMember();
        invited.setCommunity(communityWithId(1L));
        invited.setMobileNumber("9876543210");
        invited.setStatus(MemberStatus.INVITED);
        when(communityMemberRepository.findByMobileNumberAndStatusAndIdentityIdIsNull("9876543210", MemberStatus.INVITED))
                .thenReturn(List.of(invited));
        when(communityMemberRepository.findByIdentityId(CALLER_IDENTITY)).thenReturn(List.of(invited));

        communityService.activateInvitations(CALLER_IDENTITY, CALLER_TOKEN);

        assertThat(invited.getIdentityId()).isEqualTo(CALLER_IDENTITY);
        assertThat(invited.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        verify(communityMemberRepository).saveAll(List.of(invited));
    }

    @Test
    void addMember_throwsAccessDenied_whenCallerIsNotAdmin() {
        CommunityMember resident = new CommunityMember();
        resident.setRole(CommunityRole.RESIDENT);
        resident.setCommunity(communityWithId(1L));
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));

        CommunityMemberRequest request = new CommunityMemberRequest();
        request.setName("New Resident");
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
        request.setName("New Resident");
        request.setUnitNumber("A-101");
        request.setMobileNumber("9876500000");

        assertThatThrownBy(() -> communityService.addMember(1L, request, CALLER_IDENTITY))
                .isInstanceOf(ResourceAlreadyExistsException.class);
    }

    @Test
    void addMember_setsRoleResident() {
        CommunityMember admin = new CommunityMember();
        admin.setRole(CommunityRole.ADMIN);
        admin.setCommunity(communityWithId(1L));
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        when(communityRepository.getReferenceById(1L)).thenReturn(communityWithId(1L));
        when(communityMemberRepository.save(any(CommunityMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommunityMemberRequest request = new CommunityMemberRequest();
        request.setName("New Resident");
        request.setUnitNumber("A-101");
        request.setMobileNumber("9876500000");

        CommunityMemberResponse response = communityService.addMember(1L, request, CALLER_IDENTITY);

        assertThat(response.getRole()).isEqualTo(CommunityRole.RESIDENT);
        assertThat(response.getStatus()).isEqualTo(MemberStatus.INVITED);
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

    @Test
    void updateMyProfile_updatesNameEmailAndUnitNumber() {
        CommunityMember member = new CommunityMember();
        member.setRole(CommunityRole.RESIDENT);
        member.setCommunity(communityWithId(1L));
        member.setName("Old Name");
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
        when(communityMemberRepository.save(any(CommunityMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest();
        request.setName("New Name");
        request.setEmail("new@example.com");
        request.setUnitNumber("B-202");

        CommunityMemberResponse response = communityService.updateMyProfile(1L, request, CALLER_IDENTITY);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getEmail()).isEqualTo("new@example.com");
        assertThat(response.getUnitNumber()).isEqualTo("B-202");
    }

    @Test
    void updateMyProfile_throwsBadRequest_whenNameBlank() {
        CommunityMember member = new CommunityMember();
        member.setRole(CommunityRole.RESIDENT);
        member.setCommunity(communityWithId(1L));
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));

        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest();
        request.setName("   ");

        assertThatThrownBy(() -> communityService.updateMyProfile(1L, request, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(communityMemberRepository, never()).save(any());
    }

    @Test
    void updateBillingSettings_switchesToPerAreaWithRate_whenCallerIsAdmin() {
        CommunityMember admin = new CommunityMember();
        admin.setRole(CommunityRole.ADMIN);
        admin.setCommunity(communityWithId(1L));
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        when(communityRepository.getReferenceById(1L)).thenReturn(communityWithId(1L));
        when(communityRepository.save(any(Community.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BillingSettingsRequest request = new BillingSettingsRequest();
        request.setBillingMode(BillingMode.PER_AREA);
        request.setRatePerSqft(new java.math.BigDecimal("2.50"));

        CommunityResponse response = communityService.updateBillingSettings(1L, request, CALLER_IDENTITY);

        assertThat(response.getBillingMode()).isEqualTo(BillingMode.PER_AREA);
        assertThat(response.getRatePerSqft()).isEqualByComparingTo("2.50");
    }

    @Test
    void updateBillingSettings_throwsBadRequest_whenPerAreaWithNoRate() {
        CommunityMember admin = new CommunityMember();
        admin.setRole(CommunityRole.ADMIN);
        admin.setCommunity(communityWithId(1L));
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));

        BillingSettingsRequest request = new BillingSettingsRequest();
        request.setBillingMode(BillingMode.PER_AREA);

        assertThatThrownBy(() -> communityService.updateBillingSettings(1L, request, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(communityRepository, never()).save(any());
    }

    @Test
    void updateBillingSettings_switchingBackToFlat_clearsRate() {
        CommunityMember admin = new CommunityMember();
        admin.setRole(CommunityRole.ADMIN);
        admin.setCommunity(communityWithId(1L));
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        Community existing = communityWithId(1L);
        existing.setBillingMode(BillingMode.PER_AREA);
        existing.setRatePerSqft(new java.math.BigDecimal("2.50"));
        when(communityRepository.getReferenceById(1L)).thenReturn(existing);
        when(communityRepository.save(any(Community.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BillingSettingsRequest request = new BillingSettingsRequest();
        request.setBillingMode(BillingMode.FLAT);

        CommunityResponse response = communityService.updateBillingSettings(1L, request, CALLER_IDENTITY);

        assertThat(response.getBillingMode()).isEqualTo(BillingMode.FLAT);
        assertThat(response.getRatePerSqft()).isNull();
    }

    @Test
    void updateBillingSettings_throwsAccessDenied_whenCallerNotAdmin() {
        CommunityMember resident = new CommunityMember();
        resident.setRole(CommunityRole.RESIDENT);
        resident.setCommunity(communityWithId(1L));
        when(communityRepository.existsById(1L)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(1L, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));

        BillingSettingsRequest request = new BillingSettingsRequest();
        request.setBillingMode(BillingMode.FLAT);

        assertThatThrownBy(() -> communityService.updateBillingSettings(1L, request, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }
}
