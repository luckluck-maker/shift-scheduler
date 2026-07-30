package com.shiftscheduler.position;

import com.shiftscheduler.domain.JobPosition;
import com.shiftscheduler.repository.EmployeeRepository;
import com.shiftscheduler.repository.JobPositionRepository;
import com.shiftscheduler.web.ConflictException;
import com.shiftscheduler.web.ResourceNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobPositionService {

    private final JobPositionRepository jobPositionRepository;
    private final EmployeeRepository employeeRepository;

    public JobPositionService(JobPositionRepository jobPositionRepository,
                              EmployeeRepository employeeRepository) {
        this.jobPositionRepository = jobPositionRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<JobPositionResponse> findAll() {
        return jobPositionRepository.findAll(Sort.by("name")).stream()
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

        if (jobPositionRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A job position named '" + name + "' already exists");
        }

        JobPosition position = new JobPosition();
        position.setName(name);

        return toResponse(jobPositionRepository.save(position));
    }

    @Transactional
    public JobPositionResponse update(Long id, JobPositionRequest request) {
        JobPosition position = require(id);
        String name = request.name().trim();

        if (jobPositionRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A job position named '" + name + "' already exists");
        }

        position.setName(name);

        return toResponse(position);
    }

    @Transactional
    public void delete(Long id) {
        JobPosition position = require(id);

        if (employeeRepository.existsByJobPositionId(id)) {
            throw new ConflictException(
                    "Cannot delete a job position that is assigned to employees");
        }

        jobPositionRepository.delete(position);
    }

    private JobPosition require(Long id) {
        return jobPositionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job position " + id + " not found"));
    }

    private JobPositionResponse toResponse(JobPosition position) {
        return new JobPositionResponse(position.getId(), position.getName());
    }
}