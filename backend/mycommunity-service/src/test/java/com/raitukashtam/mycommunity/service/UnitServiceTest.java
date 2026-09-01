package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.entity.Unit;
import com.raitukashtam.mycommunity.exception.ResourceAlreadyExistsException;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.UnitRepository;
import com.raitukashtam.mycommunity.request.AssignUnitRequest;
import com.raitukashtam.mycommunity.request.UnitRequest;
import com.raitukashtam.mycommunity.request.UnitUpdateRequest;
import com.raitukashtam.mycommunity.response.CommunityMemberResponse;
import com.raitukashtam.mycommunity.response.UnitResponse;
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
class UnitServiceTest {

    @Mock
    private UnitRepository unitRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;

    private static final Long COMMUNITY_ID = 1L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private UnitService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        UnitService service = new UnitService();
        setField(service, "unitRepository", unitRepository);
        setField(service, "communityRepository", communityRepository);
        setField(service, "communityMemberRepository", communityMemberRepository);
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
        member.setUnitNumber("A-" + id);
        member.setRole(role);
        member.setStatus(MemberStatus.ACTIVE);
        member.setCommunity(community(COMMUNITY_ID));
        return member;
    }

    private Unit unit(Long id, String unitNumber) {
        Unit unit = new Unit();
        unit.setId(id);
        unit.setCommunity(community(COMMUNITY_ID));
        unit.setUnitNumber(unitNumber);
        unit.setAreaSqft(new BigDecimal("1200.00"));
        unit.setActive(true);
        return unit;
    }

    private void mockCaller(CommunityMember caller) {
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(caller));
    }

    @Test
    void createUnit_succeeds_whenCallerIsAdmin() {
        UnitService service = buildService();
        mockCaller(member(5L, CommunityRole.ADMIN));
        when(unitRepository.existsByCommunity_IdAndUnitNumberIgnoreCase(COMMUNITY_ID, "A-101")).thenReturn(false);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(unitRepository.save(any(Unit.class))).thenAnswer(invocation -> {
            Unit unit = invocation.getArgument(0);
            unit.setId(100L);
            return unit;
        });

        UnitRequest request = new UnitRequest();
        request.setUnitNumber("A-101");
        request.setAreaSqft(new BigDecimal("1200.00"));

        UnitResponse response = service.createUnit(COMMUNITY_ID, request, CALLER_IDENTITY);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getUnitNumber()).isEqualTo("A-101");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void createUnit_throwsAlreadyExists_whenUnitNumberDuplicate() {
        UnitService service = buildService();
        mockCaller(member(5L, CommunityRole.ADMIN));
        when(unitRepository.existsByCommunity_IdAndUnitNumberIgnoreCase(COMMUNITY_ID, "A-101")).thenReturn(true);

        UnitRequest request = new UnitRequest();
        request.setUnitNumber("A-101");

        assertThatThrownBy(() -> service.createUnit(COMMUNITY_ID, request, CALLER_IDENTITY))
                .isInstanceOf(ResourceAlreadyExistsException.class);
        verify(unitRepository, never()).save(any());
    }

    @Test
    void createUnit_throwsAccessDenied_whenCallerNotAdmin() {
        UnitService service = buildService();
        mockCaller(member(6L, CommunityRole.RESIDENT));

        UnitRequest request = new UnitRequest();
        request.setUnitNumber("A-101");

        assertThatThrownBy(() -> service.createUnit(COMMUNITY_ID, request, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateUnit_updatesArea_whenCallerIsAdmin() {
        UnitService service = buildService();
        mockCaller(member(5L, CommunityRole.ADMIN));
        Unit existing = unit(10L, "A-101");
        when(unitRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(existing));
        when(unitRepository.save(any(Unit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UnitUpdateRequest request = new UnitUpdateRequest();
        request.setAreaSqft(new BigDecimal("1500.50"));

        UnitResponse response = service.updateUnit(COMMUNITY_ID, 10L, request, CALLER_IDENTITY);

        assertThat(response.getAreaSqft()).isEqualByComparingTo("1500.50");
        assertThat(response.getUnitNumber()).isEqualTo("A-101");
    }

    @Test
    void updateUnit_throwsNotFound_whenMissing() {
        UnitService service = buildService();
        mockCaller(member(5L, CommunityRole.ADMIN));
        when(unitRepository.findByIdAndCommunity_Id(999L, COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUnit(COMMUNITY_ID, 999L, new UnitUpdateRequest(), CALLER_IDENTITY))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivateUnit_succeeds_thenThrowsConflict_ifDeactivatedAgain() {
        UnitService service = buildService();
        mockCaller(member(5L, CommunityRole.ADMIN));
        Unit existing = unit(10L, "A-101");
        when(unitRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(existing));
        when(unitRepository.save(any(Unit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UnitResponse response = service.deactivateUnit(COMMUNITY_ID, 10L, CALLER_IDENTITY);
        assertThat(response.isActive()).isFalse();

        assertThatThrownBy(() -> service.deactivateUnit(COMMUNITY_ID, 10L, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void assignUnitToMember_linksUnit_andSyncsUnitNumber() {
        UnitService service = buildService();
        mockCaller(member(5L, CommunityRole.ADMIN));
        CommunityMember target = member(6L, CommunityRole.RESIDENT);
        Unit unit = unit(10L, "B-202");
        when(communityMemberRepository.findByIdAndCommunity_Id(6L, COMMUNITY_ID)).thenReturn(Optional.of(target));
        when(unitRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(unit));
        when(communityMemberRepository.save(any(CommunityMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssignUnitRequest request = new AssignUnitRequest();
        request.setUnitId(10L);

        CommunityMemberResponse response = service.assignUnitToMember(COMMUNITY_ID, 6L, request, CALLER_IDENTITY);

        assertThat(response.getUnitId()).isEqualTo(10L);
        assertThat(response.getUnitNumber()).isEqualTo("B-202");
    }

    @Test
    void assignUnitToMember_throwsConflict_whenUnitInactive() {
        UnitService service = buildService();
        mockCaller(member(5L, CommunityRole.ADMIN));
        CommunityMember target = member(6L, CommunityRole.RESIDENT);
        Unit unit = unit(10L, "B-202");
        unit.setActive(false);
        when(communityMemberRepository.findByIdAndCommunity_Id(6L, COMMUNITY_ID)).thenReturn(Optional.of(target));
        when(unitRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(unit));

        AssignUnitRequest request = new AssignUnitRequest();
        request.setUnitId(10L);

        assertThatThrownBy(() -> service.assignUnitToMember(COMMUNITY_ID, 6L, request, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(communityMemberRepository, never()).save(any());
    }

    @Test
    void listUnits_returnsAllUnits_forActiveMember() {
        UnitService service = buildService();
        mockCaller(member(6L, CommunityRole.RESIDENT));
        when(unitRepository.findByCommunity_IdOrderByUnitNumberAsc(COMMUNITY_ID))
                .thenReturn(List.of(unit(10L, "A-101"), unit(11L, "A-102")));

        List<UnitResponse> result = service.listUnits(COMMUNITY_ID, CALLER_IDENTITY);

        assertThat(result).hasSize(2);
    }
}
