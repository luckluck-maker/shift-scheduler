package com.shiftscheduler.repository;

import com.shiftscheduler.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    boolean existsByShiftIdAndEmployeeId(Long shiftId, Long employeeId);

    List<Assignment> findByShiftId(Long shiftId);

    List<Assignment> findByShiftScheduleIdOrderByShiftShiftDateAscIdAsc(Long scheduleId);

    List<Assignment> findByShiftScheduleIdAndEmployeeId(Long scheduleId, Long employeeId);
}