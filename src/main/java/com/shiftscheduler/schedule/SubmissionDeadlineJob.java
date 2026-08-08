package com.shiftscheduler.schedule;

import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.repository.ScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

// Closes the submission window when its time comes, so the manager doesn't
// have to remember to. Just advancing the schedule status to DRAFT, as all access
// control (who can edit, when assignment starts) is already handled by the status checks.
@Component
public class SubmissionDeadlineJob {

    private static final Logger log = LoggerFactory.getLogger(SubmissionDeadlineJob.class);

    private final ScheduleRepository scheduleRepository;

    public SubmissionDeadlineJob(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    // Runs every minute (not critical if close happens after minute)
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void closeExpired() {

        log.debug("Checking for expired submission windows");

        List<Schedule> due = scheduleRepository.findByStatusAndSubmissionClosesAtBefore(
                ScheduleStatus.COLLECTING, Instant.now());

        for (Schedule schedule : due) {
            schedule.setStatus(ScheduleStatus.DRAFT);
            schedule.touch();

            log.info("Submission closed automatically for the week of {}",
                    schedule.getWeekStart());
        }
    }
}