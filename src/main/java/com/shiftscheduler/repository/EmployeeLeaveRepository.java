package com.shiftscheduler.repository;

import com.shiftscheduler.domain.EmployeeLeave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeLeaveRepository extends JpaRepository<EmployeeLeave, Long> {

    List<EmployeeLeave> findByOrderByEmployeeIdAscLeaveDateAsc();

    List<EmployeeLeave> findByEmployeeIdOrderByLeaveDateAsc(Long employeeId);

    List<EmployeeLeave> findByLeaveDate(LocalDate leaveDate);

    Optional<EmployeeLeave> findByEmployeeIdAndLeaveDate(Long employeeId, LocalDate leaveDate);

    List<EmployeeLeave> findByEmployeeIdAndLeaveDateBetween(
            Long employeeId, LocalDate from, LocalDate to);
}