package com.shiftscheduler.notification;

import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.domain.RosterChange;
import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.repository.RosterChangeRepository;
import com.shiftscheduler.repository.ScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

// Sends the emails after a week is republished.
// Reads who is waiting, mails them, and clears the list, so the next
// republish only covers what changed after this one.
@Component
public class RosterChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(RosterChangeNotifier.class);

    public static final String QUEUE = "schedule.republished";

    private final ScheduleRepository scheduleRepository;
    private final RosterChangeRepository rosterChangeRepository;
    private final EmployeeMailer mailer;

    public RosterChangeNotifier(ScheduleRepository scheduleRepository,
                                RosterChangeRepository rosterChangeRepository,
                                EmployeeMailer mailer) {
        this.scheduleRepository = scheduleRepository;
        this.rosterChangeRepository = rosterChangeRepository;
        this.mailer = mailer;
    }

    // The message holds the id, not the week itself, so it is loaded here.
    // Transactional for the delete at the end.
    @JmsListener(destination = QUEUE)
    @Transactional
    public void onRepublished(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);

        // Nothing in the app deletes a schedule. The id is only missing if the
        // database was recreated while the message was still in the queue.
        if (schedule == null) {
            log.warn("Got a republish event for schedule {}, which no longer exists", scheduleId);
            return;
        }

        List<RosterChange> pending = rosterChangeRepository.findByScheduleId(scheduleId);

        // Republish already checked that somebody is waiting. Disabling an employee
        // deletes their rows, so the list can still be empty by the time this runs.
        if (pending.isEmpty()) {
            log.warn("Schedule {} was republished with nobody waiting", scheduleId);
            return;
        }

        // The same person can have more than one shift waiting, and needs one mail.
        //
        // Someone disabled since then is skipped. They can't sign in anyway.
        List<Employee> employees = new ArrayList<>(pending.stream()
                .map(RosterChange::getEmployee)
                .filter(Employee::isActive)
                .collect(Collectors.toMap(Employee::getId, e -> e, (first, second) -> first,
                        LinkedHashMap::new))
                .values());

        log.info("Schedule {} was republished, notifying {} employees",
                scheduleId, employees.size());

        mailer.sendRosterChanged(schedule, employees);

        // Cleared only after the mails went out, so a crash mid-send leaves the
        // rows in place and the manager can republish again.
        rosterChangeRepository.deleteAll(pending);
    }
}
