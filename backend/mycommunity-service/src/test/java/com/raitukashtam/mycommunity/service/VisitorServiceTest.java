package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.entity.Visitor;
import com.raitukashtam.mycommunity.entity.VisitorStatus;
import com.raitukashtam.mycommunity.entity.VisitorType;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.VisitorRepository;
import com.raitukashtam.mycommunity.request.CreateVisitorRequest;
import com.raitukashtam.mycommunity.response.VisitorResponse;
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
class VisitorServiceTest {

    @Mock
    private VisitorRepository visitorRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;

    private static final Long COMMUNITY_ID = 1L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private VisitorService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        VisitorService service = new VisitorService();
        setField(service, "visitorRepository", visitorRepository);
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

    private void stubActiveMember(CommunityMember member) {
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
    }

    private CreateVisitorRequest visitorRequest(boolean checkedInNow) {
        CreateVisitorRequest request = new CreateVisitorRequest();
        request.setGuestName("John Doe");
        request.setType(VisitorType.GUEST);
        request.setPurpose("Birthday party");
        request.setCheckedInNow(checkedInNow);
        return request;
    }

    @Test
    void createVisitor_createsExpected_whenNotCheckedInNow() {
        VisitorService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(visitorRepository.save(any(Visitor.class))).thenAnswer(invocation -> {
            Visitor v = invocation.getArgument(0);
            v.setId(10L);
            return v;
        });

        VisitorResponse response = service.createVisitor(COMMUNITY_ID, visitorRequest(false), CALLER_IDENTITY);

        assertThat(response.getStatus()).isEqualTo(VisitorStatus.EXPECTED);
        assertThat(response.getEntryTime()).isNull();
        assertThat(response.getHostMemberId()).isEqualTo(6L);
    }

    @Test
    void createVisitor_createsCheckedIn_whenCheckedInNow() {
        VisitorService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(visitorRepository.save(any(Visitor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VisitorResponse response = service.createVisitor(COMMUNITY_ID, visitorRequest(true), CALLER_IDENTITY);

        assertThat(response.getStatus()).isEqualTo(VisitorStatus.CHECKED_IN);
        assertThat(response.getEntryTime()).isNotNull();
    }

    @Test
    void checkIn_transitionsToCheckedIn_whenHostCalls() {
        VisitorService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        Visitor visitor = new Visitor();
        visitor.setId(10L);
        visitor.setCommunity(community(COMMUNITY_ID));
        visitor.setHost(resident);
        visitor.setStatus(VisitorStatus.EXPECTED);
        when(visitorRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(visitor));
        when(visitorRepository.save(any(Visitor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VisitorResponse response = service.checkIn(COMMUNITY_ID, 10L, CALLER_IDENTITY);

        assertThat(response.getStatus()).isEqualTo(VisitorStatus.CHECKED_IN);
        assertThat(response.getEntryTime()).isNotNull();
    }

    @Test
    void checkIn_throwsAccessDenied_whenCallerIsNeitherHostNorAdmin() {
        VisitorService service = buildService();
        CommunityMember otherResident = member(7L, CommunityRole.RESIDENT);
        stubActiveMember(otherResident);
        Visitor visitor = new Visitor();
        visitor.setId(10L);
        visitor.setHost(member(6L, CommunityRole.RESIDENT));
        visitor.setStatus(VisitorStatus.EXPECTED);
        when(visitorRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(visitor));

        assertThatThrownBy(() -> service.checkIn(COMMUNITY_ID, 10L, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
        verify(visitorRepository, never()).save(any());
    }

    @Test
    void checkIn_allowedByAdmin_evenWhenNotHost() {
        VisitorService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        Visitor visitor = new Visitor();
        visitor.setId(10L);
        visitor.setCommunity(community(COMMUNITY_ID));
        visitor.setHost(member(6L, CommunityRole.RESIDENT));
        visitor.setStatus(VisitorStatus.EXPECTED);
        when(visitorRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(visitor));
        when(visitorRepository.save(any(Visitor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VisitorResponse response = service.checkIn(COMMUNITY_ID, 10L, CALLER_IDENTITY);

        assertThat(response.getStatus()).isEqualTo(VisitorStatus.CHECKED_IN);
    }

    @Test
    void checkIn_throwsConflict_whenNotExpected() {
        VisitorService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        Visitor visitor = new Visitor();
        visitor.setId(10L);
        visitor.setHost(resident);
        visitor.setStatus(VisitorStatus.CHECKED_IN);
        when(visitorRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(visitor));

        assertThatThrownBy(() -> service.checkIn(COMMUNITY_ID, 10L, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(visitorRepository, never()).save(any());
    }

    @Test
    void checkOut_transitionsToCheckedOut_whenCheckedIn() {
        VisitorService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        Visitor visitor = new Visitor();
        visitor.setId(10L);
        visitor.setCommunity(community(COMMUNITY_ID));
        visitor.setHost(resident);
        visitor.setStatus(VisitorStatus.CHECKED_IN);
        when(visitorRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(visitor));
        when(visitorRepository.save(any(Visitor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VisitorResponse response = service.checkOut(COMMUNITY_ID, 10L, CALLER_IDENTITY);

        assertThat(response.getStatus()).isEqualTo(VisitorStatus.CHECKED_OUT);
        assertThat(response.getExitTime()).isNotNull();
    }

    @Test
    void checkOut_throwsConflict_whenNotCheckedIn() {
        VisitorService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        Visitor visitor = new Visitor();
        visitor.setId(10L);
        visitor.setHost(resident);
        visitor.setStatus(VisitorStatus.EXPECTED);
        when(visitorRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(visitor));

        assertThatThrownBy(() -> service.checkOut(COMMUNITY_ID, 10L, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(visitorRepository, never()).save(any());
    }

    @Test
    void listVisitors_throwsAccessDenied_whenCallerNotAdmin() {
        VisitorService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);

        assertThatThrownBy(() -> service.listVisitors(COMMUNITY_ID, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listMyVisitors_returnsOnlyCallersVisitors() {
        VisitorService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);

        Visitor visitor = new Visitor();
        visitor.setId(10L);
        visitor.setCommunity(community(COMMUNITY_ID));
        visitor.setHost(resident);
        visitor.setGuestName("John Doe");
        visitor.setType(VisitorType.GUEST);
        visitor.setStatus(VisitorStatus.EXPECTED);
        when(visitorRepository.findByHost_IdOrderByCreatedAtDesc(6L)).thenReturn(List.of(visitor));

        List<VisitorResponse> result = service.listMyVisitors(COMMUNITY_ID, CALLER_IDENTITY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHostMemberId()).isEqualTo(6L);
    }

    @Test
    void getVisitor_throwsNotFound_whenMissing() {
        VisitorService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        when(visitorRepository.findByIdAndCommunity_Id(99L, COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVisitor(COMMUNITY_ID, 99L, CALLER_IDENTITY))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
