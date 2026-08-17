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

// Staff lookups. findByIdAndActiveTrue is the one to use anywhere a
// disabled employee must not show up.
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUsername(String username);
    Optional<Employee> findByIdAndActiveTrue(Long id);
    List<Employee> findByActiveTrue(Sort sort);

    boolean existsByJobPositionIdAndActiveTrue(Long jobPositionId);

    // A position can only be retired while nobody holds it. Without FOR UPDATE
    // a request moving somebody into it could land between the check and the
    // switch-off, leaving a live employee on a position that is gone.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.jobPosition.id = :id and e.active = true")
    List<Employee> lockActiveByJobPosition(@Param("id") Long id);

    boolean existsByUsernameIgnoreCase(String username);

    // At least one manager has to stay active. Two requests demoting different
    // managers write different rows, so @Version won't see a clash - both would
    // read "2 managers", both would pass, and none would be left.
    // FOR UPDATE makes the second one wait and count again.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.role = :role and e.active = true")
    List<Employee> lockActiveByRole(@Param("role") Role role);
}