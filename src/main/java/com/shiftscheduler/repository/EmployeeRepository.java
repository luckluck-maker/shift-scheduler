package com.shiftscheduler.repository;

import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUsername(String username);

    boolean existsByJobPositionId(Long jobPositionId);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRoleAndActiveTrue(Role role);
}