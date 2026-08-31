package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.StaffAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffAttendanceRepository extends JpaRepository<StaffAttendance, Long> {

    Optional<StaffAttendance> findByStaff_IdAndAttendanceDate(Long staffId, LocalDate attendanceDate);

    List<StaffAttendance> findByStaff_IdOrderByAttendanceDateDesc(Long staffId);
}
