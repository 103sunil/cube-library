package com.example.cube.auth.repository;

import com.example.cube.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByReportingManagerIdOrderByUsernameAsc(Long reportingManagerId);

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByEmployeeCodeIgnoreCaseAndUserIdNot(String employeeCode, Long userId);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndUserIdNot(String email, Long userId);

    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email, Pageable pageable);

    /**
     * Admin user list. removed = 'Y' lists only removed users (status D), 'N' everyone else.
     * pattern = '%' for all, otherwise '%text%' in lower case (matches username, email, employee ID).
     */
    @org.springframework.data.jpa.repository.Query("select u from AppUser u where "
            + "((:removed = 'Y' and u.status = 'D') or (:removed = 'N' and (u.status is null or u.status <> 'D'))) "
            + "and (:pattern = '%' or lower(u.username) like :pattern or lower(u.email) like :pattern "
            + "or lower(u.employeeCode) like :pattern)")
    Page<User> search(@org.springframework.data.repository.query.Param("removed") String removed,
                      @org.springframework.data.repository.query.Param("pattern") String pattern, Pageable pageable);
}
