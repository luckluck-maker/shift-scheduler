package com.shiftscheduler.repository;

import com.shiftscheduler.domain.ShiftPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// The constraints employees submitted.
public interface ShiftPreferenceRepository extends JpaRepository<ShiftPreference, Long> {

    boolean existsByEmployeeIdAndShiftId(Long employeeId, Long shiftId);

    List<ShiftPreference> findByShiftScheduleIdOrderByShiftShiftDateAscIdAsc(Long scheduleId);

    List<ShiftPreference> findByShiftScheduleIdAndEmployeeIdOrderByShiftShiftDateAscIdAsc(
            Long scheduleId, Long employeeId);
}
