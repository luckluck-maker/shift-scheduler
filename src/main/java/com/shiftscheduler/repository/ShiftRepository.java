package com.shiftscheduler.repository;

import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.domain.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    List<Shift> findByScheduleIdOrderByShiftDateAscIdAsc(Long scheduleId);

    boolean existsByShiftTypeIdAndScheduleStatus(Long shiftTypeId, ScheduleStatus status);

    void deleteByShiftTypeIdAndScheduleStatusNot(Long shiftTypeId, ScheduleStatus status);

    int countByScheduleId(Long scheduleId);

    // Moves weeks that are still being planned onto the new version of a shift
    // type. Published ones stay where they are.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Shift s set s.shiftType.id = :toId
            where s.shiftType.id = :fromId and s.schedule.status <> :published
            """)
    void movePlannedShifts(@Param("fromId") Long fromId,
                           @Param("toId") Long toId,
                           @Param("published") ScheduleStatus published);

}

