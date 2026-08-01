package com.shiftscheduler.repository;

import com.shiftscheduler.domain.EmployeeLeave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EmployeeLeaveRepository extends JpaRepository<EmployeeLeave, Long> {

    List<EmployeeLeave> findByOrderByStartDateDesc();

    List<EmployeeLeave> findByEmployeeIdOrderByStartDateDesc(Long employeeId);

    boolean existsByEmployeeIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, LocalDate rangeEnd, LocalDate rangeStart);

    boolean existsByEmployeeIdAndIdNotAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, Long excludedId, LocalDate rangeEnd, LocalDate rangeStart);

    List<EmployeeLeave> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(
            LocalDate rangeEnd, LocalDate rangeStart);
}
