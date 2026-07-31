package com.shiftscheduler.shifttype;

import com.shiftscheduler.domain.ShiftType;
import com.shiftscheduler.repository.ShiftRepository;
import com.shiftscheduler.repository.ShiftTypeRepository;
import com.shiftscheduler.web.ConflictException;
import com.shiftscheduler.web.ResourceNotFoundException;
import com.shiftscheduler.web.ValidationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

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
        return shiftTypeRepository.findAll(Sort.by("startTime")).stream()
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

        if (shiftTypeRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A shift type named '" + name + "' already exists");
        }

        ShiftType shiftType = new ShiftType();
        apply(shiftType, name, request);

        return toResponse(shiftTypeRepository.save(shiftType));
    }

    @Transactional
    public ShiftTypeResponse update(Long id, ShiftTypeRequest request) {
        ShiftType shiftType = require(id);
        String name = request.name().trim();

        if (shiftTypeRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A shift type named '" + name + "' already exists");
        }

        apply(shiftType, name, request);

        return toResponse(shiftType);
    }

    @Transactional
    public void delete(Long id) {
        ShiftType shiftType = require(id);

        if (shiftRepository.existsByShiftTypeId(id)) {
            throw new ConflictException(
                    "Cannot delete a shift type that is used by existing shifts");
        }

        shiftTypeRepository.delete(shiftType);
    }

    private void apply(ShiftType shiftType, String name, ShiftTypeRequest request) {
        LocalTime start = request.startTime();
        LocalTime end = request.endTime();

        if (start.equals(end)) {
            throw new ValidationException("startTime and endTime must differ");
        }

        shiftType.setName(name);
        shiftType.setStartTime(start);
        shiftType.setEndTime(end);
        shiftType.setCrossesMidnight(end.isBefore(start));
    }

    private ShiftType require(Long id) {
        return shiftTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift type " + id + " not found"));
    }

    private ShiftTypeResponse toResponse(ShiftType shiftType) {
        return new ShiftTypeResponse(
                shiftType.getId(),
                shiftType.getName(),
                shiftType.getStartTime(),
                shiftType.getEndTime(),
                shiftType.isCrossesMidnight(),
                durationHours(shiftType)
        );
    }

    private long durationHours(ShiftType shiftType) {
        Duration duration = Duration.between(shiftType.getStartTime(), shiftType.getEndTime());

        if (shiftType.isCrossesMidnight()) {
            duration = duration.plusHours(24);
        }

        return duration.toHours();
    }
}