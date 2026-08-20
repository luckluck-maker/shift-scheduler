package com.shiftscheduler.notification;

import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Covers who gets a mail and what happens when one address fails. The wording
// is left out on purpose, since a test on it would only repeat the text.
class EmployeeMailerTest {

    private static final String URL = "http://localhost:8080";

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);

    private final EmployeeMailer mailer =
            new EmployeeMailer(sender, employees, "noreply@shiftscheduler.local", URL);

    private final Schedule schedule = schedule();
    private final Employee maya = employee(3L, "Maya", "maya@shiftscheduler.local");
    private final Employee omer = employee(4L, "Omer", "omer@shiftscheduler.local");
    private final Employee noa = employee(7L, "Noa", "noa@shiftscheduler.local");

    // Publishing tells everybody, whether they are on a shift that week or not,
    // so the list comes from the repository rather than from the assignments.
    @Test
    void publishingMailsEveryActiveEmployee() {
        when(employees.findByActiveTrue(any(Sort.class))).thenReturn(List.of(maya, omer, noa));

        mailer.sendSchedulePublished(schedule);

        assertThat(recipients(3)).containsExactlyInAnyOrder(
                maya.getUsername(), omer.getUsername(), noa.getUsername());
    }

    // The whole point of the second mail is that it goes to fewer people, so it
    // takes the list it is given and never asks the repository for one.
    @Test
    void aChangedRosterOnlyMailsThePeopleItIsGiven() {
        mailer.sendRosterChanged(schedule, List.of(maya));

        assertThat(recipients(1)).containsExactly(maya.getUsername());
        verifyNoInteractions(employees);
    }

    // One address that the mail server refuses must not cost the rest of the
    // week's notifications.
    @Test
    void anAddressThatFailsDoesNotStopTheRest() {
        when(employees.findByActiveTrue(any(Sort.class))).thenReturn(List.of(maya, omer, noa));
        doThrow(new MailSendException("mailbox full"))
                .doNothing()
                .when(sender).send(any(SimpleMailMessage.class));

        mailer.sendSchedulePublished(schedule);

        assertThat(recipients(3)).containsExactlyInAnyOrder(
                maya.getUsername(), omer.getUsername(), noa.getUsername());
    }

    // The mail says to look in the app rather than listing the shifts, so the
    // address has to be in it.
    @Test
    void bothMailsCarryTheLinkToTheApp() {
        when(employees.findByActiveTrue(any(Sort.class))).thenReturn(List.of(maya));

        mailer.sendSchedulePublished(schedule);
        mailer.sendRosterChanged(schedule, List.of(maya));

        assertThat(captured(2)).allSatisfy(
                message -> assertThat(message.getText()).contains(URL));
    }

    private List<String> recipients(int count) {
        return captured(count).stream()
                .map(message -> message.getTo()[0])
                .toList();
    }

    private List<SimpleMailMessage> captured(int count) {
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(count)).send(sent.capture());
        return sent.getAllValues();
    }

    private static Schedule schedule() {
        Schedule schedule = new Schedule();
        schedule.setId(1L);
        schedule.setWeekStart(LocalDate.of(2026, 8, 9));
        return schedule;
    }

    private static Employee employee(Long id, String name, String username) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setFullName(name);
        employee.setUsername(username);
        return employee;
    }
}
