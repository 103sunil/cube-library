package com.example.cube.auth.repository;

import com.example.cube.auth.entity.Access;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccessRepository extends JpaRepository<Access, Long> {

    Optional<Access> findByModuleIdAndAccessCode(Long moduleId, String accessCode);

    boolean existsByModuleIdAndAccessCodeIgnoreCase(Long moduleId, String accessCode);

    boolean existsByModuleId(Long moduleId);

    List<Access> findByModuleId(Long moduleId);
}
