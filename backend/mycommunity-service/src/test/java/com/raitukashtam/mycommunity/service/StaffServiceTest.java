package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.entity.Staff;
import com.raitukashtam.mycommunity.entity.StaffRole;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.StaffRepository;
import com.raitukashtam.mycommunity.request.StaffRequest;
import com.raitukashtam.mycommunity.response.StaffResponse;
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
class StaffServiceTest {

    @Mock
    private StaffRepository staffRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;

    private static final Long COMMUNITY_ID = 1L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private StaffService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        StaffService service = new StaffService();
        setField(service, "staffRepository", staffRepository);
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
    void createStaff_savesStaff_whenCallerIsAdmin() {
        StaffService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> {
            Staff s = invocation.getArgument(0);
            s.setId(10L);
            return s;
        });

        StaffRequest request = new StaffRequest();
        request.setName("Ramesh Kumar");
        request.setRole(StaffRole.SECURITY);
        request.setPhoneNumber("9000000033");

        StaffResponse response = service.createStaff(COMMUNITY_ID, request, CALLER_IDENTITY);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getRole()).isEqualTo(StaffRole.SECURITY);
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void createStaff_throwsAccessDenied_whenCallerNotAdmin() {
        StaffService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);

        StaffRequest request = new StaffRequest();
        request.setName("Ramesh Kumar");
        request.setRole(StaffRole.SECURITY);

        assertThatThrownBy(() -> service.createStaff(COMMUNITY_ID, request, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listStaff_returnsAll_whenCallerIsAdmin() {
        StaffService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);

        Staff staff = new Staff();
        staff.setId(10L);
        staff.setCommunity(community(COMMUNITY_ID));
        staff.setName("Ramesh Kumar");
        staff.setRole(StaffRole.SECURITY);
        when(staffRepository.findByCommunity_IdOrderByNameAsc(COMMUNITY_ID)).thenReturn(List.of(staff));

        List<StaffResponse> result = service.listStaff(COMMUNITY_ID, CALLER_IDENTITY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Ramesh Kumar");
    }

    @Test
    void deactivateStaff_setsInactive_whenCallerIsAdmin() {
        StaffService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        Staff staff = new Staff();
        staff.setId(10L);
        staff.setCommunity(community(COMMUNITY_ID));
        staff.setActive(true);
        when(staffRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(staff));
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StaffResponse response = service.deactivateStaff(COMMUNITY_ID, 10L, CALLER_IDENTITY);

        assertThat(response.isActive()).isFalse();
    }

    @Test
    void deactivateStaff_throwsConflict_whenAlreadyInactive() {
        StaffService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        Staff staff = new Staff();
        staff.setId(10L);
        staff.setActive(false);
        when(staffRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.deactivateStaff(COMMUNITY_ID, 10L, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(staffRepository, never()).save(any());
    }

    @Test
    void getStaff_throwsNotFound_whenMissing() {
        StaffService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        when(staffRepository.findByIdAndCommunity_Id(99L, COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStaff(COMMUNITY_ID, 99L, CALLER_IDENTITY))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
