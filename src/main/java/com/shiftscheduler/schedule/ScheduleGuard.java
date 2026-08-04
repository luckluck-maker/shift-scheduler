package com.shiftscheduler.schedule;

import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.repository.ScheduleRepository;
import com.shiftscheduler.web.ConflictException;
import com.shiftscheduler.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.stream.Collectors;

// The checks every schedule action needs: does it exist, does its status
// allow this, and is the client's version current.
@Service
public class ScheduleGuard {

    private final ScheduleRepository scheduleRepository;

    public ScheduleGuard(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    public Schedule require(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule " + id + " not found"));
    }

    // Checks if the action is allowed in the current schedule status.
    // If not, tells the user what the status is and which ones would allow it.
    public void requireStatus(Schedule schedule, ScheduleStatus... allowed) {
        boolean ok = Arrays.asList(allowed).contains(schedule.getStatus());

        if (!ok) {
            String expected = Arrays.stream(allowed)
                    .map(Enum::name)
                    .collect(Collectors.joining(" or "));

            throw new ConflictException(
                    "This action needs the schedule to be " + expected
                            + ", but it is " + schedule.getStatus());
        }
    }

    public void requireVersion(Schedule schedule, Long expected) {
        if (expected != null && expected != schedule.getVersion()) {
            throw new ConflictException(
                    "The schedule was changed by someone else. Reload and try again.");
        }
    }

    // Marks after any change to the schedule,
    // so a client holding an older copy gets told to reload.
    public void markChanged(Schedule schedule) {
        schedule.touch();
    }
}