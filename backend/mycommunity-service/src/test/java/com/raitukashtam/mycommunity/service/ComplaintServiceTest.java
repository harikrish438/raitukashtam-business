package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.Complaint;
import com.raitukashtam.mycommunity.entity.ComplaintPriority;
import com.raitukashtam.mycommunity.entity.ComplaintStatus;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.ComplaintRepository;
import com.raitukashtam.mycommunity.request.AssignComplaintRequest;
import com.raitukashtam.mycommunity.request.ComplaintRequest;
import com.raitukashtam.mycommunity.request.ComplaintStatusRequest;
import com.raitukashtam.mycommunity.response.ComplaintResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;

    private static final Long COMMUNITY_ID = 1L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private ComplaintService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        ComplaintService service = new ComplaintService();
        setField(service, "complaintRepository", complaintRepository);
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

    private ComplaintRequest complaintRequest() {
        ComplaintRequest request = new ComplaintRequest();
        request.setCategory("Plumbing");
        request.setTitle("Leaking tap");
        request.setDescription("Kitchen tap has been leaking for two days");
        return request;
    }

    @Test
    void createComplaint_savesOpenComplaint_withDefaultMediumPriority() {
        ComplaintService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> {
            Complaint c = invocation.getArgument(0);
            c.setId(10L);
            return c;
        });

        ComplaintResponse response = service.createComplaint(COMMUNITY_ID, complaintRequest(), CALLER_IDENTITY);

        assertThat(response.getStatus()).isEqualTo(ComplaintStatus.OPEN);
        assertThat(response.getPriority()).isEqualTo(ComplaintPriority.MEDIUM);
        assertThat(response.getRaisedByMemberId()).isEqualTo(6L);
        assertThat(response.getAssignedToMemberId()).isNull();
    }

    @Test
    void createComplaint_honorsExplicitPriority() {
        ComplaintService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ComplaintRequest request = complaintRequest();
        request.setPriority(ComplaintPriority.URGENT);

        ComplaintResponse response = service.createComplaint(COMMUNITY_ID, request, CALLER_IDENTITY);

        assertThat(response.getPriority()).isEqualTo(ComplaintPriority.URGENT);
    }

    @Test
    void assignComplaint_setsAssignee_whenCallerIsAdmin() {
        ComplaintService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        Complaint complaint = new Complaint();
        complaint.setId(10L);
        complaint.setCommunity(community(COMMUNITY_ID));
        complaint.setRaisedBy(member(6L, CommunityRole.RESIDENT));
        complaint.setStatus(ComplaintStatus.OPEN);
        when(complaintRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(complaint));
        CommunityMember assignee = member(7L, CommunityRole.RESIDENT);
        when(communityMemberRepository.findByIdAndCommunity_Id(7L, COMMUNITY_ID)).thenReturn(Optional.of(assignee));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssignComplaintRequest request = new AssignComplaintRequest();
        request.setAssigneeMemberId(7L);

        ComplaintResponse response = service.assignComplaint(COMMUNITY_ID, 10L, request, CALLER_IDENTITY);

        assertThat(response.getAssignedToMemberId()).isEqualTo(7L);
    }

    @Test
    void assignComplaint_throwsNotFound_whenAssigneeNotAMember() {
        ComplaintService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        Complaint complaint = new Complaint();
        complaint.setId(10L);
        when(complaintRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(complaint));
        when(communityMemberRepository.findByIdAndCommunity_Id(999L, COMMUNITY_ID)).thenReturn(Optional.empty());

        AssignComplaintRequest request = new AssignComplaintRequest();
        request.setAssigneeMemberId(999L);

        assertThatThrownBy(() -> service.assignComplaint(COMMUNITY_ID, 10L, request, CALLER_IDENTITY))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_advancesOneStep_whenCallerIsAdmin() {
        ComplaintService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        Complaint complaint = new Complaint();
        complaint.setId(10L);
        complaint.setCommunity(community(COMMUNITY_ID));
        complaint.setRaisedBy(member(6L, CommunityRole.RESIDENT));
        complaint.setStatus(ComplaintStatus.OPEN);
        when(complaintRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ComplaintStatusRequest request = new ComplaintStatusRequest();
        request.setStatus(ComplaintStatus.IN_PROGRESS);

        ComplaintResponse response = service.updateStatus(COMMUNITY_ID, 10L, request, CALLER_IDENTITY);

        assertThat(response.getStatus()).isEqualTo(ComplaintStatus.IN_PROGRESS);
    }

    @Test
    void updateStatus_throwsConflict_whenSkippingAStep() {
        ComplaintService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        Complaint complaint = new Complaint();
        complaint.setId(10L);
        complaint.setStatus(ComplaintStatus.OPEN);
        when(complaintRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(complaint));

        ComplaintStatusRequest request = new ComplaintStatusRequest();
        request.setStatus(ComplaintStatus.RESOLVED);

        assertThatThrownBy(() -> service.updateStatus(COMMUNITY_ID, 10L, request, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(complaintRepository, never()).save(any());
    }

    @Test
    void updateStatus_throwsConflict_whenMovingBackward() {
        ComplaintService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        Complaint complaint = new Complaint();
        complaint.setId(10L);
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);
        when(complaintRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(complaint));

        ComplaintStatusRequest request = new ComplaintStatusRequest();
        request.setStatus(ComplaintStatus.OPEN);

        assertThatThrownBy(() -> service.updateStatus(COMMUNITY_ID, 10L, request, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(complaintRepository, never()).save(any());
    }

    @Test
    void getComplaint_visibleToAssignee_evenWhenNotRaiser() {
        ComplaintService service = buildService();
        CommunityMember assignee = member(7L, CommunityRole.RESIDENT);
        stubActiveMember(assignee);
        Complaint complaint = new Complaint();
        complaint.setId(10L);
        complaint.setCommunity(community(COMMUNITY_ID));
        complaint.setRaisedBy(member(6L, CommunityRole.RESIDENT));
        complaint.setAssignedTo(assignee);
        when(complaintRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(complaint));

        ComplaintResponse response = service.getComplaint(COMMUNITY_ID, 10L, CALLER_IDENTITY);

        assertThat(response.getId()).isEqualTo(10L);
    }

    @Test
    void getComplaint_throwsAccessDenied_whenCallerIsUnrelatedResident() {
        ComplaintService service = buildService();
        CommunityMember unrelated = member(8L, CommunityRole.RESIDENT);
        stubActiveMember(unrelated);
        Complaint complaint = new Complaint();
        complaint.setId(10L);
        complaint.setCommunity(community(COMMUNITY_ID));
        complaint.setRaisedBy(member(6L, CommunityRole.RESIDENT));
        complaint.setAssignedTo(member(7L, CommunityRole.RESIDENT));
        when(complaintRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> service.getComplaint(COMMUNITY_ID, 10L, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }
}
