package com.shiftscheduler.repository;

import com.shiftscheduler.domain.Assignment;
import com.shiftscheduler.domain.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    boolean existsByShiftIdAndEmployeeId(Long shiftId, Long employeeId);

    List<Assignment> findByShiftId(Long shiftId);

    List<Assignment> findByShiftScheduleIdOrderByShiftShiftDateAscIdAsc(Long scheduleId);

    List<Assignment> findByShiftScheduleIdAndEmployeeId(Long scheduleId, Long employeeId);

    List<Assignment> findByShiftScheduleIdAndShiftIdIn(Long scheduleId,
                                                       Collection<Long> shiftIds);

    List<Assignment> findByEmployeeIdAndShiftIdIn(Long employeeId, Collection<Long> shiftIds);

    List<Assignment> findByEmployeeIdAndShiftShiftDateBetween(Long employeeId,
                                                              LocalDate from, LocalDate to);

    List<Assignment> findByEmployeeIdAndShiftScheduleStatusIn(
            Long employeeId, Collection<ScheduleStatus> statuses);

    // Assignments from the day before this week starts, from published schedules
    // only, so the rest rule still applies across the week boundary.
    @Query("""
            select a from Assignment a
            where a.shift.schedule.status = com.shiftscheduler.domain.ScheduleStatus.PUBLISHED
              and a.shift.shiftDate >= :from
              and a.shift.shiftDate < :weekStart
            """)
    List<Assignment> findPublishedBefore(@Param("from") LocalDate from,
                                         @Param("weekStart") LocalDate weekStart);
}