package com.shiftscheduler.notification;

import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

// Builds and sends the emails. The address is the employee's username.
@Service
public class EmployeeMailer {

    private static final Logger log = LoggerFactory.getLogger(EmployeeMailer.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final JavaMailSender mailSender;
    private final EmployeeRepository employeeRepository;
    private final String from;

    public EmployeeMailer(JavaMailSender mailSender,
                          EmployeeRepository employeeRepository,
                          @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.employeeRepository = employeeRepository;
        this.from = from;
    }

    // Goes to everyone active, on a shift that week or not.
    public void sendSchedulePublished(Schedule schedule) {
        String weekStart = schedule.getWeekStart().format(DATE);
        // Only the start is stored. A week is always seven days.
        // Only the start is stored. A week is always seven days.
        String weekEnd = schedule.getWeekStart().plusDays(6).format(DATE);

        int sent = 0;

        for (Employee employee : employeeRepository.findByActiveTrue(Sort.by("fullName"))) {
            if (send(employee, weekStart, weekEnd)) {
                sent++;
            }
        }

        log.info("Sent {} notifications for the week of {}", sent, weekStart);
    }

    // Only to the people whose shifts changed after the week went out.
    // The mail doesn't list the changes, it says to sign in and look.
    public void sendRosterChanged(Schedule schedule, List<Employee> employees) {
        String weekStart = schedule.getWeekStart().format(DATE);
        // Only the start is stored. A week is always seven days.
        // Only the start is stored. A week is always seven days.
        String weekEnd = schedule.getWeekStart().plusDays(6).format(DATE);

        int sent = 0;

        for (Employee employee : employees) {
            if (sendChanged(employee, weekStart, weekEnd)) {
                sent++;
            }
        }

        log.info("Sent {} change notifications for the week of {}", sent, weekStart);
    }

    // The mail for a week that changed after it was published.
    private boolean sendChanged(Employee employee, String weekStart, String weekEnd) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(employee.getUsername());
        message.setSubject("Your shifts for " + weekStart + " have changed");
        message.setText("""
                Hi %s,

                The schedule for %s to %s was updated after it was published,
                and one of the changes affects you.
                Sign in to see your shifts.

                Shift Scheduler
                """.formatted(employee.getFullName(), weekStart, weekEnd));

        return send(message, employee);
    }

    // The mail for a week that was just published.
    private boolean send(Employee employee, String weekStart, String weekEnd) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(employee.getUsername());
        message.setSubject("Your shifts for " + weekStart + " are ready");
        message.setText("""
                Hi %s,

                The schedule for %s to %s has been published.
                Sign in to see which shifts you're on.

                Shift Scheduler
                """.formatted(employee.getFullName(), weekStart, weekEnd));

        return send(message, employee);
    }

    // Sends the email & if any send fails, continues sending the rest
    private boolean send(SimpleMailMessage message, Employee employee) {
        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.warn("Couldn't email {}: {}", employee.getUsername(), e.getMessage());
            return false;
        }
    }
}