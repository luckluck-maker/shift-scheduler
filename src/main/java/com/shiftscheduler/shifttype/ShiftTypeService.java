package com.shiftscheduler.shifttype;

import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.domain.ShiftType;
import com.shiftscheduler.repository.ShiftRepository;
import com.shiftscheduler.repository.ShiftTypeRepository;
import com.shiftscheduler.web.ConflictException;
import com.shiftscheduler.web.ResourceNotFoundException;
import com.shiftscheduler.web.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

// Shift types are kept as versions rather than edited in place. Once a week
// has been published it shouldn't change, and it points straight at the shift
// type - so changing the hours of one a published week uses leaves the old row
// alone, switched off, and carries on with a new one. Weeks still being
// planned move across.
//
// Only the active row is ever shown. The old ones exist so published weeks
// still make sense.
@Service
public class ShiftTypeService {

    private final ShiftTypeRepository shiftTypeRepository;
    private final ShiftRepository shiftRepository;

    public ShiftTypeService(ShiftTypeRepository shiftTypeRepository,
                            ShiftRepository shiftRepository) {
        this.shiftTypeRepository = shiftTypeRepository;
        this.shiftRepository = shiftRepository;
    }

    @Transactional(readOnly = true)
    public List<ShiftTypeResponse> findAll() {
        return shiftTypeRepository.findByActiveTrueOrderByStartTimeAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShiftTypeResponse findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public ShiftTypeResponse create(ShiftTypeRequest request) {
        String name = request.name().trim();

        requireDifferentTimes(request);
        requireNameFree(name, null);

        return toResponse(activate(request, name));
    }

    @Transactional
    public ShiftTypeResponse update(Long id, ShiftTypeRequest request) {
        ShiftType current = require(id);
        String name = request.name().trim();

        requireDifferentTimes(request);
        requireNameFree(name, id);

        // editing name for the same shift (hours), keep the shift
        if (sameTimes(current, request)) {
            current.setName(name);
            return toResponse(current);
        }

        if (!shiftRepository.existsByShiftTypeIdAndScheduleStatus(
                id, ScheduleStatus.PUBLISHED)) {

            // An old version might already match what's being asked for. If it
            // does, bring it back and let this row go rather than ending up
            // with two identical ones.
            ShiftType revived = shiftTypeRepository
                    .findByNameIgnoreCaseAndStartTimeAndEndTimeAndActiveFalse(
                            name, request.startTime(), request.endTime())
                    .orElse(null);

            if (revived == null) {
                apply(current, request, name);
                return toResponse(current);
            }

            revived.setActive(true);
            current.setActive(false);

            shiftRepository.movePlannedShifts(
                    current.getId(), revived.getId(), ScheduleStatus.PUBLISHED);

            return toResponse(revived);
        }

        current.setActive(false);

        ShiftType replacement = activate(request, name);
        shiftRepository.movePlannedShifts(
                id, replacement.getId(), ScheduleStatus.PUBLISHED);

        return toResponse(replacement);
    }

    // Shifts in weeks that haven't been published go with it - those are still
    // being planned, so an empty row for a type nobody wants is just noise.
    // Published weeks keep theirs.
    @Transactional
    public void delete(Long id) {
        ShiftType shiftType = require(id);

        shiftRepository.deleteByShiftTypeIdAndScheduleStatusNot(id, ScheduleStatus.PUBLISHED);
        shiftType.setActive(false);
    }

    // Changing the hours and changing them back shouldn't leave two identical
    // rows, so an old version that already matches comes back instead.
    private ShiftType activate(ShiftTypeRequest request, String name) {
        return shiftTypeRepository
                .findByNameIgnoreCaseAndStartTimeAndEndTimeAndActiveFalse(
                        name, request.startTime(), request.endTime())
                .map(existing -> {
                    existing.setActive(true);
                    return existing;
                })
                .orElseGet(() -> {
                    ShiftType created = new ShiftType();
                    apply(created, request, name);
                    return shiftTypeRepository.save(created);
                });
    }

    // Replaces the unique index that used to be on the name. It can't live in
    // the database any more, because old versions hold the same name.
    private void requireNameFree(String name, Long excludeId) {
        shiftTypeRepository.findByNameIgnoreCaseAndActiveTrue(name)
                .filter(other -> !other.getId().equals(excludeId))
                .ifPresent(other -> {
                    throw new ConflictException(
                            "A shift type named '" + name + "' already exists");
                });
    }

    private boolean sameTimes(ShiftType shiftType, ShiftTypeRequest request) {
        return shiftType.getStartTime().equals(request.startTime())
                && shiftType.getEndTime().equals(request.endTime());
    }

    private void requireDifferentTimes(ShiftTypeRequest request) {
        if (request.startTime().equals(request.endTime())) {
            throw new ValidationException("A shift can't start and end at the same time");
        }
    }

    // crossesMidnight is derived rather than asked for, so a night shift can't
    // be stored as if it ran backwards.
    private void apply(ShiftType shiftType, ShiftTypeRequest request, String name) {
        shiftType.setName(name);
        shiftType.setStartTime(request.startTime());
        shiftType.setEndTime(request.endTime());
        shiftType.setCrossesMidnight(request.endTime().isBefore(request.startTime()));
    }

    private ShiftType require(Long id) {
        return shiftTypeRepository.findById(id)
                .filter(ShiftType::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Shift type " + id + " not found"));
    }

    private ShiftTypeResponse toResponse(ShiftType shiftType) {
        return new ShiftTypeResponse(
                shiftType.getId(),
                shiftType.getName(),
                shiftType.getStartTime(),
                shiftType.getEndTime(),
                shiftType.isCrossesMidnight(),
                durationHours(shiftType.getStartTime(), shiftType.getEndTime()));
    }

    private long durationHours(LocalTime start, LocalTime end) {
        Duration duration = Duration.between(start, end);

        return duration.isNegative() ? duration.plusHours(24).toHours() : duration.toHours();
    }
}