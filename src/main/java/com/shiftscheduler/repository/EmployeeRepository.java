package com.shiftscheduler.repository;

import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.domain.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUsername(String username);
    Optional<Employee> findByIdAndActiveTrue(Long id);
    List<Employee> findByActiveTrue(Sort sort);

    boolean existsByJobPositionIdAndActiveTrue(Long jobPositionId);

    boolean existsByUsernameIgnoreCase(String username);

    // Locks the rows until the transaction ends. Created for the following edge case:
    // Two requests demoting different managers touch different rows, so @Version
    // won't catch it. Without the lock both read "2 managers" and both pass,
    // and we end up with zero.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.role = :role and e.active = true")
    List<Employee> lockActiveByRole(@Param("role") Role role);
}