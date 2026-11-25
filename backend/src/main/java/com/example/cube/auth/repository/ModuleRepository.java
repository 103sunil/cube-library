package com.example.cube.auth.repository;

import com.example.cube.auth.entity.Module;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModuleRepository extends JpaRepository<Module, Long> {

    Optional<Module> findByModuleCode(String moduleCode);

    boolean existsByModuleCodeIgnoreCase(String moduleCode);
}
