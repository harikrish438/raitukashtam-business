package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.Staff;
import com.raitukashtam.mycommunity.entity.StaffAttendance;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.StaffAttendanceRepository;
import com.raitukashtam.mycommunity.request.MarkAttendanceRequest;
import com.raitukashtam.mycommunity.response.StaffAttendanceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Marking attendance is an upsert -- a second call for the same
 * staff+date corrects the earlier entry rather than being rejected as a
 * duplicate, since attendance mis-entry is a real, common correction
 * need (unlike e.g. Bill generation, which does reject duplicates).
 * Staff lookup is delegated to StaffService.requireStaff (package-
 * private, same package) rather than duplicated here.
 */
@Service
@Slf4j
public class StaffAttendanceService {
    @Autowired
    private StaffAttendanceRepository attendanceRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private StaffService staffService;

    @Autowired
    private CommunityService communityService;

    @Transactional
    public StaffAttendanceResponse markAttendance(Long communityId, Long staffId, MarkAttendanceRequest request, String callerIdentityId) {
        CommunityMember admin = communityService.requireActiveAdmin(communityId, callerIdentityId);
        Staff staff = staffService.requireStaff(communityId, staffId);

        StaffAttendance attendance = attendanceRepository.findByStaff_IdAndAttendanceDate(staffId, request.getAttendanceDate())
                .orElseGet(() -> {
                    Community community = communityRepository.getReferenceById(communityId);
                    StaffAttendance created = new StaffAttendance();
                    created.setCommunity(community);
                    created.setStaff(staff);
                    created.setAttendanceDate(request.getAttendanceDate());
                    return created;
                });
        attendance.setStatus(request.getStatus());
        attendance.setMarkedBy(admin);
        StaffAttendance saved = attendanceRepository.save(attendance);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<StaffAttendanceResponse> listAttendance(Long communityId, Long staffId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        staffService.requireStaff(communityId, staffId);
        return attendanceRepository.findByStaff_IdOrderByAttendanceDateDesc(staffId).stream()
                .map(this::toResponse)
                .toList();
    }

    private StaffAttendanceResponse toResponse(StaffAttendance attendance) {
        return new StaffAttendanceResponse(
                attendance.getId(),
                attendance.getCommunity().getId(),
                attendance.getStaff().getId(),
                attendance.getStaff().getName(),
                attendance.getAttendanceDate(),
                attendance.getStatus(),
                attendance.getMarkedBy().getId(),
                attendance.getMarkedBy().getName());
    }
}
