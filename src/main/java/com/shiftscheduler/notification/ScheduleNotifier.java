package com.shiftscheduler.notification;

import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.repository.ScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

// Sends the emails after a week is published.
// The publish request only puts the schedule id on a topic and returns, so
// the manager isn't left waiting on the mail server.
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

    // The message holds the id, not the week itself, so it is loaded here.
    @JmsListener(destination = TOPIC)
    public void onSchedulePublished(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);

        // Nothing in the app deletes a schedule. The id is only missing if the
        // database was recreated while the message was still in the queue.
        if (schedule == null) {
            log.warn("Got a published event for schedule {}, which no longer exists", scheduleId);
            return;
        }

        log.info("Schedule {} was published, sending notifications", scheduleId);

        mailer.sendSchedulePublished(schedule);
    }
}