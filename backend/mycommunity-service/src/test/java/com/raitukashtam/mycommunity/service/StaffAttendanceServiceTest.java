package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.AttendanceStatus;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.entity.Staff;
import com.raitukashtam.mycommunity.entity.StaffAttendance;
import com.raitukashtam.mycommunity.entity.StaffRole;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.StaffAttendanceRepository;
import com.raitukashtam.mycommunity.repository.StaffRepository;
import com.raitukashtam.mycommunity.request.MarkAttendanceRequest;
import com.raitukashtam.mycommunity.response.StaffAttendanceResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffAttendanceServiceTest {

    @Mock
    private StaffAttendanceRepository attendanceRepository;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;

    private static final Long COMMUNITY_ID = 1L;
    private static final Long STAFF_ID = 30L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private StaffAttendanceService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        StaffService staffService = new StaffService();
        setField(staffService, "staffRepository", staffRepository);
        setField(staffService, "communityRepository", communityRepository);
        setField(staffService, "communityService", communityService);

        StaffAttendanceService service = new StaffAttendanceService();
        setField(service, "attendanceRepository", attendanceRepository);
        setField(service, "communityRepository", communityRepository);
        setField(service, "staffService", staffService);
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

    private Staff staff() {
        Staff staff = new Staff();
        staff.setId(STAFF_ID);
        staff.setCommunity(community(COMMUNITY_ID));
        staff.setName("Ramesh Kumar");
        staff.setRole(StaffRole.SECURITY);
        staff.setActive(true);
        return staff;
    }

    private void stubActiveMember(CommunityMember m) {
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
    }

    @Test
    void markAttendance_createsNewRecord_whenNoneExistsForThatDate() {
        StaffAttendanceService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        when(staffRepository.findByIdAndCommunity_Id(STAFF_ID, COMMUNITY_ID)).thenReturn(Optional.of(staff()));
        LocalDate today = LocalDate.now();
        when(attendanceRepository.findByStaff_IdAndAttendanceDate(STAFF_ID, today)).thenReturn(Optional.empty());
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(attendanceRepository.save(any(StaffAttendance.class))).thenAnswer(invocation -> {
            StaffAttendance a = invocation.getArgument(0);
            a.setId(100L);
            return a;
        });

        MarkAttendanceRequest request = new MarkAttendanceRequest();
        request.setAttendanceDate(today);
        request.setStatus(AttendanceStatus.PRESENT);

        StaffAttendanceResponse response = service.markAttendance(COMMUNITY_ID, STAFF_ID, request, CALLER_IDENTITY);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(response.getMarkedByMemberId()).isEqualTo(5L);
    }

    @Test
    void markAttendance_correctsExistingRecord_whenAlreadyMarkedForThatDate() {
        StaffAttendanceService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        when(staffRepository.findByIdAndCommunity_Id(STAFF_ID, COMMUNITY_ID)).thenReturn(Optional.of(staff()));
        LocalDate today = LocalDate.now();

        StaffAttendance existing = new StaffAttendance();
        existing.setId(100L);
        existing.setCommunity(community(COMMUNITY_ID));
        existing.setStaff(staff());
        existing.setAttendanceDate(today);
        existing.setStatus(AttendanceStatus.ABSENT);
        existing.setMarkedBy(member(5L, CommunityRole.ADMIN));
        when(attendanceRepository.findByStaff_IdAndAttendanceDate(STAFF_ID, today)).thenReturn(Optional.of(existing));
        when(attendanceRepository.save(any(StaffAttendance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MarkAttendanceRequest request = new MarkAttendanceRequest();
        request.setAttendanceDate(today);
        request.setStatus(AttendanceStatus.PRESENT);

        StaffAttendanceResponse response = service.markAttendance(COMMUNITY_ID, STAFF_ID, request, CALLER_IDENTITY);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        verify(attendanceRepository, times(1)).save(any(StaffAttendance.class));
    }

    @Test
    void listAttendance_returnsHistory_orderedNewestFirst() {
        StaffAttendanceService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        when(staffRepository.findByIdAndCommunity_Id(STAFF_ID, COMMUNITY_ID)).thenReturn(Optional.of(staff()));

        StaffAttendance attendance = new StaffAttendance();
        attendance.setId(100L);
        attendance.setCommunity(community(COMMUNITY_ID));
        attendance.setStaff(staff());
        attendance.setAttendanceDate(LocalDate.now());
        attendance.setStatus(AttendanceStatus.PRESENT);
        attendance.setMarkedBy(admin);
        when(attendanceRepository.findByStaff_IdOrderByAttendanceDateDesc(STAFF_ID)).thenReturn(List.of(attendance));

        List<StaffAttendanceResponse> result = service.listAttendance(COMMUNITY_ID, STAFF_ID, CALLER_IDENTITY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStaffId()).isEqualTo(STAFF_ID);
    }
}
