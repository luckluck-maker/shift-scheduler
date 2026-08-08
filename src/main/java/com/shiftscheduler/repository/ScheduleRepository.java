package com.shiftscheduler.repository;

import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.domain.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;


public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    boolean existsByWeekStart(LocalDate weekStart);

    List<Schedule> findAllByOrderByWeekStartDesc();

    List<Schedule> findByStatusInOrderByWeekStartDesc(Collection<ScheduleStatus> statuses);

    Optional<Schedule> findFirstByWeekStartLessThanOrderByWeekStartDesc(LocalDate weekStart);

    List<Schedule> findByStatusAndSubmissionClosesAtBefore(
            ScheduleStatus status, Instant cutoff);
}