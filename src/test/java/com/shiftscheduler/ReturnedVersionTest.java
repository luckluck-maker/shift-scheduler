package com.shiftscheduler;

import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.domain.JobPosition;
import com.shiftscheduler.domain.PreferenceType;
import com.shiftscheduler.domain.Role;
import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.domain.Shift;
import com.shiftscheduler.domain.ShiftType;
import com.shiftscheduler.employee.EmployeeCreateRequest;
import com.shiftscheduler.employee.EmployeeService;
import com.shiftscheduler.employee.EmployeeUpdateRequest;
import com.shiftscheduler.preference.ShiftPreferenceCreateRequest;
import com.shiftscheduler.preference.ShiftPreferenceService;
import com.shiftscheduler.preference.ShiftPreferenceUpdateRequest;
import com.shiftscheduler.repository.AssignmentRepository;
import com.shiftscheduler.repository.EmployeeRepository;
import com.shiftscheduler.repository.JobPositionRepository;
import com.shiftscheduler.repository.ScheduleRepository;
import com.shiftscheduler.repository.ShiftPreferenceRepository;
import com.shiftscheduler.repository.ShiftRepository;
import com.shiftscheduler.repository.ShiftTypeRepository;
import com.shiftscheduler.schedule.DeadlineRequest;
import com.shiftscheduler.schedule.ScheduleCreateRequest;
import com.shiftscheduler.schedule.ScheduleService;
import com.shiftscheduler.schedule.VersionedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Checks that a call which changes something hands back the version the database
// ends up holding, so the caller can send that number straight back.
//
// A version is raised on flush, and a response built before the flush carries the
// number from before the change. Each test here reads the row again after the
// call and compares.
//
// publish and republish are left out, since publishing from a test sends real
// mail, and both of them raise the version through markChanged like the rest.
@SpringBootTest
class ReturnedVersionTest {

    @Autowired ScheduleRepository scheduleRepository;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired JobPositionRepository jobPositionRepository;
    @Autowired ShiftRepository shiftRepository;
    @Autowired ShiftTypeRepository shiftTypeRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired ShiftPreferenceRepository preferenceRepository;

    @Autowired ScheduleService scheduleService;
    @Autowired EmployeeService employeeService;
    @Autowired ShiftPreferenceService preferenceService;

