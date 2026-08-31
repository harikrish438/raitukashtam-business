package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.entity.Vendor;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.VendorRepository;
import com.raitukashtam.mycommunity.request.VendorRequest;
import com.raitukashtam.mycommunity.response.VendorResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class VendorServiceTest {

    @Mock
    private VendorRepository vendorRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;

    private static final Long COMMUNITY_ID = 1L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private VendorService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        VendorService service = new VendorService();
        setField(service, "vendorRepository", vendorRepository);
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
    void createVendor_savesVendor_whenCallerIsAdmin() {
        VendorService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(vendorRepository.save(any(Vendor.class))).thenAnswer(invocation -> {
            Vendor v = invocation.getArgument(0);
            v.setId(20L);
            return v;
        });

        VendorRequest request = new VendorRequest();
        request.setName("Acme Electricals");
        request.setServiceType("Electrical");
        request.setContactPerson("Suresh");
        request.setPhoneNumber("9000000044");

        VendorResponse response = service.createVendor(COMMUNITY_ID, request, CALLER_IDENTITY);

        assertThat(response.getId()).isEqualTo(20L);
        assertThat(response.getServiceType()).isEqualTo("Electrical");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void createVendor_throwsAccessDenied_whenCallerNotAdmin() {
        VendorService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);

        VendorRequest request = new VendorRequest();
        request.setName("Acme Electricals");
        request.setServiceType("Electrical");

        assertThatThrownBy(() -> service.createVendor(COMMUNITY_ID, request, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listVendors_returnsAll_whenCallerIsAdmin() {
        VendorService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);

        Vendor vendor = new Vendor();
        vendor.setId(20L);
        vendor.setCommunity(community(COMMUNITY_ID));
        vendor.setName("Acme Electricals");
        vendor.setServiceType("Electrical");
        when(vendorRepository.findByCommunity_IdOrderByNameAsc(COMMUNITY_ID)).thenReturn(List.of(vendor));

        List<VendorResponse> result = service.listVendors(COMMUNITY_ID, CALLER_IDENTITY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Acme Electricals");
    }

    @Test
    void deactivateVendor_setsInactive_whenCallerIsAdmin() {
        VendorService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        Vendor vendor = new Vendor();
        vendor.setId(20L);
        vendor.setCommunity(community(COMMUNITY_ID));
        vendor.setActive(true);
        when(vendorRepository.findByIdAndCommunity_Id(20L, COMMUNITY_ID)).thenReturn(Optional.of(vendor));
        when(vendorRepository.save(any(Vendor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VendorResponse response = service.deactivateVendor(COMMUNITY_ID, 20L, CALLER_IDENTITY);

        assertThat(response.isActive()).isFalse();
    }

    @Test
    void deactivateVendor_throwsConflict_whenAlreadyInactive() {
        VendorService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        Vendor vendor = new Vendor();
        vendor.setId(20L);
        vendor.setActive(false);
        when(vendorRepository.findByIdAndCommunity_Id(20L, COMMUNITY_ID)).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> service.deactivateVendor(COMMUNITY_ID, 20L, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(vendorRepository, never()).save(any());
    }

    @Test
    void getVendor_throwsNotFound_whenMissing() {
        VendorService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        when(vendorRepository.findByIdAndCommunity_Id(99L, COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVendor(COMMUNITY_ID, 99L, CALLER_IDENTITY))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
