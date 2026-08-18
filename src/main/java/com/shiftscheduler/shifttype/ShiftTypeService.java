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

// Shift types.
// A published week keeps the hours it was published with. Changing those hours
// makes a new row, and only planned weeks move to it.
// Anything else is edited in place.
@Service
public class ShiftTypeService {

    private final ShiftTypeRepository shiftTypeRepository;
    private final ShiftRepository shiftRepository;

    public ShiftTypeService(ShiftTypeRepository shiftTypeRepository,
                            ShiftRepository shiftRepository) {
        this.shiftTypeRepository = shiftTypeRepository;
        this.shiftRepository = shiftRepository;
    }

    // The live types, earliest start first.
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

    // An old version with the same name and hours is reused.
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

    // Shifts in weeks that were not published go with it. Published weeks keep
    // theirs.
    @Transactional
    public void delete(Long id) {
        ShiftType shiftType = require(id);

        shiftRepository.deleteByShiftTypeIdAndScheduleStatusNot(id, ScheduleStatus.PUBLISHED);
        shiftType.setActive(false);
    }

    // Changing the hours back to an old version brings that row back instead of
    // making a duplicate.
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

    // A name may only be used by one live shift type at a time. It can't be a
    // unique key, because old versions keep their name.
    // The write lock stops two requests both finding nothing and both saving.
    private void requireNameFree(String name, Long excludeId) {
        shiftTypeRepository.lockActive().stream()
                .filter(other -> other.getName().equalsIgnoreCase(name))
                .filter(other -> !other.getId().equals(excludeId))
                .findFirst()
                .ifPresent(other -> {
                    throw new ConflictException(
                            "A shift type named '" + name + "' already exists");
                });
    }

    // True when only the name is being changed.
    private boolean sameTimes(ShiftType shiftType, ShiftTypeRequest request) {
        return shiftType.getStartTime().equals(request.startTime())
                && shiftType.getEndTime().equals(request.endTime());
    }

    // A shift that starts and ends at the same time has no length.
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

    // Loads a live shift type, or 404.
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

    // A night shift ends the next day, so its length wraps past midnight.
    private long durationHours(LocalTime start, LocalTime end) {
        Duration duration = Duration.between(start, end);

        return duration.isNegative() ? duration.plusHours(24).toHours() : duration.toHours();
    }
}