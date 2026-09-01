package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.CommitteeMember;
import com.raitukashtam.mycommunity.entity.CommitteePosition;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommitteeMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.CommitteeMemberRequest;
import com.raitukashtam.mycommunity.response.CommitteeMemberResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommitteeServiceTest {

    @Mock
    private CommitteeMemberRepository committeeMemberRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;

    private static final Long COMMUNITY_ID = 1L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private CommitteeService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        CommitteeService service = new CommitteeService();
        setField(service, "committeeMemberRepository", committeeMemberRepository);
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

    private void mockCaller(CommunityMember caller) {
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(caller));
    }

    @Test
    void createCommitteeMember_succeeds_whenCallerIsAdmin() {
        CommitteeService service = buildService();
        mockCaller(member(5L, CommunityRole.ADMIN));
        CommunityMember target = member(6L, CommunityRole.RESIDENT);
        when(communityMemberRepository.findByIdAndCommunity_Id(6L, COMMUNITY_ID)).thenReturn(Optional.of(target));
        when(committeeMemberRepository.existsByMember_IdAndTermEndIsNull(6L)).thenReturn(false);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(committeeMemberRepository.save(any(CommitteeMember.class))).thenAnswer(invocation -> {
            CommitteeMember cm = invocation.getArgument(0);
            cm.setId(100L);
            return cm;
        });

        CommitteeMemberRequest request = new CommitteeMemberRequest();
        request.setMemberId(6L);
        request.setPosition(CommitteePosition.SECRETARY);

        CommitteeMemberResponse response = service.createCommitteeMember(COMMUNITY_ID, request, CALLER_IDENTITY);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getPosition()).isEqualTo(CommitteePosition.SECRETARY);
        assertThat(response.isCurrent()).isTrue();
        assertThat(response.getTermEnd()).isNull();
    }

    @Test
    void createCommitteeMember_throwsAccessDenied_whenCallerNotAdmin() {
        CommitteeService service = buildService();
        mockCaller(member(6L, CommunityRole.RESIDENT));

        CommitteeMemberRequest request = new CommitteeMemberRequest();
        request.setMemberId(6L);
        request.setPosition(CommitteePosition.SECRETARY);

        assertThatThrownBy(() -> service.createCommitteeMember(COMMUNITY_ID, request, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createCommitteeMember_throwsBadRequest_whenOtherPositionWithNoCustomLabel() {
        CommitteeService service = buildService();
        mockCaller(member(5L, CommunityRole.ADMIN));

        CommitteeMemberRequest request = new CommitteeMemberRequest();
        request.setMemberId(6L);
        request.setPosition(CommitteePosition.OTHER);

        assertThatThrownBy(() -> service.createCommitteeMember(COMMUNITY_ID, request, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(committeeMemberRepository, never()).save(any());
    }

    @Test
    void createCommitteeMember_throwsNotFound_whenMemberMissing() {
        CommitteeService service = buildService();
        mockCaller(member(5L, CommunityRole.ADMIN));
        when(communityMemberRepository.findByIdAndCommunity_Id(999L, COMMUNITY_ID)).thenReturn(Optional.empty());

        CommitteeMemberRequest request = new CommitteeMemberRequest();
        request.setMemberId(999L);
        request.setPosition(CommitteePosition.TREASURER);

        assertThatThrownBy(() -> service.createCommitteeMember(COMMUNITY_ID, request, CALLER_IDENTITY))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createCommitteeMember_throwsConflict_whenMemberAlreadyHoldsCurrentPosition() {
        CommitteeService service = buildService();
        mockCaller(member(5L, CommunityRole.ADMIN));
        CommunityMember target = member(6L, CommunityRole.RESIDENT);
        when(communityMemberRepository.findByIdAndCommunity_Id(6L, COMMUNITY_ID)).thenReturn(Optional.of(target));
        when(committeeMemberRepository.existsByMember_IdAndTermEndIsNull(6L)).thenReturn(true);

        CommitteeMemberRequest request = new CommitteeMemberRequest();
        request.setMemberId(6L);
        request.setPosition(CommitteePosition.TREASURER);

        assertThatThrownBy(() -> service.createCommitteeMember(COMMUNITY_ID, request, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(committeeMemberRepository, never()).save(any());
    }

    @Test
    void endTerm_setsTermEndToToday_thenThrowsConflict_ifEndedAgain() {
        CommitteeService service = buildService();
        mockCaller(member(5L, CommunityRole.ADMIN));
        CommitteeMember existing = new CommitteeMember();
        existing.setId(10L);
        existing.setCommunity(community(COMMUNITY_ID));
        existing.setMember(member(6L, CommunityRole.RESIDENT));
        existing.setPosition(CommitteePosition.SECRETARY);
        existing.setTermStart(LocalDate.now().minusMonths(6));
        when(committeeMemberRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(existing));
        when(committeeMemberRepository.save(any(CommitteeMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommitteeMemberResponse response = service.endTerm(COMMUNITY_ID, 10L, CALLER_IDENTITY);
        assertThat(response.isCurrent()).isFalse();
        assertThat(response.getTermEnd()).isEqualTo(LocalDate.now());

        assertThatThrownBy(() -> service.endTerm(COMMUNITY_ID, 10L, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void listCurrentCommittee_returnsOnlyCurrentPositions() {
        CommitteeService service = buildService();
        mockCaller(member(6L, CommunityRole.RESIDENT));
        CommitteeMember cm = new CommitteeMember();
        cm.setId(10L);
        cm.setCommunity(community(COMMUNITY_ID));
        cm.setMember(member(7L, CommunityRole.RESIDENT));
        cm.setPosition(CommitteePosition.PRESIDENT);
        cm.setTermStart(LocalDate.now().minusYears(1));
        when(committeeMemberRepository.findByCommunity_IdAndTermEndIsNullOrderByPositionAsc(COMMUNITY_ID))
                .thenReturn(List.of(cm));

        List<CommitteeMemberResponse> result = service.listCurrentCommittee(COMMUNITY_ID, CALLER_IDENTITY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPosition()).isEqualTo(CommitteePosition.PRESIDENT);
        assertThat(result.get(0).isCurrent()).isTrue();
    }

    @Test
    void getCommitteeMember_throwsNotFound_whenMissing() {
        CommitteeService service = buildService();
        mockCaller(member(6L, CommunityRole.RESIDENT));
        when(committeeMemberRepository.findByIdAndCommunity_Id(999L, COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCommitteeMember(COMMUNITY_ID, 999L, CALLER_IDENTITY))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
