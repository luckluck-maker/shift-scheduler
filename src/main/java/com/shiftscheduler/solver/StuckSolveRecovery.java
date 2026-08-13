package com.shiftscheduler.solver;

import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.repository.ScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StuckSolveRecovery {

    private static final Logger log = LoggerFactory.getLogger(StuckSolveRecovery.class);
    private final ScheduleRepository scheduleRepository;

    public StuckSolveRecovery(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    // On startup nothing is actually solving, so any schedule still marked
    // SOLVING is a leftover from a crash. Put it back to DRAFT so the
    // manager can edit or run it again instead of being stuck.
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void releaseStuck() {
        for (Schedule schedule : scheduleRepository.findByStatus(ScheduleStatus.SOLVING)) {
            schedule.setStatus(ScheduleStatus.DRAFT);
            schedule.touch();
            log.warn("Released schedule {} stuck in SOLVING after restart", schedule.getId());
        }
    }
}
