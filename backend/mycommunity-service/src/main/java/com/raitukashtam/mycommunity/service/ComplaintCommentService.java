package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.Complaint;
import com.raitukashtam.mycommunity.entity.ComplaintComment;
import com.raitukashtam.mycommunity.repository.ComplaintCommentRepository;
import com.raitukashtam.mycommunity.request.ComplaintCommentRequest;
import com.raitukashtam.mycommunity.response.ComplaintCommentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Visibility for both adding and reading comments matches the parent
 * Complaint's own visibility rule (ADMIN, raiser, or assignee) -- reused
 * from ComplaintService rather than duplicated.
 */
@Service
@Slf4j
public class ComplaintCommentService {
    @Autowired
    private ComplaintCommentRepository commentRepository;

    @Autowired
    private ComplaintService complaintService;

    @Autowired
    private CommunityService communityService;

    @Transactional
    public ComplaintCommentResponse addComment(Long communityId, Long complaintId, ComplaintCommentRequest request, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        Complaint complaint = complaintService.requireComplaint(communityId, complaintId);
        complaintService.requireVisibleToCaller(complaint, caller);

        ComplaintComment comment = new ComplaintComment();
        comment.setComplaint(complaint);
        comment.setAuthor(caller);
        comment.setComment(request.getComment().trim());
        ComplaintComment saved = commentRepository.save(comment);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ComplaintCommentResponse> listComments(Long communityId, Long complaintId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        Complaint complaint = complaintService.requireComplaint(communityId, complaintId);
        complaintService.requireVisibleToCaller(complaint, caller);

        return commentRepository.findByComplaint_IdOrderByCreatedAtAsc(complaintId).stream()
                .map(this::toResponse)
                .toList();
    }

    private ComplaintCommentResponse toResponse(ComplaintComment comment) {
        return new ComplaintCommentResponse(
                comment.getId(),
                comment.getComplaint().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getName(),
                comment.getComment(),
                comment.getCreatedAt());
    }
}
