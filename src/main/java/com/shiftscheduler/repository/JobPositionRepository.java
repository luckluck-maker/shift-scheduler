package com.shiftscheduler.repository;

import com.shiftscheduler.domain.JobPosition;
import org.springframework.data.domain.Sort;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

// Job positions.
public interface JobPositionRepository extends JpaRepository<JobPosition, Long> {

    List<JobPosition> findByActiveTrue(Sort sort);

    Optional<JobPosition> findByNameIgnoreCaseAndActiveTrue(String name);

    Optional<JobPosition> findByNameIgnoreCaseAndActiveFalse(String name);

    // A name may only be used by one live position at a time. It can't be a
    // unique key, because a removed position keeps its name and the table may
    // hold two rows called "Locksmith" - one removed, one live.
    // Loads the live ones locked, and JobPositionService compares the names.
    // Without FOR UPDATE two requests would both read, both find nothing, both save.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from JobPosition p where p.active = true")
    List<JobPosition> lockActive();
}
