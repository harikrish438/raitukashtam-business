package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.Complaint;
import com.raitukashtam.mycommunity.entity.ComplaintComment;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.ComplaintCommentRepository;
import com.raitukashtam.mycommunity.repository.ComplaintRepository;
import com.raitukashtam.mycommunity.request.ComplaintCommentRequest;
import com.raitukashtam.mycommunity.response.ComplaintCommentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplaintCommentServiceTest {

    @Mock
    private ComplaintCommentRepository commentRepository;
    @Mock
    private ComplaintRepository complaintRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;

    private static final Long COMMUNITY_ID = 1L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private ComplaintCommentService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        ComplaintService complaintService = new ComplaintService();
        setField(complaintService, "complaintRepository", complaintRepository);
        setField(complaintService, "communityRepository", communityRepository);
        setField(complaintService, "communityMemberRepository", communityMemberRepository);
        setField(complaintService, "communityService", communityService);

        ComplaintCommentService service = new ComplaintCommentService();
        setField(service, "commentRepository", commentRepository);
        setField(service, "complaintService", complaintService);
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

    private Complaint complaint(CommunityMember raiser) {
        Complaint complaint = new Complaint();
        complaint.setId(10L);
        complaint.setCommunity(community(COMMUNITY_ID));
        complaint.setRaisedBy(raiser);
        return complaint;
    }

    @Test
    void addComment_savesComment_whenCallerIsRaiser() {
        ComplaintCommentService service = buildService();
        CommunityMember raiser = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(raiser);
        Complaint complaint = complaint(raiser);
        when(complaintRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(complaint));
        when(commentRepository.save(any(ComplaintComment.class))).thenAnswer(invocation -> {
            ComplaintComment c = invocation.getArgument(0);
            c.setId(50L);
            return c;
        });

        ComplaintCommentRequest request = new ComplaintCommentRequest();
        request.setComment("Any update on this?");

        ComplaintCommentResponse response = service.addComment(COMMUNITY_ID, 10L, request, CALLER_IDENTITY);

        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getAuthorMemberId()).isEqualTo(6L);
        assertThat(response.getComment()).isEqualTo("Any update on this?");
    }

    @Test
    void addComment_throwsAccessDenied_whenCallerIsUnrelatedResident() {
        ComplaintCommentService service = buildService();
        CommunityMember unrelated = member(8L, CommunityRole.RESIDENT);
        stubActiveMember(unrelated);
        Complaint complaint = complaint(member(6L, CommunityRole.RESIDENT));
        when(complaintRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(complaint));

        ComplaintCommentRequest request = new ComplaintCommentRequest();
        request.setComment("Any update on this?");

        assertThatThrownBy(() -> service.addComment(COMMUNITY_ID, 10L, request, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void addComment_allowedForAdmin_evenWhenNotRaiser() {
        ComplaintCommentService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        Complaint complaint = complaint(member(6L, CommunityRole.RESIDENT));
        when(complaintRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(complaint));
        when(commentRepository.save(any(ComplaintComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ComplaintCommentRequest request = new ComplaintCommentRequest();
        request.setComment("Looking into it.");

        ComplaintCommentResponse response = service.addComment(COMMUNITY_ID, 10L, request, CALLER_IDENTITY);

        assertThat(response.getAuthorMemberId()).isEqualTo(5L);
    }

    @Test
    void listComments_returnsChronologicalList() {
        ComplaintCommentService service = buildService();
        CommunityMember raiser = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(raiser);
        Complaint complaint = complaint(raiser);
        when(complaintRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(complaint));

        ComplaintComment comment = new ComplaintComment();
        comment.setId(50L);
        comment.setComplaint(complaint);
        comment.setAuthor(raiser);
        comment.setComment("First comment");
        when(commentRepository.findByComplaint_IdOrderByCreatedAtAsc(10L)).thenReturn(List.of(comment));

        List<ComplaintCommentResponse> result = service.listComments(COMMUNITY_ID, 10L, CALLER_IDENTITY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getComment()).isEqualTo("First comment");
    }
}
