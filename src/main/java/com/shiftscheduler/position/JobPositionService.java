package com.shiftscheduler.position;

import com.shiftscheduler.domain.JobPosition;
import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.repository.EmployeeRepository;
import com.shiftscheduler.repository.JobPositionRepository;
import com.shiftscheduler.repository.ShiftRequirementRepository;
import com.shiftscheduler.web.ConflictException;
import com.shiftscheduler.web.ResourceNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Same versioning as shift types, but simpler: a position is only a name, so
// there is nothing that could change and leave a published week reading wrong.
// Renaming applies everywhere and no new version is ever needed.
//
// Deleting is the one thing that can be refused. An employee has to hold a
// position, so anyone still in it needs moving first.
@Service
public class JobPositionService {

    private final JobPositionRepository jobPositionRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftRequirementRepository requirementRepository;

    public JobPositionService(JobPositionRepository jobPositionRepository,
                              EmployeeRepository employeeRepository,
                              ShiftRequirementRepository requirementRepository) {
        this.jobPositionRepository = jobPositionRepository;
        this.employeeRepository = employeeRepository;
        this.requirementRepository = requirementRepository;
    }

    @Transactional(readOnly = true)
    public List<JobPositionResponse> findAll() {
        return jobPositionRepository.findByActiveTrue(Sort.by("name")).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobPositionResponse findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public JobPositionResponse create(JobPositionRequest request) {
        String name = request.name().trim();

        requireNameFree(name, null);

        // Recreating a position that was removed brings the old row back, so
        // published weeks that referred to it line up again.
        JobPosition position = jobPositionRepository
                .findByNameIgnoreCaseAndActiveFalse(name)
                .orElseGet(JobPosition::new);

        position.setName(name);
        position.setActive(true);

        return toResponse(position.getId() == null
                ? jobPositionRepository.save(position)
                : position);
    }

    @Transactional
    public JobPositionResponse update(Long id, JobPositionRequest request) {
        JobPosition position = require(id);
        String name = request.name().trim();

        requireNameFree(name, id);
        position.setName(name);

        return toResponse(position);
    }

    @Transactional
    public void delete(Long id) {
        JobPosition position = require(id);

        if (employeeRepository.existsByJobPositionIdAndActiveTrue(id)) {
            throw new ConflictException("Employees are still assigned to this position");
        }

        requirementRepository.deleteByJobPositionIdAndShiftScheduleStatusNot(
                id, ScheduleStatus.PUBLISHED);

        position.setActive(false);
    }

    // Replaces the unique index that used to be on the name. Removed positions
    // keep theirs, so uniqueness only holds among the active ones.
    private void requireNameFree(String name, Long excludeId) {
        jobPositionRepository.findByNameIgnoreCaseAndActiveTrue(name)
                .filter(other -> !other.getId().equals(excludeId))
                .ifPresent(other -> {
                    throw new ConflictException(
                            "A job position named '" + name + "' already exists");
                });
    }

    private JobPosition require(Long id) {
        return jobPositionRepository.findById(id)
                .filter(JobPosition::isActive)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Job position " + id + " not found"));
    }

    private JobPositionResponse toResponse(JobPosition position) {
        return new JobPositionResponse(position.getId(), position.getName());
    }
}