package com.shiftscheduler.repository;

import com.shiftscheduler.domain.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    boolean existsByShiftTypeId(Long shiftTypeId);
}