package com.shiftscheduler.repository;

import com.shiftscheduler.domain.ShiftType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftTypeRepository extends JpaRepository<ShiftType, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}