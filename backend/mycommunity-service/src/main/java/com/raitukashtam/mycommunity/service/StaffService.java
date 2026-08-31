package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.Staff;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.StaffRepository;
import com.raitukashtam.mycommunity.request.StaffRequest;
import com.raitukashtam.mycommunity.response.StaffResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * ADMIN-only end to end -- staff/vendor management is a back-office
 * concern, no app screen drives it yet (matches Expense's precedent).
 * Membership authorization is delegated to CommunityService.
 */
@Service
@Slf4j
public class StaffService {
    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityService communityService;

    @Transactional
    public StaffResponse createStaff(Long communityId, StaffRequest request, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);

        Community community = communityRepository.getReferenceById(communityId);
        Staff staff = new Staff();
        staff.setCommunity(community);
        staff.setName(request.getName().trim());
        staff.setRole(request.getRole());
        staff.setPhoneNumber(request.getPhoneNumber());
        staff.setActive(true);
        Staff saved = staffRepository.save(staff);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> listStaff(Long communityId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        return staffRepository.findByCommunity_IdOrderByNameAsc(communityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StaffResponse getStaff(Long communityId, Long staffId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        return toResponse(requireStaff(communityId, staffId));
    }

    @Transactional
    public StaffResponse deactivateStaff(Long communityId, Long staffId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        Staff staff = requireStaff(communityId, staffId);
        if (!staff.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Staff member is already inactive");
        }
        staff.setActive(false);
        return toResponse(staffRepository.save(staff));
    }

    /** Package-private -- reused by StaffAttendanceService so marking attendance doesn't duplicate the "staff exists in this community" lookup. */
    Staff requireStaff(Long communityId, Long staffId) {
        return staffRepository.findByIdAndCommunity_Id(staffId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with id: " + staffId));
    }

    private StaffResponse toResponse(Staff staff) {
        return new StaffResponse(
                staff.getId(),
                staff.getCommunity().getId(),
                staff.getName(),
                staff.getRole(),
                staff.getPhoneNumber(),
                staff.isActive());
    }
}
