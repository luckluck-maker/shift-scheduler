package com.shiftscheduler.repository;

import com.shiftscheduler.domain.ShiftType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ShiftTypeRepository extends JpaRepository<ShiftType, Long> {

    List<ShiftType> findByActiveTrueOrderByStartTimeAsc();

    Optional<ShiftType> findByNameIgnoreCaseAndActiveTrue(String name);

    // Used to bring an old version back instead of adding an identical row.
    Optional<ShiftType> findByNameIgnoreCaseAndStartTimeAndEndTimeAndActiveFalse(
            String name, LocalTime startTime, LocalTime endTime);
}