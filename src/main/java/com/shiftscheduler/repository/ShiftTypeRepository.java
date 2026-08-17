package com.shiftscheduler.repository;

import com.shiftscheduler.domain.ShiftType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

// Shift types.
public interface ShiftTypeRepository extends JpaRepository<ShiftType, Long> {

    List<ShiftType> findByActiveTrueOrderByStartTimeAsc();

    Optional<ShiftType> findByNameIgnoreCaseAndActiveTrue(String name);

    // Used to bring an old version back instead of adding an identical row.
    Optional<ShiftType> findByNameIgnoreCaseAndStartTimeAndEndTimeAndActiveFalse(
            String name, LocalTime startTime, LocalTime endTime);

    // A name may only be used by one live shift type at a time. It can't be a
    // unique key, because a removed type keeps its name and the table may hold
    // two rows called "Night" - one removed, one live.
    // Loads the live ones locked, and ShiftTypeService compares the names.
    // Without FOR UPDATE two requests would both read, both find nothing, both save.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ShiftType t where t.active = true")
    List<ShiftType> lockActive();
}