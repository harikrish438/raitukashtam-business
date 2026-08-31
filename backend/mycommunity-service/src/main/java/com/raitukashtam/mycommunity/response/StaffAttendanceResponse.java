package com.raitukashtam.mycommunity.response;

import com.raitukashtam.mycommunity.entity.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffAttendanceResponse {
    private Long id;
    private Long communityId;
    private Long staffId;
    private String staffName;
    private LocalDate attendanceDate;
    private AttendanceStatus status;
    private Long markedByMemberId;
    private String markedByName;
}
