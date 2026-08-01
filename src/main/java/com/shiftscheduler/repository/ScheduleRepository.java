package com.shiftscheduler.repository;

import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.domain.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    boolean existsByWeekStart(LocalDate weekStart);

    List<Schedule> findAllByOrderByWeekStartDesc();

    List<Schedule> findByStatusOrderByWeekStartDesc(ScheduleStatus status);

    Optional<Schedule> findFirstByWeekStartLessThanOrderByWeekStartDesc(LocalDate weekStart);
}