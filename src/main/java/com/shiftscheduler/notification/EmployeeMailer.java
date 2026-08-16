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
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

// Builds and sends the emails.
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

    public void sendSchedulePublished(Schedule schedule) {
        String weekStart = schedule.getWeekStart().format(DATE);
        String weekEnd = schedule.getWeekStart().plusDays(6).format(DATE);

        int sent = 0;

        for (Employee employee : employeeRepository.findByActiveTrue(Sort.by("fullName"))) {
            if (send(employee, weekStart, weekEnd)) {
                sent++;
            }
        }

        log.info("Sent {} notifications for the week of {}", sent, weekStart);
    }

    // Same mail, but only to the people the manager actually moved after the
    // week went out. Getting it at all is the message: your week changed.
    public void sendRosterChanged(Schedule schedule, List<Employee> employees) {
        String weekStart = schedule.getWeekStart().format(DATE);
        String weekEnd = schedule.getWeekStart().plusDays(6).format(DATE);

        int sent = 0;

        for (Employee employee : employees) {
            if (sendChanged(employee, weekStart, weekEnd)) {
                sent++;
            }
        }

        log.info("Sent {} change notifications for the week of {}", sent, weekStart);
    }

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