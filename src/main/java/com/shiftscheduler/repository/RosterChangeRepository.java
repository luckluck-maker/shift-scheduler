package com.shiftscheduler.repository;

import com.shiftscheduler.domain.RosterChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// Who still needs to be told their published week changed.
public interface RosterChangeRepository extends JpaRepository<RosterChange, Long> {

    Optional<RosterChange> findByScheduleIdAndEmployeeIdAndShiftId(
            Long scheduleId, Long employeeId, Long shiftId);

    List<RosterChange> findByScheduleId(Long scheduleId);

    List<RosterChange> findByEmployeeId(Long employeeId);

    // People, not rows: moving one person across three shifts is still one mail.
    // Someone who has since been disabled is left out, so the number the manager
    // sees is the number of mails that will actually go out.
    @Query("""
            select count(distinct rc.employee.id) from RosterChange rc
            where rc.schedule.id = :scheduleId
              and rc.employee.active = true
            """)
    long countEmployeesByScheduleId(@Param("scheduleId") Long scheduleId);
}
