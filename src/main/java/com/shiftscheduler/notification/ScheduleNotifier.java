package com.shiftscheduler.notification;

import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.repository.ScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

// Reacts when a schedule gets published: sends everyone their email.
// Spring runs it when a message arrives on the topic.
@Component
public class ScheduleNotifier {

    private static final Logger log = LoggerFactory.getLogger(ScheduleNotifier.class);

    public static final String TOPIC = "schedule.published";

    private final ScheduleRepository scheduleRepository;
    private final EmployeeMailer mailer;

    public ScheduleNotifier(ScheduleRepository scheduleRepository, EmployeeMailer mailer) {
        this.scheduleRepository = scheduleRepository;
        this.mailer = mailer;
    }

    @JmsListener(destination = TOPIC)
    public void onSchedulePublished(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);

        if (schedule == null) {
            log.warn("Got a published event for schedule {}, which no longer exists", scheduleId);
            return;
        }

        log.info("Schedule {} was published, sending notifications", scheduleId);

        mailer.sendSchedulePublished(schedule);
    }
}