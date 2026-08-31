package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.ComplaintComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintCommentRepository extends JpaRepository<ComplaintComment, Long> {

    List<ComplaintComment> findByComplaint_IdOrderByCreatedAtAsc(Long complaintId);
}