    private final List<Runnable> cleanups = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (int i = cleanups.size() - 1; i >= 0; i--) {
            try {
                cleanups.get(i).run();
            } catch (RuntimeException ignored) {
                // Best effort, since a failed test may already have removed it.
            }
        }
        cleanups.clear();
        SecurityContextHolder.clearContext();
    }

    // The services ask who is calling, and nothing here goes through the HTTP
    // layer, so the context has to be put in place by hand.
    private void asManager() {
        Jwt token = Jwt.withTokenValue("test")
                .header("alg", "none")
                .claim("employeeId", 1L)
                .claim("role", "MANAGER")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token,
                        List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
    }

    private long storedScheduleVersion(Long id) {
        return scheduleRepository.findById(id).orElseThrow().getVersion();
    }

    private long storedEmployeeVersion(Long id) {
        return employeeRepository.findById(id).orElseThrow().getVersion();
    }

    private long storedPreferenceVersion(Long id) {
        return preferenceRepository.findById(id).orElseThrow().getVersion();
    }

    // ---------------------------------------------------------------- weeks

    @Test
    void creatingAWeekHandsBackTheStoredVersion() {
        asManager();
        LocalDate week = LocalDate.of(2032, 1, 4);
        clearWeek(week);

        var created = scheduleService.create(new ScheduleCreateRequest(week, null));
        cleanups.add(() -> clearWeek(week));

        assertThat(created.version()).isEqualTo(storedScheduleVersion(created.id()));
    }

    @Test
    void lockingAWeekHandsBackTheStoredVersion() {
        asManager();
        Schedule week = aWeekIn(ScheduleStatus.COLLECTING, LocalDate.of(2032, 2, 1));

        var locked = scheduleService.lock(week.getId(), new VersionedRequest(week.getVersion()));

        assertThat(locked.version()).isEqualTo(storedScheduleVersion(week.getId()));
    }

    @Test
    void settingTheDeadlineHandsBackTheStoredVersion() {
        asManager();
        Schedule week = aWeekIn(ScheduleStatus.COLLECTING, LocalDate.of(2032, 3, 7));
        Instant closes = Instant.now().plus(30, ChronoUnit.DAYS);

        var changed = scheduleService.setSubmissionDeadline(
                week.getId(), new DeadlineRequest(week.getVersion(), closes));

        assertThat(changed.version()).isEqualTo(storedScheduleVersion(week.getId()));
    }

    // ------------------------------------------------------------- the staff

    @Test
    void creatingAnEmployeeHandsBackTheStoredVersion() {
        asManager();
        JobPosition position = jobPositionRepository.findByActiveTrue(Sort.by("name")).get(0);
        String username = "created@version.test";
        removeByUsername(username);

        var created = employeeService.create(new EmployeeCreateRequest(
                "Test created", username, "password123", Role.EMPLOYEE, 40, position.getId()));
        cleanups.add(() -> removeByUsername(username));

        assertThat(created.version()).isEqualTo(storedEmployeeVersion(created.id()));
    }

    @Test
    void updatingAnEmployeeHandsBackTheStoredVersion() {
        asManager();
        Employee employee = anEmployee("updated");

        var updated = employeeService.update(employee.getId(), new EmployeeUpdateRequest(
                employee.getVersion(), "Renamed", Role.EMPLOYEE, 32,
                employee.getJobPosition().getId()));

        assertThat(updated.version()).isEqualTo(storedEmployeeVersion(employee.getId()));
    }

    @Test
    void activatingAnEmployeeHandsBackTheStoredVersion() {
        asManager();
        Employee employee = anEmployee("revived");
        employeeService.deactivate(employee.getId(), employee.getVersion());

        var activated = employeeService.activate(
                employee.getId(), storedEmployeeVersion(employee.getId()));

        assertThat(activated.version()).isEqualTo(storedEmployeeVersion(employee.getId()));
    }

    // ----------------------------------------------------------- constraints

    @Test
    void creatingAConstraintHandsBackTheStoredVersion() {
        asManager();
        Schedule week = aWeekIn(ScheduleStatus.COLLECTING, LocalDate.of(2032, 4, 4));
        Shift shift = shiftRepository.findByScheduleIdOrderByShiftDateAscIdAsc(week.getId()).get(0);
        Employee employee = anEmployee("prefnew");

        var created = preferenceService.create(new ShiftPreferenceCreateRequest(
                shift.getId(), PreferenceType.PREFERS_NOT, "first", employee.getId()));
        cleanups.add(() -> preferenceRepository.deleteById(created.id()));

        assertThat(created.version()).isEqualTo(storedPreferenceVersion(created.id()));
    }

    @Test
    void updatingAConstraintHandsBackTheStoredVersion() {
        asManager();
        Schedule week = aWeekIn(ScheduleStatus.COLLECTING, LocalDate.of(2032, 5, 2));
        Shift shift = shiftRepository.findByScheduleIdOrderByShiftDateAscIdAsc(week.getId()).get(0);
        Employee employee = anEmployee("prefedit");

        var created = preferenceService.create(new ShiftPreferenceCreateRequest(
                shift.getId(), PreferenceType.PREFERS_NOT, "first", employee.getId()));
        cleanups.add(() -> preferenceRepository.deleteById(created.id()));

        var updated = preferenceService.update(created.id(), new ShiftPreferenceUpdateRequest(
                created.version(), PreferenceType.CANNOT, "second"));

        assertThat(updated.version()).isEqualTo(storedPreferenceVersion(created.id()));
    }

    // ------------------------------------------------------------- fixtures

    private void clearWeek(LocalDate weekStart) {
        scheduleRepository.findAll().stream()
                .filter(s -> s.getWeekStart().equals(weekStart))
                .forEach(scheduleRepository::delete);
    }

    private Schedule aWeekIn(ScheduleStatus status, LocalDate weekStart) {
        clearWeek(weekStart);

        Schedule schedule = new Schedule();
        schedule.setWeekStart(weekStart);
        schedule.setStatus(status);
        schedule.touch();
        Schedule saved = scheduleRepository.save(schedule);

        ShiftType type = shiftTypeRepository.findByActiveTrueOrderByStartTimeAsc().get(0);

        for (int day = 0; day < 3; day++) {
            Shift shift = new Shift();
            shift.setSchedule(saved);
            shift.setShiftDate(weekStart.plusDays(day));
            shift.setShiftType(type);
            shiftRepository.save(shift);
        }

        cleanups.add(() -> scheduleRepository.findById(saved.getId())
                .ifPresent(scheduleRepository::delete));

        return scheduleRepository.findById(saved.getId()).orElseThrow();
    }

    private Employee anEmployee(String tag) {
        JobPosition position = jobPositionRepository.findByActiveTrue(Sort.by("name")).get(0);

        // A run that failed part way through can leave its people behind, so
        // the fixture clears its own name first and the test stays repeatable.
        String username = tag + "@version.test";
        removeByUsername(username);

        Employee employee = new Employee();
        employee.setFullName("Test " + tag);
        employee.setUsername(username);
        employee.setPasswordHash("x");
        employee.setRole(Role.EMPLOYEE);
        employee.setMaxWeeklyHours(40);
        employee.setActive(true);
        employee.setJobPosition(position);

        Employee saved = employeeRepository.save(employee);
        cleanups.add(() -> employeeRepository.findById(saved.getId())
                .ifPresent(this::forceRemove));

        return saved;
    }

    private void removeByUsername(String username) {
        employeeRepository.findAll().stream()
                .filter(e -> username.equalsIgnoreCase(e.getUsername()))
                .forEach(this::forceRemove);
    }

    // Deletes their assignments first since those rows point at the employee.
    private void forceRemove(Employee employee) {
        assignmentRepository.deleteAll(
                assignmentRepository.findByEmployeeIdAndShiftScheduleStatusIn(
                        employee.getId(), List.of(ScheduleStatus.values())));
        employeeRepository.delete(employee);
    }
}
