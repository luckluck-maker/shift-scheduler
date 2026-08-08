package com.shiftscheduler.repository;

import com.shiftscheduler.domain.ShiftRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import com.shiftscheduler.domain.ScheduleStatus;

import java.util.List;

public interface ShiftRequirementRepository extends JpaRepository<ShiftRequirement, Long> {

    List<ShiftRequirement> findByShiftIdOrderByIdAsc(Long shiftId);

    List<ShiftRequirement> findByShiftScheduleIdOrderByIdAsc(Long scheduleId);

    void deleteByJobPositionIdAndShiftScheduleStatusNot(
            Long jobPositionId, ScheduleStatus status);

    void deleteByShiftId(Long shiftId);
}