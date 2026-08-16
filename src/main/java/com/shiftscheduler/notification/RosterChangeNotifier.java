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

// Reacts when a manager republishes a week that was changed by hand.
// Reads who is waiting, mails them, and clears the list so the next
// republish only covers what changed since this one.
@Component
public class RosterChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(RosterChangeNotifier.class);

    public static final String TOPIC = "schedule.republished";

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

    @JmsListener(destination = TOPIC)
    @Transactional
    public void onRepublished(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);

        if (schedule == null) {
            log.warn("Got a republish event for schedule {}, which no longer exists", scheduleId);
            return;
        }

        List<RosterChange> pending = rosterChangeRepository.findByScheduleId(scheduleId);

        if (pending.isEmpty()) {
            log.warn("Schedule {} was republished with nobody waiting", scheduleId);
            return;
        }

        // One person can have more than one shift waiting, and they only need
        // telling once. Keyed by id rather than by the entity, so this does not
        // rely on Employee having an equals of its own.
        //
        // Someone disabled since the change is skipped, the same way the publish
        // mail only goes to active staff. They are signed out of the app, so
        // telling them to go and look at their shifts leads nowhere.
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
