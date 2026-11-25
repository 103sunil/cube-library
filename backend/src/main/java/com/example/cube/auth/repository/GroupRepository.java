package com.example.cube.auth.repository;

import com.example.cube.auth.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByGroupCode(String groupCode);

    boolean existsByGroupCodeIgnoreCase(String groupCode);
}
