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

        // Locked read rather than a plain exists, so somebody being moved into
        // this position right now finishes first and is seen.
        if (!employeeRepository.lockActiveByJobPosition(id).isEmpty()) {
            throw new ConflictException("Employees are still assigned to this position");
        }

        requirementRepository.deleteByJobPositionIdAndShiftScheduleStatusNot(
                id, ScheduleStatus.PUBLISHED);

        position.setActive(false);
    }

    // Replaces the unique index that used to be on the name. Removed positions
    // keep theirs, so uniqueness only holds among the active ones.
    // Reads the active positions under a write lock rather than querying for the
    // one name. Two managers adding the same name at the same moment would both
    // find nothing and both save it; this way the second one waits for the first
    // to finish and then sees it.
    private void requireNameFree(String name, Long excludeId) {
        jobPositionRepository.lockActive().stream()
                .filter(other -> other.getName().equalsIgnoreCase(name))
                .filter(other -> !other.getId().equals(excludeId))
                .findFirst()
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