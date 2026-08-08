package com.shiftscheduler.repository;

import com.shiftscheduler.domain.JobPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobPositionRepository extends JpaRepository<JobPosition, Long> {

    List<JobPosition> findByActiveTrue(Sort sort);

    Optional<JobPosition> findByNameIgnoreCaseAndActiveTrue(String name);

    Optional<JobPosition> findByNameIgnoreCaseAndActiveFalse(String name);
}
