package com.shiftscheduler.repository;

import com.shiftscheduler.domain.ShiftRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShiftRequirementRepository extends JpaRepository<ShiftRequirement, Long> {

    List<ShiftRequirement> findByShiftIdOrderByIdAsc(Long shiftId);

    List<ShiftRequirement> findByShiftScheduleIdOrderByIdAsc(Long scheduleId);

    void deleteByShiftId(Long shiftId);
}